package com.zig.gravity.physics

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class NBodyEngine(val state: SimArrays = SimArrays()) {

    private val eventsQueue = ArrayDeque<SimEvent>()
    var consecutiveFailures: Int = 0
        private set

    // ---- Last-good snapshot (integrity layer) ----
    private val lastGoodX = DoubleArray(state.capacity)
    private val lastGoodY = DoubleArray(state.capacity)
    private val lastGoodVx = DoubleArray(state.capacity)
    private val lastGoodVy = DoubleArray(state.capacity)
    private val lastGoodAx = DoubleArray(state.capacity)
    private val lastGoodAy = DoubleArray(state.capacity)
    private val lastGoodMass = DoubleArray(state.capacity)
    private val lastGoodRadius = DoubleArray(state.capacity)
    private val lastGoodIds = LongArray(state.capacity)
    private val lastGoodTypes = ByteArray(state.capacity)
    private val lastGoodActive = BooleanArray(state.capacity)
    private val lastGoodKinematic = BooleanArray(state.capacity)
    private val lastGoodPartnerIds = LongArray(state.capacity)
    private val lastGoodLastExitMouth = LongArray(state.capacity)
    private val lastGoodTeleportCooldownUntil = DoubleArray(state.capacity)
    private var lastGoodCount = 0
    private var lastGoodSimTime = 0.0

    init {
        if (state.count > 0) {
            computeAccelerations()
        }
        saveLastGood()
    }

    /**
     * Adds a body to the engine state. On success, emits a [SimEvent.BodyAdded] event,
     * recomputes initial accelerations, and updates the last-good snapshot.
     */
    fun addBody(
        type: BodyType,
        massKg: Double,
        radiusMeters: Double,
        x: Double,
        y: Double,
        vx: Double = 0.0,
        vy: Double = 0.0,
        partnerId: Long = 0L
    ): Long {
        val id = state.addBody(type, massKg, radiusMeters, x, y, vx, vy, partnerId)
        if (id > 0L) {
            eventsQueue.addLast(SimEvent.BodyAdded(id))
            computeAccelerations()
            saveLastGood()
        }
        return id
    }

    /** Links two bodies mutually as wormhole partners. */
    fun linkPair(id1: Long, id2: Long) {
        state.linkPair(id1, id2)
        saveLastGood()
    }

    /**
     * Removes a body by id. On success, emits a [SimEvent.BodyRemoved] event,
     * recomputes accelerations, and updates the snapshot.
     */
    fun removeBody(id: Long): Boolean {
        val removed = state.removeBody(id)
        if (removed) {
            eventsQueue.addLast(SimEvent.BodyRemoved(id))
            computeAccelerations()
            saveLastGood()
        }
        return removed
    }

    /** Returns and clears all queued events. */
    fun drainEvents(): List<SimEvent> {
        if (eventsQueue.isEmpty()) return emptyList()
        val list = ArrayList<SimEvent>(eventsQueue.size)
        while (eventsQueue.isNotEmpty()) {
            list.add(eventsQueue.removeFirst())
        }
        return list
    }

    /**
     * Computes gravitational accelerations for all active bodies.
     * Exact O(n²) symmetric pairs with Plummer softening (Newton's 3rd law).
     * Zero heap allocations.
     */
    fun computeAccelerations() {
        val count = state.count
        val x = state.x
        val y = state.y
        val ax = state.ax
        val ay = state.ay
        val mass = state.mass
        val G = EngineConstants.G
        val epsSoft2 = EngineConstants.EPS_SOFT * EngineConstants.EPS_SOFT

        for (i in 0 until count) {
            ax[i] = 0.0
            ay[i] = 0.0
        }

        for (i in 0 until count) {
            val xi = x[i]
            val yi = y[i]
            val mi = mass[i]
            for (j in i + 1 until count) {
                val dx = x[j] - xi
                val dy = y[j] - yi
                val r2 = dx * dx + dy * dy + epsSoft2
                val inv = 1.0 / (r2 * sqrt(r2))
                val fi = G * mass[j] * inv
                val fj = G * mi * inv
                ax[i] += dx * fi
                ay[i] += dy * fi
                ax[j] -= dx * fj
                ay[j] -= dy * fj
            }
        }
    }

    /**
     * Executes one simulation step of nominal size [dt].
     * Uses Adaptive Safety Refinement if closing displacement exceeds safety threshold.
     * Returns total inner Verlet steps executed.
     */
    fun step(dt: Double): Int {
        if (consecutiveFailures >= 3) return 0
        return stepRecursive(dt, 0)
    }

    private fun stepRecursive(dt: Double, depth: Int): Int {
        if (consecutiveFailures >= 3) return 0

        // If state is already corrupt before stepping, roll back immediately
        if (!isStateFinite()) {
            validateAndMaybeRollback()
            return 1
        }

        if (needsRefinement(dt) && depth < EngineConstants.REFINEMENT_MAX_DEPTH) {
            val halfDt = dt * 0.5
            val step1 = stepRecursive(halfDt, depth + 1)
            if (consecutiveFailures > 0) return step1
            val step2 = stepRecursive(halfDt, depth + 1)
            return step1 + step2
        }

        stepInner(dt)
        state.advanceTime(dt)
        val teleported = teleportPass()
        if (teleported) {
            computeAccelerations()
        }
        resolveCollisions()

        if (!validateAndMaybeRollback()) {
            return 1
        }

        saveLastGood()
        return 1
    }

    private fun isStateFinite(): Boolean {
        val count = state.count
        val x = state.x
        val y = state.y
        val vx = state.vx
        val vy = state.vy
        val ax = state.ax
        val ay = state.ay
        for (i in 0 until count) {
            if (!x[i].isFinite() || !y[i].isFinite() ||
                !vx[i].isFinite() || !vy[i].isFinite() ||
                !ax[i].isFinite() || !ay[i].isFinite()
            ) {
                return false
            }
        }
        return true
    }

    private fun stepInner(dt: Double) {
        val count = state.count
        val x = state.x
        val y = state.y
        val vx = state.vx
        val vy = state.vy
        val ax = state.ax
        val ay = state.ay
        val kinematic = state.kinematic
        val halfDt = dt * 0.5

        // Kick 1: v += a * dt / 2
        for (i in 0 until count) {
            if (!kinematic[i]) {
                vx[i] += ax[i] * halfDt
                vy[i] += ay[i] * halfDt
            }
        }

        // Drift: x += v * dt
        for (i in 0 until count) {
            if (!kinematic[i]) {
                x[i] += vx[i] * dt
                y[i] += vy[i] * dt
            }
        }

        // Force update: compute new a(x(t + dt))
        computeAccelerations()

        // Kick 2: v += a * dt / 2
        for (i in 0 until count) {
            if (!kinematic[i]) {
                vx[i] += ax[i] * halfDt
                vy[i] += ay[i] * halfDt
            }
        }
    }

    private fun needsRefinement(dt: Double): Boolean {
        val count = state.count
        val x = state.x
        val y = state.y
        val vx = state.vx
        val vy = state.vy
        val ax = state.ax
        val ay = state.ay
        val triggerFraction = EngineConstants.REFINEMENT_TRIGGER_FRACTION
        val halfDt2 = 0.5 * dt * dt

        for (i in 0 until count) {
            val xi = x[i]; val yi = y[i]
            val vxi = vx[i]; val vyi = vy[i]
            val axi = ax[i]; val ayi = ay[i]
            for (j in i + 1 until count) {
                val dx = x[j] - xi
                val dy = y[j] - yi
                val dr2 = dx * dx + dy * dy
                val dr = sqrt(dr2)

                val dvx = vx[j] - vxi
                val dvy = vy[j] - vyi
                val vRel = sqrt(dvx * dvx + dvy * dvy)

                val dax = ax[j] - axi
                val day = ay[j] - ayi
                val aRel = sqrt(dax * dax + day * day)

                val s = vRel * dt + aRel * halfDt2
                if (s > triggerFraction * dr) {
                    return true
                }
            }
        }
        return false
    }

    private fun validateAndMaybeRollback(): Boolean {
        val count = state.count
        val x = state.x
        val y = state.y
        val vx = state.vx
        val vy = state.vy
        val ax = state.ax
        val ay = state.ay

        var corrupt = false
        for (i in 0 until count) {
            if (!x[i].isFinite() || !y[i].isFinite() ||
                !vx[i].isFinite() || !vy[i].isFinite() ||
                !ax[i].isFinite() || !ay[i].isFinite()
            ) {
                corrupt = true
                break
            }
        }

        if (corrupt) {
            restoreLastGood()
            eventsQueue.addLast(SimEvent.NumericalFailure)
            consecutiveFailures++
            return false
        } else {
            consecutiveFailures = 0
            return true
        }
    }

    private fun saveLastGood() {
        val count = state.count
        lastGoodCount = count
        lastGoodSimTime = state.simTime
        System.arraycopy(state.x, 0, lastGoodX, 0, count)
        System.arraycopy(state.y, 0, lastGoodY, 0, count)
        System.arraycopy(state.vx, 0, lastGoodVx, 0, count)
        System.arraycopy(state.vy, 0, lastGoodVy, 0, count)
        System.arraycopy(state.ax, 0, lastGoodAx, 0, count)
        System.arraycopy(state.ay, 0, lastGoodAy, 0, count)
        System.arraycopy(state.mass, 0, lastGoodMass, 0, count)
        System.arraycopy(state.radius, 0, lastGoodRadius, 0, count)
        System.arraycopy(state.ids, 0, lastGoodIds, 0, count)
        System.arraycopy(state.types, 0, lastGoodTypes, 0, count)
        System.arraycopy(state.active, 0, lastGoodActive, 0, count)
        System.arraycopy(state.kinematic, 0, lastGoodKinematic, 0, count)
        System.arraycopy(state.partnerIds, 0, lastGoodPartnerIds, 0, count)
        System.arraycopy(state.lastExitMouth, 0, lastGoodLastExitMouth, 0, count)
        System.arraycopy(state.teleportCooldownUntil, 0, lastGoodTeleportCooldownUntil, 0, count)
    }

    private fun restoreLastGood() {
        val count = lastGoodCount
        System.arraycopy(lastGoodX, 0, state.x, 0, count)
        System.arraycopy(lastGoodY, 0, state.y, 0, count)
        System.arraycopy(lastGoodVx, 0, state.vx, 0, count)
        System.arraycopy(lastGoodVy, 0, state.vy, 0, count)
        System.arraycopy(lastGoodAx, 0, state.ax, 0, count)
        System.arraycopy(lastGoodAy, 0, state.ay, 0, count)
        System.arraycopy(lastGoodMass, 0, state.mass, 0, count)
        System.arraycopy(lastGoodRadius, 0, state.radius, 0, count)
        System.arraycopy(lastGoodIds, 0, state.ids, 0, count)
        System.arraycopy(lastGoodTypes, 0, state.types, 0, count)
        System.arraycopy(lastGoodActive, 0, state.active, 0, count)
        System.arraycopy(lastGoodKinematic, 0, state.kinematic, 0, count)
        System.arraycopy(lastGoodPartnerIds, 0, state.partnerIds, 0, count)
        System.arraycopy(lastGoodLastExitMouth, 0, state.lastExitMouth, 0, count)
        System.arraycopy(lastGoodTeleportCooldownUntil, 0, state.teleportCooldownUntil, 0, count)
        state.restoreState(lastGoodCount, lastGoodSimTime)
    }

    /**
     * Executes the teleport pass for wormholes:
     * 1. For each body b (non-kinematic, not a mouth) and each mouth M: trigger when dist(b, M) < M.radius
     * 2. Gate — dual cooldown (BOTH must hold):
     *    a. Spatial: if b.lastExitMouth != NONE, require dist(b, lastExitMouth) > 1.5 * lastExitMouth.radius
     *    b. Temporal: require simTime >= b.teleportCooldownUntil (5.0e5 sim-s)
     * 3. On trigger: exit position = partner center P + unit(v_b) * 1.2 * P.radius; velocity bitwise unchanged;
     *    set b.lastExitMouth = P.id; b.teleportCooldownUntil = simTime + WORMHOLE_COOLDOWN_SIM_SECONDS;
     *    emit SimEvent.WormholeTraversal(bodyId, fromMouthId, toMouthId).
     * 4. One traversal per body per substep.
     * Returns true if any body teleported.
     */
    fun teleportPass(): Boolean {
        var anyTeleported = false
        val count = state.count
        val simTime = state.simTime

        for (i in 0 until count) {
            if (state.kinematic[i]) continue
            val typeI = BodyType.entries[state.types[i].toInt()]
            if (typeI == BodyType.WORMHOLE_MOUTH) continue

            // Scan mouths by slot
            for (m in 0 until count) {
                val typeM = BodyType.entries[state.types[m].toInt()]
                if (typeM != BodyType.WORMHOLE_MOUTH) continue

                val partnerId = state.partnerIds[m]
                if (partnerId == 0L) continue
                val pSlot = state.slotOf(partnerId)
                if (pSlot < 0) continue

                val dx = state.x[i] - state.x[m]
                val dy = state.y[i] - state.y[m]
                val dist = sqrt(dx * dx + dy * dy)
                val mRadius = state.radius[m]

                // Trigger condition: center-entry (dist < M.radius)
                if (dist < mRadius) {
                    // Spatial gate check
                    val lastExitId = state.lastExitMouth[i]
                    if (lastExitId != 0L) {
                        val lastSlot = state.slotOf(lastExitId)
                        if (lastSlot >= 0) {
                            val ldx = state.x[i] - state.x[lastSlot]
                            val ldy = state.y[i] - state.y[lastSlot]
                            val ldist = sqrt(ldx * ldx + ldy * ldy)
                            if (ldist <= 1.5 * state.radius[lastSlot]) {
                                continue // Spatial gate blocks
                            }
                        }
                    }

                    // Temporal gate check
                    if (simTime < state.teleportCooldownUntil[i]) {
                        continue // Temporal gate blocks
                    }

                    // Gate passed -> Teleport!
                    val vx = state.vx[i]
                    val vy = state.vy[i]
                    val speed = sqrt(vx * vx + vy * vy)
                    val (unitVx, unitVy) = if (speed > 1e-12) {
                        (vx / speed) to (vy / speed)
                    } else {
                        1.0 to 0.0
                    }

                    val pRadius = state.radius[pSlot]
                    val exitDist = 1.2 * pRadius
                    state.x[i] = state.x[pSlot] + unitVx * exitDist
                    state.y[i] = state.y[pSlot] + unitVy * exitDist
                    // velocity bitwise unchanged
                    state.lastExitMouth[i] = state.ids[pSlot]
                    state.teleportCooldownUntil[i] = simTime + EngineConstants.WORMHOLE_COOLDOWN_SIM_SECONDS

                    eventsQueue.addLast(
                        SimEvent.WormholeTraversal(
                            bodyId = state.ids[i],
                            fromMouthId = state.ids[m],
                            toMouthId = state.ids[pSlot]
                        )
                    )
                    anyTeleported = true
                    break // First trigger in scan order wins per body per substep
                }
            }
        }
        return anyTeleported
    }

    /** Resets the consecutive failures counter. */
    fun clearFailures() {
        consecutiveFailures = 0
    }

    var marbleBounceMode: Boolean = false

    /**
     * Resolves collisions between active bodies per the Phase 5 collision policy.
     * Deterministic scan order: pairs i < j by slot; capped at 20 passes.
     * Emits [SimEvent.BodyMerged] for each merge.
     */
    fun resolveCollisions() {
        var pass = 0
        var stateChangedOverall = false

        while (pass < 20) {
            pass++
            var collisionFoundInPass = false
            val count = state.count

            outer@ for (i in 0 until count) {
                for (j in i + 1 until count) {
                    val typeI = BodyType.entries[state.types[i].toInt()]
                    val typeJ = BodyType.entries[state.types[j].toInt()]
                    if (typeI == BodyType.WORMHOLE_MOUTH || typeJ == BodyType.WORMHOLE_MOUTH) {
                        continue
                    }

                    val dx = state.x[j] - state.x[i]
                    val dy = state.y[j] - state.y[i]
                    val rSum = state.radius[i] + state.radius[j]
                    if (dx * dx + dy * dy <= rSum * rSum) {
                        collisionFoundInPass = true
                        stateChangedOverall = true

                        val shouldBounce = marbleBounceMode &&
                                typeI == BodyType.TEST_MARBLE &&
                                typeJ == BodyType.TEST_MARBLE &&
                                state.mass[i] > 0.0 &&
                                state.mass[j] > 0.0

                        if (shouldBounce) {
                            Collision.bounce(state, i, j)
                        } else {
                            val isBh = typeI == BodyType.BLACK_HOLE || typeJ == BodyType.BLACK_HOLE
                            val (survivorSlot, gonerId) = Collision.merge(state, i, j)
                            val idKept = state.ids[survivorSlot]
                            val mergedMass = state.mass[survivorSlot]

                            eventsQueue.addLast(
                                SimEvent.BodyMerged(
                                    idKept = idKept,
                                    idGone = gonerId,
                                    mergedMassKg = mergedMass,
                                    blackHole = isBh
                                )
                            )
                            break@outer
                        }
                    }
                }
            }

            if (!collisionFoundInPass) {
                break
            }
        }

        if (stateChangedOverall) {
            computeAccelerations()
        }
    }
}

/**
 * Engine-level HARD clamp. Scales the vector so |v| <= VELOCITY_HARD_CAP.
 * Returns the unchanged components bitwise when already within the cap.
 */
fun clampVelocity(vx: Double, vy: Double): Pair<Double, Double> {
    val hardCap = EngineConstants.VELOCITY_HARD_CAP
    val v2 = vx * vx + vy * vy
    if (v2 <= hardCap * hardCap) {
        return Pair(vx, vy)
    }
    val vMag = sqrt(v2)
    val scale = hardCap / vMag
    return Pair(vx * scale, vy * scale)
}

/**
 * UI guidance only (never a physics constraint): the suggested speed ceiling
 * at a body's position = min(2 * sqrt(2*G*M_dominant/r), VELOCITY_HARD_CAP),
 * where M_dominant/r is the dominant attractor found by max(G*m / r²softened).
 * Pure function over the state arrays.
 */
fun uiSpeedGuidance(state: SimArrays, slot: Int): Double {
    val count = state.count
    if (count <= 1 || slot < 0 || slot >= count) {
        return EngineConstants.VELOCITY_HARD_CAP
    }

    val targetX = state.x[slot]
    val targetY = state.y[slot]
    val G = EngineConstants.G
    val epsSoft2 = EngineConstants.EPS_SOFT * EngineConstants.EPS_SOFT

    var maxAttraction = -1.0
    var dominantSlot = -1

    for (j in 0 until count) {
        if (j == slot || state.mass[j] <= 0.0) continue
        val dx = state.x[j] - targetX
        val dy = state.y[j] - targetY
        val r2Soft = dx * dx + dy * dy + epsSoft2
        val attraction = G * state.mass[j] / r2Soft
        if (attraction > maxAttraction) {
            maxAttraction = attraction
            dominantSlot = j
        }
    }

    if (dominantSlot < 0) {
        return EngineConstants.VELOCITY_HARD_CAP
    }

    val dx = state.x[dominantSlot] - targetX
    val dy = state.y[dominantSlot] - targetY
    var r = sqrt(dx * dx + dy * dy)
    if (r <= 0.0) {
        r = EngineConstants.EPS_SOFT
    }

    val mDominant = state.mass[dominantSlot]
    val rawSpeed = 2.0 * sqrt(2.0 * G * mDominant / r)
    return min(rawSpeed, EngineConstants.VELOCITY_HARD_CAP)
}

/**
 * Computes circularized velocity components for [slot] around its dominant attractor.
 * |v| = sqrt(G * m_j / r) perpendicular to r_hat (keeping current angular-momentum sign, default CCW).
 */
fun computeCircularOrbitVelocity(state: SimArrays, slot: Int): Pair<Double, Double> {
    val count = state.count
    if (count <= 1 || slot < 0 || slot >= count) {
        return Pair(state.vx[slot], state.vy[slot])
    }

    val targetX = state.x[slot]
    val targetY = state.y[slot]
    val G = EngineConstants.G
    val epsSoft2 = EngineConstants.EPS_SOFT * EngineConstants.EPS_SOFT

    var maxAttraction = -1.0
    var dominantSlot = -1

    for (j in 0 until count) {
        if (j == slot || state.mass[j] <= 0.0) continue
        val dx = state.x[j] - targetX
        val dy = state.y[j] - targetY
        val r2Soft = dx * dx + dy * dy + epsSoft2
        val attraction = G * state.mass[j] / r2Soft
        if (attraction > maxAttraction) {
            maxAttraction = attraction
            dominantSlot = j
        }
    }

    if (dominantSlot < 0) {
        return Pair(state.vx[slot], state.vy[slot])
    }

    // Relative vector from attractor j to target body
    val rx = targetX - state.x[dominantSlot]
    val ry = targetY - state.y[dominantSlot]
    var r = sqrt(rx * rx + ry * ry)
    if (r <= 0.0) {
        r = EngineConstants.EPS_SOFT
    }

    val mDominant = state.mass[dominantSlot]
    val vMag = sqrt(G * mDominant / r)

    // Unit vector from attractor to target (r_hat)
    val rHatX = rx / r
    val rHatY = ry / r

    // Relative velocity of target w.r.t attractor
    val relVx = state.vx[slot] - state.vx[dominantSlot]
    val relVy = state.vy[slot] - state.vy[dominantSlot]

    // 2D Angular momentum (rx * relVy - ry * relVx)
    val Lz = rx * relVy - ry * relVx

    // Perpendicular unit vector:
    // CCW tangential vector to r_hat is (-rHatY, rHatX)
    // CW tangential vector is (rHatY, -rHatX)
    val sign = if (Lz < 0.0) -1.0 else 1.0 // Default CCW (sign = +1) if Lz >= 0
    val tanX = -rHatY * sign
    val tanY = rHatX * sign

    val newVx = state.vx[dominantSlot] + tanX * vMag
    val newVy = state.vy[dominantSlot] + tanY * vMag

    return clampVelocity(newVx, newVy)
}

