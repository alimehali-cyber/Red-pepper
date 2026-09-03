package com.zig.gravity.ui

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alijafari.red.astronomy.BuildConfig
import com.alijafari.red.astronomy.R
import com.zig.gravity.sim.ChallengePhase
import com.zig.gravity.sim.SimulationViewModel
import com.zig.gravity.sim.ZigGravityDebug
import com.zig.gravity.ui.theme.ZigGravityColor
import com.zig.gravity.ui.theme.ZigGravityTheme
import kotlin.math.roundToInt

@Composable
fun GravitySandboxScreen(
    modifier: Modifier = Modifier,
    vm: SimulationViewModel = viewModel()
) {
    val snapshotState = vm.snapshot.collectAsState()      // State<SimSnapshot> — value read ONLY in draw
    val ui by vm.ui.collectAsState()                       // chrome state — recomposes only on user action
    val activeCard by vm.activeCard.collectAsState()
    val activeChallengeState by vm.activeChallengeState.collectAsState()
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val saver = remember { FileSimSaver(context) }

    // Cold start / restore once
    LaunchedEffect(Unit) {
        saver.load()?.let { vm.importSave(it) }
    }

    // ON_STOP: pause + autosave
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.pauseIfRunning()
        saver.save(vm.exportSave())
    }

    val currentLocale = remember(ui.language) { java.util.Locale(ui.language) }
    val configuration = LocalConfiguration.current
    val localizedConfiguration = remember(configuration, currentLocale) {
        Configuration(configuration).apply {
            setLocale(currentLocale)
            setLayoutDirection(currentLocale)
        }
    }

    ZigGravityTheme(darkTheme = ui.darkTheme) {
        CompositionLocalProvider(
            LocalConfiguration provides localizedConfiguration,
            LocalLayoutDirection provides (if (ui.language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr)
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .testTag("gravity_sandbox_screen")
            ) {
                TabletopCanvas(
                    snapshotState = snapshotState,
                    trails = vm.trails,
                    density = density,
                    trailsEnabled = ui.trailsEnabled,
                    lastPickedType = ui.lastPickedType,
                    onViewportChanged = vm::onViewportChanged,
                    onSelectBody = vm::selectBody,
                    onTapEmpty = vm::clearSelection,
                    onLongPressBody = vm::openContextMenu,
                    onLongPressEmpty = vm::openCatalog,
                    onBeginDrag = vm::beginDrag,
                    onDragMoveTo = vm::dragMoveTo,
                    onEndDrag = vm::endDrag,
                    onUpdateLaunchPreview = vm::updateLaunchPreview,
                    onClearLaunchPreview = vm::clearLaunchPreview,
                    onSpawnLaunchedBody = vm::spawnLaunchedBody
                )

                if (snapshotState.value.bodies.isEmpty()) {
                    Text(
                        text = stringResource(R.string.zig_gravity_empty_state),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ZigGravityColor.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("gravity_empty_state_text")
                    )
                }

                HudBar(
                    ui = ui,
                    onTogglePlayPause = vm::togglePlayPause,
                    onSetSpeed = vm::setSpeed,
                    onReset = vm::reset,
                    onLongReset = vm::openPresetPicker,
                    onToggleTrails = vm::toggleTrails,
                    onToggleTeaching = vm::toggleTeaching,
                    onToggleTheme = vm::toggleTheme,
                    onToggleLanguage = vm::toggleLanguage,
                    onTogglePerfOverlay = vm::togglePerfOverlay,
                    onOpenChallenges = vm::openChallengesSheet,
                    onOpenCatalog = vm::openCatalog,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Debug-only frame-time overlay (zero release-build surface unless ZigGravityDebug explicitly enabled)
                if ((BuildConfig.DEBUG && ui.showPerfOverlay) || ZigGravityDebug.enabled) {
                    val fps = if (vm.meanFrameMs > 0.0) (1000.0 / vm.meanFrameMs).roundToInt() else 60
                    val perfText = String.format(
                        java.util.Locale.US,
                        "FPS: %d · mean: %.1f ms · max: %.1f ms · bodies: %d",
                        fps,
                        vm.meanFrameMs,
                        vm.maxFrameMs,
                        snapshotState.value.bodies.size
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 76.dp, end = 12.dp)
                            .testTag("perf_overlay"),
                        shape = RoundedCornerShape(8.dp),
                        color = ZigGravityColor.glassContainer,
                        border = BorderStroke(1.dp, ZigGravityColor.glassStroke)
                    ) {
                        Text(
                            text = perfText,
                            style = MaterialTheme.typography.labelSmall,
                            color = ZigGravityColor.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Teaching Card Overlay
                if (activeCard != null && (ui.teachingMode || activeChallengeState != null)) {
                    TeachingCard(
                        card = activeCard!!,
                        onDismiss = vm::dismissCard,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = if (activeChallengeState?.phase == ChallengePhase.OBSERVATION) 72.dp else 16.dp)
                    )
                }

                // Challenge Observation Hint Chip
                if (activeChallengeState?.phase == ChallengePhase.OBSERVATION) {
                    ChallengeHintChip(
                        onReady = {
                            vm.completeChallengeObservation { resId -> context.getString(resId) }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                    )
                }

                // Challenge Prediction Dialog
                if (activeChallengeState?.phase == ChallengePhase.PREDICTION) {
                    ChallengePredictionDialog(
                        challenge = activeChallengeState!!.challenge,
                        onSelectOption = vm::submitChallengePrediction,
                        onDismiss = vm::dismissChallenge
                    )
                }

                // Inspector Bottom Sheet
                if (ui.showInspectorSheet && ui.selectedId != -1L) {
                    val selectedBody = snapshotState.value.bodies.find { it.id == ui.selectedId }
                    if (selectedBody != null) {
                        InspectorSheet(
                            body = selectedBody,
                            metersPerDp = snapshotState.value.metersPerDp,
                            teachingMode = ui.teachingMode,
                            onSetMass = vm::setMass,
                            onSetRadius = vm::setRadius,
                            onSetVelocity = vm::setVelocity,
                            onCircularize = vm::circularize,
                            onDuplicate = vm::duplicateBody,
                            onRemove = vm::removeBody,
                            onDismiss = vm::dismissSheets
                        )
                    }
                }

                // Context Bottom Sheet
                if (ui.showContextSheet && ui.selectedId != -1L) {
                    ContextSheet(
                        bodyId = ui.selectedId,
                        onInspect = { vm.selectBody(ui.selectedId) },
                        onDuplicate = { vm.duplicateBody(ui.selectedId) },
                        onRemove = { vm.removeBody(ui.selectedId) },
                        onDismiss = vm::dismissSheets
                    )
                }

                // Catalog Bottom Sheet
                if (ui.showCatalogSheet) {
                    CatalogSheet(
                        isFull = ui.catalogFullNotice || snapshotState.value.bodies.size >= 20,
                        marbleBounce = ui.marbleBounce,
                        onToggleMarbleBounce = vm::toggleMarbleBounce,
                        onSelectType = vm::addBodyAtCenter,
                        onDismiss = vm::dismissSheets
                    )
                }

                // Preset Picker Bottom Sheet
                if (ui.showPresetPickerSheet) {
                    PresetPickerSheet(
                        currentPresetKey = ui.presetKey,
                        onSelectPreset = vm::loadPreset,
                        onDismiss = vm::dismissSheets
                    )
                }

                // Challenges Bottom Sheet
                if (ui.showChallengesSheet) {
                    ChallengesSheet(
                        onSelectChallenge = vm::startChallenge,
                        onDismiss = vm::dismissSheets
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { t -> vm.onFrame(t) }   // THE frame loop, main dispatcher
            }
        }
    }
}
