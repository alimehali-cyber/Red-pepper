package com.zig.gravity.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class GravityPalette(
    val surfaceCenter: Color,
    val surfaceEdge: Color,
    val vignette: Color,
    val sheen: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val glassContainer: Color,
    val glassStroke: Color,
    val accent: Color,
    val bodySunBase: Color,
    val bodySunDeep: Color,
    val bodyEarthBase: Color,
    val bodyEarthDeep: Color,
    val bodyMoonBase: Color,
    val bodyMoonDeep: Color,
    val bodyAsteroidBase: Color,
    val bodyAsteroidDeep: Color,
    val bodyMarbleBase: Color,
    val bodyMarbleDeep: Color,
    val bodyBlackHoleDisk: Color,
)

val LocalGravityPalette = staticCompositionLocalOf { ZigGravityColor.DarkPalette }

object ZigGravityColor {
    // Surfaces & Chrome (dark tabletop)
    val DarkPalette = GravityPalette(
        surfaceCenter = Color(0xFF1C1F26),   // radial gradient center (slightly lighter)
        surfaceEdge = Color(0xFF16181D),     // radial gradient edge
        vignette = Color(0xFF0F1116),        // corner darkening
        sheen = Color(0x0FFFFFFF),           // white at ~6% alpha
        onSurface = Color(0xFFF1F4F7),
        onSurfaceVariant = Color(0xFFABB6C2),
        glassContainer = Color(0xB3161A22),  // #161A22 at ~70% alpha
        glassStroke = Color(0x0FFFFFFF),     // white at ~6% alpha
        accent = Color(0xFF2DD4BF),          // vivid aqua-teal
        bodySunBase = Color(0xFFFFDC4A),
        bodySunDeep = Color(0xFFF5A623),     // bright yellow / deep
        bodyEarthBase = Color(0xFF3FA1FF),
        bodyEarthDeep = Color(0xFF1E63D8),   // vivid azure / deep
        bodyMoonBase = Color(0xFFFFFFFF),
        bodyMoonDeep = Color(0xFFC7CCD8),    // bright white / pearl
        bodyAsteroidBase = Color(0xFFD89B66),
        bodyAsteroidDeep = Color(0xFF96603B), // warm terracotta
        bodyMarbleBase = Color(0xFFF4F6FB),
        bodyMarbleDeep = Color(0xFFBFC7DA),  // cool pearl
        bodyBlackHoleDisk = Color(0xFF0A0A0C) // ring uses accent
    )

    // Surfaces & Chrome (light paper)
    val LightPalette = GravityPalette(
        surfaceCenter = Color(0xFFF4F1EA),   // warm paper radial center
        surfaceEdge = Color(0xFFE9E4D9),     // warm paper radial edge
        vignette = Color(0xFFDCD5C4),        // faint, max ~22% alpha
        sheen = Color(0x06FFFFFF),           // white 2.5%
        onSurface = Color(0xFF23272E),
        onSurfaceVariant = Color(0xFF5D6672),
        glassContainer = Color(0xA6FFFFFF),  // white ~65%
        glassStroke = Color(0x1425282E),     // dark ~8%
        accent = Color(0xFF0E9F8F),          // deep teal
        bodySunBase = Color(0xFFFFDC4A),
        bodySunDeep = Color(0xFFF5A623),     // bright yellow / deep
        bodyEarthBase = Color(0xFF3FA1FF),
        bodyEarthDeep = Color(0xFF1E63D8),   // vivid azure / deep
        bodyMoonBase = Color(0xFFFFFFFF),
        bodyMoonDeep = Color(0xFFC7CCD8),    // bright white / pearl
        bodyAsteroidBase = Color(0xFFD89B66),
        bodyAsteroidDeep = Color(0xFF96603B), // warm terracotta
        bodyMarbleBase = Color(0xFFF4F6FB),
        bodyMarbleDeep = Color(0xFFBFC7DA),  // cool pearl
        bodyBlackHoleDisk = Color(0xFF26262B) // ring uses light accent teal
    )

    // Current palette for composables
    val current: GravityPalette
        @Composable
        get() = LocalGravityPalette.current

    // Composable accessors for dynamic styling
    val surfaceCenter: Color @Composable get() = LocalGravityPalette.current.surfaceCenter
    val surfaceEdge: Color @Composable get() = LocalGravityPalette.current.surfaceEdge
    val vignette: Color @Composable get() = LocalGravityPalette.current.vignette
    val sheen: Color @Composable get() = LocalGravityPalette.current.sheen
    val onSurface: Color @Composable get() = LocalGravityPalette.current.onSurface
    val onSurfaceVariant: Color @Composable get() = LocalGravityPalette.current.onSurfaceVariant
    val glassContainer: Color @Composable get() = LocalGravityPalette.current.glassContainer
    val glassStroke: Color @Composable get() = LocalGravityPalette.current.glassStroke
    val accent: Color @Composable get() = LocalGravityPalette.current.accent

    val bodySunBase: Color @Composable get() = LocalGravityPalette.current.bodySunBase
    val bodySunDeep: Color @Composable get() = LocalGravityPalette.current.bodySunDeep
    val bodyEarthBase: Color @Composable get() = LocalGravityPalette.current.bodyEarthBase
    val bodyEarthDeep: Color @Composable get() = LocalGravityPalette.current.bodyEarthDeep
    val bodyMoonBase: Color @Composable get() = LocalGravityPalette.current.bodyMoonBase
    val bodyMoonDeep: Color @Composable get() = LocalGravityPalette.current.bodyMoonDeep
    val bodyAsteroidBase: Color @Composable get() = LocalGravityPalette.current.bodyAsteroidBase
    val bodyAsteroidDeep: Color @Composable get() = LocalGravityPalette.current.bodyAsteroidDeep
    val bodyMarbleBase: Color @Composable get() = LocalGravityPalette.current.bodyMarbleBase
    val bodyMarbleDeep: Color @Composable get() = LocalGravityPalette.current.bodyMarbleDeep
    val bodyBlackHoleDisk: Color @Composable get() = LocalGravityPalette.current.bodyBlackHoleDisk
}

