package com.zig.gravity.sim

import androidx.lifecycle.ViewModel
import com.alijafari.red.astronomy.R
import com.zig.gravity.edu.ChallengeDef
import com.zig.gravity.edu.ChallengeSetup
import com.zig.gravity.edu.Challenges
import com.zig.gravity.edu.detectors.DetectorEngine
import com.zig.gravity.edu.detectors.EduEvent
import com.zig.gravity.physics.BodyType
import com.zig.gravity.physics.EngineConstants
import com.zig.gravity.physics.NBodyEngine
import com.zig.gravity.physics.SimArrays
import com.zig.gravity.physics.SimEvent
import com.zig.gravity.physics.TimeAccumulator
import com.zig.gravity.physics.clampVelocity
import com.zig.gravity.physics.computeCircularOrbitVelocity
import com.zig.gravity.physics.uiSpeedGuidance
import com.zig.gravity.ui.ActiveTeachingCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class ChallengePhase {
    PREDICTION,
    OBSERVATION,
    COMPLETED
}

data class ActiveChallengeState(
    val challenge: ChallengeDef,
    val phase: ChallengePhase,
    val selectedOptionIndex: Int? = null
)

object ZigGravityDebug {
    var enabled: Boolean = false
}

class SimulationViewModel : ViewModel() {
    val engine = NBodyEngine()                       // public read for tests
    private val accumulator = TimeAccumulator()
    val trails = TrailStore()
    private val detectorEngine = DetectorEngine()

    private val _snapshot = MutableStateFlow(SimSnapshot(emptyList(), 0.0, -1L, 0.0, null))
    val snapshot: StateFlow<SimSnapshot> = _snapshot.asStateFlow()
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _activeCard = MutableStateFlow<ActiveTeachingCard?>(null)
    val activeCard: StateFlow<ActiveTeachingCard?> = _activeCard.asStateFlow()
    private val cardQueue = ArrayDeque<ActiveTeachingCard>()

    private val _activeChallengeState = MutableStateFlow<ActiveChallengeState?>(null)
    val activeChallengeState: StateFlow<ActiveChallengeState?> = _activeChallengeState.asStateFlow()

    private var lastNanos = 0L
    private var metersPerDp = 0.0
    private var viewportKnown = false
    private var firstFrame = true

    // Rolling performance tracking (last 60 frames)
    private val frameDurationsMs = DoubleArray(60)
    private var frameDurationIndex = 0
    private var frameDurationCount = 0
    var meanFrameMs: Double = 0.0
        private set
    var maxFrameMs: Double = 0.0
        private set

    // User-customized property tracking
    private val userSized = HashSet<Long>()
    private val userMass = HashSet<Long>()
    private val presetDps = HashMap<Long, Float>()

    // Drag tracking
    private var draggedId: Long = -1L
    private class DragPoint(var t: Long = 0L, var x: Double = 0.0, var y: Double = 0.0)
    private val dragHistory = Array(16) { DragPoint() }
    private var dragHistoryHead = 0
    private var dragHistorySize = 0

    // Preallocated buffers for launch preview
    private val previewXs = FloatArray(120)
    private val previewYs = FloatArray(120)
    private var currentPreview: PreviewState? = null

    // Merge feedback pulses (max 3 concurrent)
    private val pulses = ArrayDeque<PulseRing>()

    init {
        loadPreset(Presets.sunEarth())
    }

    // ---- Frame entry point (called from the UI's withFrameNanos loop) ----
    fun onFrame(frameTimeNanos: Long) {
        if (lastNanos > 0L) {
            val frameMs = (frameTimeNanos - lastNanos) / 1_000_000.0
            frameDurationsMs[frameDurationIndex] = frameMs
            frameDurationIndex = (frameDurationIndex + 1) % 60
            if (frameDurationCount < 60) frameDurationCount++
            var sum = 0.0
            var maxVal = 0.0
            for (k in 0 until frameDurationCount) {
                val v = frameDurationsMs[k]
                sum += v
                if (v > maxVal) maxVal = v
            }
            meanFrameMs = sum / frameDurationCount
            maxFrameMs = maxVal
        }
        val delta = if (lastNanos == 0L) 0.0 else (frameTimeNanos - lastNanos) / 1e9
        lastNanos = frameTimeNanos
        if (_ui.value.running) accumulator.onFrame(delta)   // PAUSE = accrue nothing
        accumulator.pump { dt -> engine.step(dt) }
        for (e in engine.drainEvents()) {
            when (e) {
                is SimEvent.BodyRemoved -> {
                    trails.remove(e.id)
                    userSized.remove(e.id)
                    userMass.remove(e.id)
                    presetDps.remove(e.id)
                }
                is SimEvent.BodyMerged -> {
                    trails.remove(e.idGone)
                    userSized.remove(e.idGone)
                    userMass.remove(e.idGone)
                    presetDps.remove(e.idGone)
                    if (draggedId == e.idGone) {
                        draggedId = -1L
                        dragHistorySize = 0
                    }
                    if (_ui.value.selectedId == e.idGone) {
                        _ui.value = _ui.value.copy(selectedId = e.idKept)
                    }

                    // Append pulse ring (≤ 3 concurrent; drop oldest)
                    val slot = engine.state.slotOf(e.idKept)
                    if (slot >= 0) {
                        if (pulses.size >= 3) {
                            pulses.removeFirst()
                        }
                        pulses.addLast(
                            PulseRing(
                                xMeters = engine.state.x[slot],
                                yMeters = engine.state.y[slot],
                                startRadiusMeters = engine.state.radius[slot],
                                expiryNanos = frameTimeNanos + 400_000_000L
                            )
                        )

                        val isBh = engine.state.types[slot] == BodyType.BLACK_HOLE.ordinal.toByte()
                        if (isBh) {
                            checkChallengeAutoFire("capture")
                            queueCard(ActiveTeachingCard("capture", postedNanos = frameTimeNanos))
                        } else {
                            checkChallengeAutoFire("merge")
                            queueCard(ActiveTeachingCard("merge", postedNanos = frameTimeNanos))
                        }
                    }
                }
                is SimEvent.WormholeTraversal -> {
                    trails.breakAt(e.bodyId)
                    checkChallengeAutoFire("wormhole")
                    queueCard(ActiveTeachingCard("wormhole", postedNanos = frameTimeNanos))
                }
                else -> {}
            }
        }

        // Clean up expired pulses
        while (pulses.isNotEmpty() && frameTimeNanos >= pulses.first().expiryNanos) {
            pulses.removeFirst()
        }

        if (_ui.value.running || firstFrame) {            // record once per frame, only while running
            for (i in 0 until engine.state.count) {
                // Section 3.5: record trails ONLY for non-kinematic slots
                if (!engine.state.kinematic[i]) {
                    trails.record(engine.state.ids[i], engine.state.x[i], engine.state.y[i])
                }
            }
            firstFrame = false
        }

        // Detectors
        if (_ui.value.running) {
            val eduEvents = detectorEngine.update(_snapshot.value)
            for (ev in eduEvents) {
                when (ev) {
                    is EduEvent.OrbitStabilized -> {
                        checkChallengeAutoFire("orbit")
                        queueCard(ActiveTeachingCard("orbit", bodyId = ev.bodyId, postedNanos = frameTimeNanos))
                    }
                    is EduEvent.BodyEscaped -> {
                        checkChallengeAutoFire("escape")
                        queueCard(ActiveTeachingCard("escape", bodyId = ev.bodyId, postedNanos = frameTimeNanos))
                    }
                    is EduEvent.OrbitDecayed -> {
                        checkChallengeAutoFire("decay")
                        queueCard(ActiveTeachingCard("decay", bodyId = ev.bodyId, postedNanos = frameTimeNanos))
                    }
                    is EduEvent.TwoBodyDance -> {
                        checkChallengeAutoFire("dance")
                        queueCard(ActiveTeachingCard("dance", postedNanos = frameTimeNanos))
                    }
                }
            }
        }

        // Auto-dismiss tier-1 after 8.0 seconds
        val currentCard = _activeCard.value
        if (currentCard != null && _activeChallengeState.value?.phase != ChallengePhase.OBSERVATION) {
            if ((frameTimeNanos - currentCard.postedNanos) > 8_000_000_000L) {
                dismissCard()
            }
        }

        publish()
    }

    // ---- Viewport / scene scale ----
    fun onViewportChanged(widthPx: Int, density: Float) {
        if (widthPx <= 0 || density <= 0f) return
        metersPerDp = EngineConstants.VIEWPORT_WIDTH_AU * EngineConstants.AU / (widthPx.toDouble() / density.toDouble())
        viewportKnown = true
        syncRadiiToSceneScale()
        publish()
    }

    private fun syncRadiiToSceneScale() {
        val count = engine.state.count
        for (i in 0 until count) {
            val id = engine.state.ids[i]
            if (userSized.contains(id)) continue
            val type = BodyType.entries[engine.state.types[i].toInt()]
            val dp = presetDps[id] ?: VisualScale.defaultDp(type)
            engine.state.radius[i] = dp * metersPerDp
        }
    }

    // ---- Time & HUD Intents ----
    fun togglePlayPause() {
        lastNanos = 0L
        _ui.value = _ui.value.copy(running = !_ui.value.running)
    }

    fun pauseIfRunning() {
        if (_ui.value.running) {
            lastNanos = 0L
            _ui.value = _ui.value.copy(running = false)
        }
    }

    fun setSpeed(s: Double) {
        accumulator.speedMultiplier = s
        _ui.value = _ui.value.copy(speed = s)
    }

    fun reset() {
        lastNanos = 0L
        val key = _ui.value.presetKey ?: "sun_earth"
        loadPreset(Presets.byKey(key))
    }

    fun toggleTrails() {
        _ui.value = _ui.value.copy(trailsEnabled = !_ui.value.trailsEnabled)
    }

    fun toggleMarbleBounce() {
        val newMode = !_ui.value.marbleBounce
        _ui.value = _ui.value.copy(marbleBounce = newMode)
        engine.marbleBounceMode = newMode
    }

    fun toggleTheme() {
        _ui.value = _ui.value.copy(darkTheme = !_ui.value.darkTheme)
    }

    fun toggleLanguage() {
        val nextLang = if (_ui.value.language == "fa") "en" else "fa"
        _ui.value = _ui.value.copy(language = nextLang)
    }

    fun togglePerfOverlay() {
        _ui.value = _ui.value.copy(showPerfOverlay = !_ui.value.showPerfOverlay)
    }

    // ---- Presets & Persistence ----
    fun loadPreset(def: PresetDef) {
        lastNanos = 0L
        accumulator.reset()
        while (engine.state.count > 0) {
            engine.removeBody(engine.state.ids[0])
        }
        engine.drainEvents()
        trails.clear()
        userSized.clear()
        userMass.clear()
        presetDps.clear()
        pulses.clear()
        draggedId = -1L
        currentPreview = null
        engine.clearFailures()
        detectorEngine.reset()
        cardQueue.clear()
        _activeCard.value = null
        _activeChallengeState.value = null

        var lastAddedId = 0L
        for (b in def.bodies) {
            val radiusMeters = if (metersPerDp > 0.0) b.dp * metersPerDp else VisualScale.defaultDp(b.type) * 1e9
            val id = engine.addBody(
                type = b.type,
                massKg = b.massKg,
                radiusMeters = radiusMeters,
                x = b.x,
                y = b.y,
                vx = b.vx,
                vy = b.vy
            )
            if (id > 0L) {
                presetDps[id] = b.dp
            }
            if (b.isWormholePartnerWithNext) {
                lastAddedId = id
            } else if (lastAddedId > 0L && b.type == BodyType.WORMHOLE_MOUTH) {
                engine.linkPair(lastAddedId, id)
                lastAddedId = 0L
            }
        }
        engine.computeAccelerations()
        engine.state.simTime = 0.0
        if (viewportKnown) {
            syncRadiiToSceneScale()
        }
        _ui.value = _ui.value.copy(
            running = true,
            presetKey = def.key,
            selectedId = -1L,
            showInspectorSheet = false,
            showContextSheet = false,
            showCatalogSheet = false,
            showPresetPickerSheet = false,
            catalogFullNotice = false
        )
        publish()
    }

    fun exportSave(): String? {
        if (engine.state.count == 0) return null
        val effectiveMetersPerDp = if (viewportKnown && metersPerDp > 0.0) metersPerDp else VisualScale.DEFAULT_METERS_PER_DP
        lastNanos = 0L
        accumulator.reset()
        val count = engine.state.count
        val bodies = ArrayList<BodySave>(count)
        for (i in 0 until count) {
            val id = engine.state.ids[i]
            val partnerId = engine.state.partnerIds[i]
            val pIndex = if (partnerId != 0L) {
                engine.state.slotOf(partnerId)
            } else {
                -1
            }
            bodies.add(
                BodySave(
                    typeOrdinal = engine.state.types[i].toInt(),
                    massKg = engine.state.mass[i],
                    radiusDp = engine.state.radius[i] / effectiveMetersPerDp,
                    x = engine.state.x[i],
                    y = engine.state.y[i],
                    vx = engine.state.vx[i],
                    vy = engine.state.vy[i],
                    userSized = userSized.contains(id),
                    userMass = userMass.contains(id),
                    partnerIndex = pIndex
                )
            )
        }
        val save = SimSave(
            version = 3,
            presetKey = _ui.value.presetKey,
            simTime = engine.state.simTime,
            speed = _ui.value.speed,
            trailsEnabled = _ui.value.trailsEnabled,
            marbleBounce = _ui.value.marbleBounce,
            bodies = bodies,
            theme = if (_ui.value.darkTheme) "dark" else "light",
            language = _ui.value.language
        )
        return try {
            jsonFormat.encodeToString(save)
        } catch (_: Exception) {
            null
        }
    }

    private val jsonFormat = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun importSave(json: String): Boolean {
        val save = try {
            jsonFormat.decodeFromString<SimSave>(json)
        } catch (_: Exception) {
            return false
        }
        if (save.bodies.isEmpty()) return false

        val effectiveMetersPerDp = if (viewportKnown && metersPerDp > 0.0) metersPerDp else VisualScale.DEFAULT_METERS_PER_DP
        lastNanos = 0L
        accumulator.reset()
        while (engine.state.count > 0) {
            engine.removeBody(engine.state.ids[0])
        }
        engine.drainEvents()
        trails.clear()
        userSized.clear()
        userMass.clear()
        presetDps.clear()
        pulses.clear()
        draggedId = -1L
        currentPreview = null
        engine.clearFailures()

        val newIds = LongArray(save.bodies.size)
        for ((index, b) in save.bodies.withIndex()) {
            val type = BodyType.entries.getOrNull(b.typeOrdinal) ?: BodyType.TEST_MARBLE
            val radiusMeters = if (b.radiusDp > 0.0) b.radiusDp * effectiveMetersPerDp else VisualScale.defaultDp(type) * effectiveMetersPerDp
            val id = engine.addBody(
                type = type,
                massKg = b.massKg,
                radiusMeters = radiusMeters,
                x = b.x,
                y = b.y,
                vx = b.vx,
                vy = b.vy
            )
            newIds[index] = id
            if (id > 0L) {
                presetDps[id] = b.radiusDp.toFloat()
                if (b.userSized) userSized.add(id)
                if (b.userMass) userMass.add(id)
            }
        }

        // Re-link wormhole partner pairs by partnerIndex
        for ((index, b) in save.bodies.withIndex()) {
            if (b.partnerIndex in save.bodies.indices && b.partnerIndex != index) {
                val myId = newIds[index]
                val partnerId = newIds[b.partnerIndex]
                if (myId > 0L && partnerId > 0L) {
                    engine.linkPair(myId, partnerId)
                }
            }
        }

        engine.state.simTime = save.simTime
        setSpeed(save.speed)
        engine.marbleBounceMode = save.marbleBounce
        val isDark = save.theme != "light"
        val lang = save.language ?: "fa"
        _ui.value = _ui.value.copy(
            running = false,
            presetKey = save.presetKey,
            trailsEnabled = save.trailsEnabled,
            marbleBounce = save.marbleBounce,
            darkTheme = isDark,
            language = lang,
            selectedId = -1L,
            showInspectorSheet = false,
            showContextSheet = false,
            showCatalogSheet = false,
            showPresetPickerSheet = false,
            catalogFullNotice = false
        )
        engine.computeAccelerations()
        if (viewportKnown) {
            syncRadiiToSceneScale()
        }
        publish()
        return true
    }

    // ---- Selection & Sheet UI state ----
    fun selectBody(id: Long) {
        _ui.value = _ui.value.copy(
            selectedId = id,
            showInspectorSheet = true,
            showContextSheet = false,
            showCatalogSheet = false,
            showPresetPickerSheet = false
        )
        publish()
    }

    fun openContextMenu(id: Long) {
        _ui.value = _ui.value.copy(
            selectedId = id,
            showInspectorSheet = false,
            showContextSheet = true,
            showCatalogSheet = false,
            showPresetPickerSheet = false
        )
        publish()
    }

    fun openCatalog() {
        val isFull = engine.state.count >= engine.state.capacity
        _ui.value = _ui.value.copy(
            showCatalogSheet = true,
            showInspectorSheet = false,
            showContextSheet = false,
            showPresetPickerSheet = false,
            catalogFullNotice = isFull
        )
    }

    fun openPresetPicker() {
        _ui.value = _ui.value.copy(
            showPresetPickerSheet = true,
            showInspectorSheet = false,
            showContextSheet = false,
            showCatalogSheet = false,
            showChallengesSheet = false
        )
    }

    fun openChallengesSheet() {
        _ui.value = _ui.value.copy(
            showChallengesSheet = true,
            showInspectorSheet = false,
            showContextSheet = false,
            showCatalogSheet = false,
            showPresetPickerSheet = false
        )
    }

    fun toggleTeaching() {
        val newMode = !_ui.value.teachingMode
        _ui.value = _ui.value.copy(teachingMode = newMode)
        if (!newMode) {
            _activeCard.value = null
            cardQueue.clear()
        }
    }

    fun dismissCard() {
        if (cardQueue.isNotEmpty()) {
            _activeCard.value = cardQueue.removeFirst().copy(postedNanos = System.nanoTime())
        } else {
            _activeCard.value = null
        }
    }

    private fun queueCard(card: ActiveTeachingCard) {
        if (!_ui.value.teachingMode && _activeChallengeState.value == null) return
        if (_activeCard.value == null) {
            _activeCard.value = card
        } else {
            if (_activeCard.value?.cardId != card.cardId && cardQueue.none { it.cardId == card.cardId }) {
                cardQueue.addLast(card)
            }
        }
    }

    private fun checkChallengeAutoFire(eventKey: String) {
        val chState = _activeChallengeState.value ?: return
        if (chState.phase == ChallengePhase.OBSERVATION && chState.challenge.autoFireEventKey == eventKey) {
            completeChallengeObservation(null)
        }
    }

    fun startChallenge(challenge: ChallengeDef) {
        lastNanos = 0L
        accumulator.reset()
        while (engine.state.count > 0) {
            engine.removeBody(engine.state.ids[0])
        }
        engine.drainEvents()
        trails.clear()
        userSized.clear()
        userMass.clear()
        presetDps.clear()
        pulses.clear()
        draggedId = -1L
        currentPreview = null
        engine.clearFailures()
        detectorEngine.reset()
        cardQueue.clear()
        _activeCard.value = null

        when (val setup = challenge.setup) {
            is ChallengeSetup.PresetKey -> {
                val def = Presets.byKey(setup.key)
                for (b in def.bodies) {
                    val radiusMeters = if (metersPerDp > 0.0) b.dp * metersPerDp else VisualScale.defaultDp(b.type) * 1e9
                    val id = engine.addBody(
                        type = b.type,
                        massKg = b.massKg,
                        radiusMeters = radiusMeters,
                        x = b.x,
                        y = b.y,
                        vx = b.vx,
                        vy = b.vy
                    )
                    if (id > 0L) {
                        presetDps[id] = b.dp
                    }
                }
            }
            is ChallengeSetup.Custom -> {
                var lastAddedId = 0L
                for (cb in setup.bodies) {
                    val id = engine.addBody(
                        type = cb.type,
                        massKg = cb.massKg,
                        radiusMeters = cb.radiusMeters,
                        x = cb.x,
                        y = cb.y,
                        vx = cb.vx,
                        vy = cb.vy
                    )
                    if (cb.isWormholePartnerWithNext) {
                        lastAddedId = id
                    } else if (lastAddedId > 0L && cb.type == BodyType.WORMHOLE_MOUTH) {
                        engine.linkPair(lastAddedId, id)
                        lastAddedId = 0L
                    }
                }
            }
        }

        engine.computeAccelerations()
        engine.state.simTime = 0.0
        if (viewportKnown) {
            syncRadiiToSceneScale()
        }

        _ui.value = _ui.value.copy(
            running = false,
            presetKey = if (challenge.setup is ChallengeSetup.PresetKey) challenge.setup.key else null,
            selectedId = -1L,
            showInspectorSheet = false,
            showContextSheet = false,
            showCatalogSheet = false,
            showPresetPickerSheet = false,
            showChallengesSheet = false,
            catalogFullNotice = false
        )
        _activeChallengeState.value = ActiveChallengeState(challenge, ChallengePhase.PREDICTION)
        publish()
    }

    fun submitChallengePrediction(optionIndex: Int) {
        val state = _activeChallengeState.value ?: return
        val challenge = state.challenge

        if (challenge.hasMutation && challenge.id == "double_mass") {
            for (i in 0 until engine.state.count) {
                if (engine.state.types[i].toInt() == BodyType.PLANET.ordinal) {
                    engine.state.mass[i] = 2.0 * EngineConstants.M_EARTH
                    userMass.add(engine.state.ids[i])
                    break
                }
            }
            engine.computeAccelerations()
        }

        _ui.value = _ui.value.copy(running = true)
        _activeChallengeState.value = ActiveChallengeState(
            challenge = challenge,
            phase = ChallengePhase.OBSERVATION,
            selectedOptionIndex = optionIndex
        )
        publish()
    }

    fun completeChallengeObservation(contextStringGetter: ((Int) -> String)?) {
        val state = _activeChallengeState.value ?: return
        if (state.phase == ChallengePhase.COMPLETED) return

        val challenge = state.challenge
        val selectedIdx = state.selectedOptionIndex ?: 0
        val correctIdx = challenge.correctIndex

        val card = ActiveTeachingCard(
            cardId = challenge.resultCardId,
            postedNanos = System.nanoTime(),
            successLine = contextStringGetter?.invoke(challenge.successLineRes),
            reflection = if (contextStringGetter != null) {
                val selText = contextStringGetter.invoke(challenge.optionsRes[selectedIdx])
                val corText = contextStringGetter.invoke(challenge.optionsRes[correctIdx])
                contextStringGetter.invoke(R.string.zig_gravity_edu_reflection_template).let { tpl ->
                    try {
                        String.format(tpl, selText, corText)
                    } catch (_: Exception) {
                        "$selText -> $corText"
                    }
                }
            } else null
        )

        _activeChallengeState.value = ActiveChallengeState(
            challenge = challenge,
            phase = ChallengePhase.COMPLETED,
            selectedOptionIndex = state.selectedOptionIndex
        )
        _activeCard.value = card
    }

    fun dismissChallenge() {
        _activeChallengeState.value = null
        _activeCard.value = null
    }

    fun dismissSheets() {
        _ui.value = _ui.value.copy(
            selectedId = -1L,
            showInspectorSheet = false,
            showContextSheet = false,
            showCatalogSheet = false,
            showPresetPickerSheet = false,
            showChallengesSheet = false,
            catalogFullNotice = false
        )
        publish()
    }

    fun clearSelection() {
        dismissSheets()
    }

    // ---- Drag & Kinematic Intents (§3.3) ----
    fun beginDrag(id: Long) {
        val slot = engine.state.slotOf(id)
        if (slot >= 0) {
            engine.state.kinematic[slot] = true
            draggedId = id
            dragHistoryHead = 0
            dragHistorySize = 0
            val t = System.nanoTime()
            pushDragHistory(t, engine.state.x[slot], engine.state.y[slot])
        }
    }

    fun dragMoveTo(id: Long, xMeters: Double, yMeters: Double) {
        val slot = engine.state.slotOf(id)
        if (slot >= 0 && engine.state.kinematic[slot]) {
            engine.state.x[slot] = xMeters
            engine.state.y[slot] = yMeters
            val t = System.nanoTime()
            pushDragHistory(t, xMeters, yMeters)
        }
    }

    fun endDrag() {
        if (draggedId != -1L) {
            val slot = engine.state.slotOf(draggedId)
            if (slot >= 0) {
                engine.state.kinematic[slot] = false
                // Calculate throw velocity from pointer history (last ~80ms)
                val (rawVx, rawVy) = calculateThrowVelocity()
                val (clampedVx, clampedVy) = clampVelocity(rawVx, rawVy)
                engine.state.vx[slot] = clampedVx
                engine.state.vy[slot] = clampedVy
                engine.computeAccelerations()
            }
            draggedId = -1L
            dragHistorySize = 0
            publish()
        }
    }

    private fun pushDragHistory(t: Long, x: Double, y: Double) {
        val p = dragHistory[dragHistoryHead]
        p.t = t
        p.x = x
        p.y = y
        dragHistoryHead = (dragHistoryHead + 1) % dragHistory.size
        if (dragHistorySize < dragHistory.size) {
            dragHistorySize++
        }
    }

    private fun calculateThrowVelocity(): Pair<Double, Double> {
        if (dragHistorySize < 2) return Pair(0.0, 0.0)
        val now = System.nanoTime()
        val newestIdx = (dragHistoryHead - 1 + dragHistory.size) % dragHistory.size
        val newest = dragHistory[newestIdx]
        // If the user held still before releasing (no drag events in >80ms), release with zero velocity
        if (now - newest.t > 80_000_000L) {
            return Pair(0.0, 0.0)
        }
        val cutoffNanos = newest.t - 80_000_000L // 80 ms window

        var oldestIdx = newestIdx
        var validCount = 0
        for (i in 0 until dragHistorySize) {
            val idx = (newestIdx - i + dragHistory.size) % dragHistory.size
            if (dragHistory[idx].t >= cutoffNanos) {
                oldestIdx = idx
                validCount++
            } else {
                break
            }
        }

        if (validCount < 2) {
            // Take the 2 most recent points if time gap was large
            val prevIdx = (newestIdx - 1 + dragHistory.size) % dragHistory.size
            val dt = (newest.t - dragHistory[prevIdx].t) / 1e9
            if (dt <= 1e-6) return Pair(0.0, 0.0)
            val vx = (newest.x - dragHistory[prevIdx].x) / dt
            val vy = (newest.y - dragHistory[prevIdx].y) / dt
            return Pair(vx, vy)
        }

        val oldest = dragHistory[oldestIdx]
        val dt = (newest.t - oldest.t) / 1e9
        if (dt <= 1e-6) return Pair(0.0, 0.0)
        val vx = (newest.x - oldest.x) / dt
        val vy = (newest.y - oldest.y) / dt
        return Pair(vx, vy)
    }

    // ---- Launch Preview (§3.2) ----
    fun updateLaunchPreview(pressX: Double, pressY: Double, vx: Double, vy: Double, type: BodyType) {
        val (clampedVx, clampedVy) = clampVelocity(vx, vy)
        val candidateMass = defaultMassForType(type)
        val candidateRadius = if (metersPerDp > 0.0) VisualScale.defaultDp(type) * metersPerDp else defaultRadiusForType(type)

        var totalSystemMass = 0.0
        for (i in 0 until engine.state.count) {
            totalSystemMass += engine.state.mass[i]
        }
        val approximate = candidateMass > 0.01 * max(totalSystemMass, 1e-10)

        // Frozen-field test particle integration (up to 600 steps × DT)
        val count = engine.state.count
        val xArr = engine.state.x
        val yArr = engine.state.y
        val mArr = engine.state.mass
        val rArr = engine.state.radius
        val G = EngineConstants.G
        val epsSoft2 = EngineConstants.EPS_SOFT * EngineConstants.EPS_SOFT
        val dt = EngineConstants.DT

        var px = pressX
        var py = pressY
        var pvx = clampedVx
        var pvy = clampedVy

        val halfDiag = EngineConstants.VIEWPORT_WIDTH_AU * EngineConstants.AU * 1.5
        val maxDistSq = halfDiag * halfDiag

        var recordedCount = 0
        val sampleInterval = 5 // 600 / 5 = 120 max points

        for (step in 0 until 600) {
            if (step % sampleInterval == 0 && recordedCount < 120) {
                previewXs[recordedCount] = px.toFloat()
                previewYs[recordedCount] = py.toFloat()
                recordedCount++
            }

            // Early stop: off-screen
            if (px * px + py * py > maxDistSq) break

            // Early stop: contact with existing bodies
            var contacted = false
            for (j in 0 until count) {
                val dx = xArr[j] - px
                val dy = yArr[j] - py
                val rMin = candidateRadius + rArr[j]
                if (dx * dx + dy * dy <= rMin * rMin) {
                    contacted = true
                    break
                }
            }
            if (contacted) break

            // Compute frozen-field gravity acceleration on test particle
            var ax = 0.0
            var ay = 0.0
            for (j in 0 until count) {
                val dx = xArr[j] - px
                val dy = yArr[j] - py
                val dist2 = dx * dx + dy * dy
                val r2Soft = dist2 + epsSoft2
                val invDist3 = 1.0 / (r2Soft * sqrt(r2Soft))
                val f = G * mArr[j] * invDist3
                ax += f * dx
                ay += f * dy
            }

            // Velocity-Verlet / Euler step for test particle
            pvx += ax * dt
            pvy += ay * dt
            px += pvx * dt
            py += pvy * dt
        }

        currentPreview = PreviewState(
            ghostX = pressX,
            ghostY = pressY,
            ghostRadiusDp = VisualScale.defaultDp(type),
            type = type,
            pathXs = previewXs.copyOf(recordedCount),
            pathYs = previewYs.copyOf(recordedCount),
            pathLength = recordedCount,
            approximate = approximate
        )
        publish()
    }

    fun clearLaunchPreview() {
        currentPreview = null
        publish()
    }

    fun spawnLaunchedBody(type: BodyType, x: Double, y: Double, vx: Double, vy: Double) {
        if (engine.state.count >= engine.state.capacity) return
        val massKg = defaultMassForType(type)
        val radiusMeters = if (metersPerDp > 0.0) VisualScale.defaultDp(type) * metersPerDp else defaultRadiusForType(type)

        // UI guidance clamping: min(2·v_esc_local, VELOCITY_HARD_CAP)
        val (clampedVx, clampedVy) = clampVelocity(vx, vy)
        val speed = sqrt(clampedVx * clampedVx + clampedVy * clampedVy)

        // Compute local escape guidance speed
        var maxAttraction = -1.0
        var dominantSlot = -1
        val G = EngineConstants.G
        val epsSoft2 = EngineConstants.EPS_SOFT * EngineConstants.EPS_SOFT
        for (j in 0 until engine.state.count) {
            if (engine.state.mass[j] <= 0.0) continue
            val dx = engine.state.x[j] - x
            val dy = engine.state.y[j] - y
            val r2Soft = dx * dx + dy * dy + epsSoft2
            val attraction = G * engine.state.mass[j] / r2Soft
            if (attraction > maxAttraction) {
                maxAttraction = attraction
                dominantSlot = j
            }
        }
        val guidanceSpeed = if (dominantSlot >= 0) {
            val dx = engine.state.x[dominantSlot] - x
            val dy = engine.state.y[dominantSlot] - y
            val r = max(sqrt(dx * dx + dy * dy), EngineConstants.EPS_SOFT)
            val vEsc = sqrt(2.0 * G * engine.state.mass[dominantSlot] / r)
            min(2.0 * vEsc, EngineConstants.VELOCITY_HARD_CAP)
        } else {
            EngineConstants.VELOCITY_HARD_CAP
        }

        val finalVx: Double
        val finalVy: Double
        if (speed > guidanceSpeed && speed > 0.0) {
            val scale = guidanceSpeed / speed
            finalVx = clampedVx * scale
            finalVy = clampedVy * scale
        } else {
            finalVx = clampedVx
            finalVy = clampedVy
        }

        val id = engine.addBody(
            type = type,
            massKg = massKg,
            radiusMeters = radiusMeters,
            x = x,
            y = y,
            vx = finalVx,
            vy = finalVy
        )
        if (id > 0L) {
            _ui.value = _ui.value.copy(lastPickedType = type)
        }
        currentPreview = null
        publish()
    }

    fun addBodyAtCenter(type: BodyType) {
        if (type == BodyType.WORMHOLE_MOUTH) {
            if (engine.state.count + 2 > engine.state.capacity) {
                _ui.value = _ui.value.copy(catalogFullNotice = true)
                return
            }
            val radiusMeters = if (metersPerDp > 0.0) VisualScale.defaultDp(type) * metersPerDp else VisualScale.defaultDp(type) * VisualScale.DEFAULT_METERS_PER_DP
            val idA = engine.addBody(
                type = type,
                massKg = 0.0,
                radiusMeters = radiusMeters,
                x = -0.9 * EngineConstants.AU,
                y = 0.0,
                vx = 0.0,
                vy = 0.0
            )
            val idB = engine.addBody(
                type = type,
                massKg = 0.0,
                radiusMeters = radiusMeters,
                x = 0.9 * EngineConstants.AU,
                y = 0.0,
                vx = 0.0,
                vy = 0.0,
                partnerId = idA
            )
            if (idA > 0L && idB > 0L) {
                engine.linkPair(idA, idB)
                presetDps[idA] = VisualScale.defaultDp(type)
                presetDps[idB] = VisualScale.defaultDp(type)
                _ui.value = _ui.value.copy(
                    selectedId = idA,
                    showInspectorSheet = true,
                    showCatalogSheet = false,
                    lastPickedType = type
                )
            }
            publish()
            return
        }

        if (engine.state.count >= engine.state.capacity) {
            _ui.value = _ui.value.copy(catalogFullNotice = true)
            return
        }
        val massKg = defaultMassForType(type)
        val radiusMeters = if (metersPerDp > 0.0) VisualScale.defaultDp(type) * metersPerDp else defaultRadiusForType(type)
        val shiftStep = if (metersPerDp > 0.0) 3.0 * metersPerDp else 3.0 * 1e9

        var targetX = 0.0
        var targetY = 0.0
        var attempts = 0

        while (attempts < 20) {
            var overlaps = false
            for (i in 0 until engine.state.count) {
                val dx = engine.state.x[i] - targetX
                val dy = engine.state.y[i] - targetY
                val minDist = radiusMeters + engine.state.radius[i]
                if (dx * dx + dy * dy < minDist * minDist) {
                    overlaps = true
                    break
                }
            }
            if (!overlaps) break
            attempts++
            targetX += shiftStep * (1 + attempts)
            targetY += shiftStep * (1 + attempts)
        }

        val id = engine.addBody(
            type = type,
            massKg = massKg,
            radiusMeters = radiusMeters,
            x = targetX,
            y = targetY,
            vx = 0.0,
            vy = 0.0
        )
        if (id > 0L) {
            _ui.value = _ui.value.copy(
                selectedId = id,
                showInspectorSheet = true,
                showCatalogSheet = false,
                lastPickedType = type
            )
        }
        publish()
    }

    fun duplicateBody(id: Long) {
        val slot = engine.state.slotOf(id)
        if (slot < 0) return
        val type = BodyType.entries[engine.state.types[slot].toInt()]

        if (type == BodyType.WORMHOLE_MOUTH) {
            val partnerId = engine.state.partnerIds[slot]
            if (partnerId != 0L) {
                if (engine.state.count + 2 > engine.state.capacity) return
                val pSlot = engine.state.slotOf(partnerId)
                if (pSlot < 0) return
                val offset = if (metersPerDp > 0.0) 3.0 * metersPerDp else 3.0 * VisualScale.DEFAULT_METERS_PER_DP
                val rA = engine.state.radius[slot]
                val rB = engine.state.radius[pSlot]
                val newXA = engine.state.x[slot] + offset
                val newYA = engine.state.y[slot] + offset
                val newXB = engine.state.x[pSlot] + offset
                val newYB = engine.state.y[pSlot] + offset

                val newIdA = engine.addBody(BodyType.WORMHOLE_MOUTH, 0.0, rA, newXA, newYA, 0.0, 0.0)
                val newIdB = engine.addBody(BodyType.WORMHOLE_MOUTH, 0.0, rB, newXB, newYB, 0.0, 0.0, partnerId = newIdA)
                if (newIdA > 0L && newIdB > 0L) {
                    engine.linkPair(newIdA, newIdB)
                    if (userSized.contains(id)) userSized.add(newIdA)
                    if (userSized.contains(partnerId)) userSized.add(newIdB)
                    presetDps[newIdA] = presetDps[id] ?: VisualScale.defaultDp(type)
                    presetDps[newIdB] = presetDps[partnerId] ?: VisualScale.defaultDp(type)

                    _ui.value = _ui.value.copy(
                        selectedId = newIdA,
                        showInspectorSheet = true,
                        showContextSheet = false
                    )
                }
                publish()
                return
            }
        }

        if (engine.state.count >= engine.state.capacity) return
        val massKg = engine.state.mass[slot]
        val radiusMeters = engine.state.radius[slot]
        val shiftMeters = if (metersPerDp > 0.0) 2.0 * metersPerDp else 2.0 * 1e9
        val newX = engine.state.x[slot] + shiftMeters
        val newY = engine.state.y[slot] + shiftMeters
        val vx = engine.state.vx[slot]
        val vy = engine.state.vy[slot]

        val newId = engine.addBody(type, massKg, radiusMeters, newX, newY, vx, vy)
        if (newId > 0L) {
            if (userSized.contains(id)) userSized.add(newId)
            if (userMass.contains(id)) userMass.add(newId)
            _ui.value = _ui.value.copy(
                selectedId = newId,
                showInspectorSheet = true,
                showContextSheet = false
            )
        }
        publish()
    }

    fun removeBody(id: Long) {
        val slot = engine.state.slotOf(id)
        if (slot >= 0 && BodyType.entries[engine.state.types[slot].toInt()] == BodyType.WORMHOLE_MOUTH) {
            val partnerId = engine.state.partnerIds[slot]
            if (partnerId != 0L) {
                val pSlot = engine.state.slotOf(partnerId)
                val frameTimeNanos = System.nanoTime()
                while (pulses.size > 1) pulses.removeFirst()
                pulses.addLast(
                    PulseRing(
                        xMeters = engine.state.x[slot],
                        yMeters = engine.state.y[slot],
                        startRadiusMeters = engine.state.radius[slot],
                        expiryNanos = frameTimeNanos + 400_000_000L
                    )
                )
                if (pSlot >= 0) {
                    pulses.addLast(
                        PulseRing(
                            xMeters = engine.state.x[pSlot],
                            yMeters = engine.state.y[pSlot],
                            startRadiusMeters = engine.state.radius[pSlot],
                            expiryNanos = frameTimeNanos + 400_000_000L
                        )
                    )
                }
                trails.remove(partnerId)
                userSized.remove(partnerId)
                userMass.remove(partnerId)
                presetDps.remove(partnerId)
                engine.removeBody(partnerId)

                trails.remove(id)
                userSized.remove(id)
                userMass.remove(id)
                presetDps.remove(id)
                engine.removeBody(id)

                if (_ui.value.selectedId == id || _ui.value.selectedId == partnerId) {
                    _ui.value = _ui.value.copy(
                        selectedId = -1L,
                        showInspectorSheet = false,
                        showContextSheet = false
                    )
                }
                publish()
                return
            }
        }

        trails.remove(id)
        userSized.remove(id)
        userMass.remove(id)
        presetDps.remove(id)
        engine.removeBody(id)
        if (_ui.value.selectedId == id) {
            _ui.value = _ui.value.copy(
                selectedId = -1L,
                showInspectorSheet = false,
                showContextSheet = false
            )
        }
        publish()
    }

    fun setMass(id: Long, kg: Double) {
        if (!kg.isFinite() || kg < 0.0) return
        val slot = engine.state.slotOf(id)
        if (slot >= 0) {
            engine.state.mass[slot] = kg
            userMass.add(id)
            engine.computeAccelerations()
            publish()
        }
    }

    fun setRadius(id: Long, meters: Double) {
        if (!meters.isFinite() || meters <= 0.0) return
        val slot = engine.state.slotOf(id)
        if (slot >= 0) {
            engine.state.radius[slot] = meters
            userSized.add(id)
            publish()
        }
    }

    fun setVelocity(id: Long, vx: Double, vy: Double) {
        if (!vx.isFinite() || !vy.isFinite()) return
        val slot = engine.state.slotOf(id)
        if (slot >= 0) {
            val (clampedVx, clampedVy) = clampVelocity(vx, vy)
            engine.state.vx[slot] = clampedVx
            engine.state.vy[slot] = clampedVy
            engine.computeAccelerations()
            publish()
        }
    }

    fun circularize(id: Long) {
        val slot = engine.state.slotOf(id)
        if (slot >= 0) {
            val (newVx, newVy) = computeCircularOrbitVelocity(engine.state, slot)
            engine.state.vx[slot] = newVx
            engine.state.vy[slot] = newVy
            engine.computeAccelerations()
            publish()
        }
    }

    private fun defaultMassForType(type: BodyType): Double = BodyDefaults.massKg(type)

    private fun defaultRadiusForType(type: BodyType): Double = when (type) {
        BodyType.SUN -> EngineConstants.R_SUN
        BodyType.PLANET -> EngineConstants.R_EARTH
        BodyType.MOON -> EngineConstants.R_MOON
        BodyType.ASTEROID -> 1e5
        BodyType.TEST_MARBLE -> 1e4
        BodyType.BLACK_HOLE -> 1.5e4
        BodyType.WORMHOLE_MOUTH -> 1.5e4
    }

    private fun publish() {
        val count = engine.state.count
        val list = ArrayList<BodyRender>(count)
        for (i in 0 until count) {
            val type = BodyType.entries[engine.state.types[i].toInt()]
            list.add(
                BodyRender(
                    id = engine.state.ids[i],
                    type = type,
                    x = engine.state.x[i],
                    y = engine.state.y[i],
                    vx = engine.state.vx[i],
                    vy = engine.state.vy[i],
                    massKg = engine.state.mass[i],
                    radiusMeters = engine.state.radius[i],
                    partnerId = engine.state.partnerIds[i]
                )
            )
        }
        _snapshot.value = SimSnapshot(
            bodies = list,
            simTime = engine.state.simTime,
            selectedId = _ui.value.selectedId,
            metersPerDp = metersPerDp,
            preview = currentPreview,
            pulses = pulses.toList()
        )
    }
}
