package com.zig.gravity.sim

import com.zig.gravity.physics.BodyType
import com.zig.gravity.ui.theme.ChromeMode
import com.zig.gravity.ui.theme.TableSurfaces

data class BodyRender(
    val id: Long,
    val type: BodyType,
    val x: Double,
    val y: Double,
    val vx: Double,
    val vy: Double,
    val massKg: Double,
    val radiusMeters: Double,
    val partnerId: Long = 0L
)

data class PreviewState(
    val ghostX: Double,
    val ghostY: Double,
    val ghostRadiusDp: Float,
    val type: BodyType,
    val pathXs: FloatArray,
    val pathYs: FloatArray,
    val pathLength: Int,
    val approximate: Boolean
)

data class PulseRing(
    val xMeters: Double,
    val yMeters: Double,
    val startRadiusMeters: Double,
    val expiryNanos: Long
)

data class SimSnapshot(
    val bodies: List<BodyRender>,
    val simTime: Double,
    val selectedId: Long,        // -1 when none
    val metersPerDp: Double,     // 0.0 until the viewport is known
    val preview: PreviewState? = null,
    val pulses: List<PulseRing> = emptyList()
)

/** Chrome state — changes ONLY on user action (never per frame). */
data class UiState(
    val running: Boolean = true,
    val speed: Double = 1.0,
    val trailsEnabled: Boolean = true,
    val selectedId: Long = -1L,
    val showInspectorSheet: Boolean = false,
    val showContextSheet: Boolean = false,
    val showCatalogSheet: Boolean = false,
    val catalogFullNotice: Boolean = false,
    val lastPickedType: BodyType = BodyType.TEST_MARBLE,
    val teachingMode: Boolean = true,
    val marbleBounce: Boolean = false,
    val showPresetPickerSheet: Boolean = false,
    val showChallengesSheet: Boolean = false,
    val showTableSurfacesSheet: Boolean = false,
    val presetKey: String? = "sun_earth",
    val tableSurface: String = "midnight",
    val language: String = "fa",
    val showPerfOverlay: Boolean = false
) {
    val darkTheme: Boolean
        get() = TableSurfaces.get(tableSurface).chromeMode == ChromeMode.DARK
}

