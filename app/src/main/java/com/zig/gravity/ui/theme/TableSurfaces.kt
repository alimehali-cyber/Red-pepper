package com.zig.gravity.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.alijafari.red.astronomy.R

enum class ChromeMode {
    DARK,
    LIGHT
}

enum class GradientType {
    RADIAL,
    LINEAR_VERTICAL
}

data class StarPatternSpec(
    val dotCount: Int,
    val minRadiusDp: Float,
    val maxRadiusDp: Float,
    val minAlpha: Float,
    val maxAlpha: Float,
    val extraBrightCount: Int = 0,
    val extraBrightRadiusDp: Float = 1.8f,
    val extraBrightAlpha: Float = 0.5f,
    val seed: Long
)

data class StarDot(
    val normX: Float,
    val normY: Float,
    val radiusDp: Float,
    val alpha: Float
)

data class GravitySurface(
    val key: String,
    val titleRes: Int,
    val chromeMode: ChromeMode,
    val gradientType: GradientType,
    val gradientColors: List<Color>,
    val radialCenterNorm: Pair<Float, Float> = Pair(0.5f, 0.38f),
    val vignetteColor: Color = Color.Black,
    val vignetteStrength: Float = 0.0f,
    val sheenStrength: Float = 0.0f,
    val pattern: StarPatternSpec? = null,
    val trailAlphaPair: Pair<Float, Float> = if (chromeMode == ChromeMode.DARK) Pair(0.18f, 0.26f) else Pair(0.22f, 0.30f)
)

object TableSurfaces {

    // 1. midnight (DEFAULT)
    // آسمانِ شب / Midnight Sky
    // radial (50%, 38%): #232D4F → #0B0F1E
    // stars: 130 dots, r 0.7–1.5 dp, alpha 0.16–0.42, plus 10 dots r 1.8 dp alpha 0.5, seed 7
    // chrome: dark
    val midnight = GravitySurface(
        key = "midnight",
        titleRes = R.string.zig_gravity_surface_midnight,
        chromeMode = ChromeMode.DARK,
        gradientType = GradientType.RADIAL,
        gradientColors = listOf(Color(0xFF232D4F), Color(0xFF0B0F1E)),
        radialCenterNorm = Pair(0.50f, 0.38f),
        pattern = StarPatternSpec(
            dotCount = 130,
            minRadiusDp = 0.7f,
            maxRadiusDp = 1.5f,
            minAlpha = 0.16f,
            maxAlpha = 0.42f,
            extraBrightCount = 10,
            extraBrightRadiusDp = 1.8f,
            extraBrightAlpha = 0.50f,
            seed = 7L
        ),
        trailAlphaPair = Pair(0.18f, 0.26f)
    )

    // 2. charcoal
    // زغالی / Charcoal
    // EXACTLY the original dark surface (#1C1F26→#16181D, vignette #0F1116 @33%, sheen 3.5%)
    // chrome: dark
    val charcoal = GravitySurface(
        key = "charcoal",
        titleRes = R.string.zig_gravity_surface_charcoal,
        chromeMode = ChromeMode.DARK,
        gradientType = GradientType.RADIAL,
        gradientColors = listOf(Color(0xFF1C1F26), Color(0xFF16181D)),
        radialCenterNorm = Pair(0.50f, 0.38f),
        vignetteColor = Color(0xFF0F1116),
        vignetteStrength = 0.33f,
        sheenStrength = 0.035f,
        pattern = null,
        trailAlphaPair = Pair(0.18f, 0.26f)
    )

    // 3. ocean
    // اقیانوسِ ژرف / Deep Ocean
    // linear vertical: #10263B → #071523
    // chrome: dark
    val ocean = GravitySurface(
        key = "ocean",
        titleRes = R.string.zig_gravity_surface_ocean,
        chromeMode = ChromeMode.DARK,
        gradientType = GradientType.LINEAR_VERTICAL,
        gradientColors = listOf(Color(0xFF10263B), Color(0xFF071523)),
        pattern = null,
        trailAlphaPair = Pair(0.18f, 0.26f)
    )

    // 4. lavender
    // گرگومیشِ بنفش / Lavender Dusk
    // radial: #2C2148 → #140E24
    // stars: 90 dots, r 0.7–1.3 dp, alpha 0.14–0.35, seed 21
    // chrome: dark
    val lavender = GravitySurface(
        key = "lavender",
        titleRes = R.string.zig_gravity_surface_lavender,
        chromeMode = ChromeMode.DARK,
        gradientType = GradientType.RADIAL,
        gradientColors = listOf(Color(0xFF2C2148), Color(0xFF140E24)),
        radialCenterNorm = Pair(0.50f, 0.38f),
        pattern = StarPatternSpec(
            dotCount = 90,
            minRadiusDp = 0.7f,
            maxRadiusDp = 1.3f,
            minAlpha = 0.14f,
            maxAlpha = 0.35f,
            extraBrightCount = 0,
            seed = 21L
        ),
        trailAlphaPair = Pair(0.18f, 0.26f)
    )

    // 5. paper
    // کاغذِ گرم / Warm Paper
    // EXACTLY the original light surface (#F4F1EA→#E9E4D9, vignette #DCD5C4 @22%)
    // chrome: light
    val paper = GravitySurface(
        key = "paper",
        titleRes = R.string.zig_gravity_surface_paper,
        chromeMode = ChromeMode.LIGHT,
        gradientType = GradientType.RADIAL,
        gradientColors = listOf(Color(0xFFF4F1EA), Color(0xFFE9E4D9)),
        radialCenterNorm = Pair(0.50f, 0.38f),
        vignetteColor = Color(0xFFDCD5C4),
        vignetteStrength = 0.22f,
        sheenStrength = 0.0f,
        pattern = null,
        trailAlphaPair = Pair(0.22f, 0.30f)
    )

    // 6. porcelain
    // مهِ صبح / Morning Mist
    // linear vertical: #F8FAFC → #E2E8F0
    // chrome: light
    val porcelain = GravitySurface(
        key = "porcelain",
        titleRes = R.string.zig_gravity_surface_porcelain,
        chromeMode = ChromeMode.LIGHT,
        gradientType = GradientType.LINEAR_VERTICAL,
        gradientColors = listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0)),
        pattern = null,
        trailAlphaPair = Pair(0.22f, 0.30f)
    )

    // 7. blush
    // شنِ گلبهی / Blush Sand
    // radial: #FBF1EC → #EFDCCF
    // chrome: light
    val blush = GravitySurface(
        key = "blush",
        titleRes = R.string.zig_gravity_surface_blush,
        chromeMode = ChromeMode.LIGHT,
        gradientType = GradientType.RADIAL,
        gradientColors = listOf(Color(0xFFFBF1EC), Color(0xFFEFDCCF)),
        radialCenterNorm = Pair(0.50f, 0.38f),
        pattern = null,
        trailAlphaPair = Pair(0.22f, 0.30f)
    )

    val allSurfaces: List<GravitySurface> = listOf(
        midnight,
        charcoal,
        ocean,
        lavender,
        paper,
        porcelain,
        blush
    )

    private val surfaceMap: Map<String, GravitySurface> = allSurfaces.associateBy { it.key }

    fun contains(key: String): Boolean = surfaceMap.containsKey(key)

    fun get(key: String): GravitySurface = surfaceMap[key] ?: midnight
}

val LocalTableSurface = staticCompositionLocalOf { TableSurfaces.midnight }

fun generateStarDots(spec: StarPatternSpec): List<StarDot> {
    val rng = java.util.Random(spec.seed)
    val list = ArrayList<StarDot>(spec.dotCount + spec.extraBrightCount)

    fun nextDot(rMin: Float, rMax: Float, aMin: Float, aMax: Float): StarDot {
        var nx: Float
        var ny: Float
        while (true) {
            nx = rng.nextFloat()
            ny = rng.nextFloat()
            // avoid center 15% region: [0.425f, 0.575f] on both axes
            val inCenterX = nx in 0.425f..0.575f
            val inCenterY = ny in 0.425f..0.575f
            if (!(inCenterX && inCenterY)) {
                break
            }
        }
        val r = rMin + rng.nextFloat() * (rMax - rMin)
        val a = aMin + rng.nextFloat() * (aMax - aMin)
        return StarDot(nx, ny, r, a)
    }

    for (i in 0 until spec.dotCount) {
        list.add(nextDot(spec.minRadiusDp, spec.maxRadiusDp, spec.minAlpha, spec.maxAlpha))
    }
    for (i in 0 until spec.extraBrightCount) {
        list.add(nextDot(spec.extraBrightRadiusDp, spec.extraBrightRadiusDp, spec.extraBrightAlpha, spec.extraBrightAlpha))
    }
    return list
}
