package com.zig.gravity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun createColorScheme(palette: GravityPalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.surfaceEdge,
        primaryContainer = palette.accent.copy(alpha = 0.2f),
        onPrimaryContainer = palette.onSurface,
        background = palette.surfaceEdge,
        onBackground = palette.onSurface,
        surface = palette.surfaceCenter,
        onSurface = palette.onSurface,
        surfaceVariant = palette.glassContainer,
        onSurfaceVariant = palette.onSurfaceVariant,
        outline = palette.glassStroke
    )
} else {
    lightColorScheme(
        primary = palette.accent,
        onPrimary = palette.surfaceCenter,
        primaryContainer = palette.accent.copy(alpha = 0.15f),
        onPrimaryContainer = palette.onSurface,
        background = palette.surfaceEdge,
        onBackground = palette.onSurface,
        surface = palette.surfaceCenter,
        onSurface = palette.onSurface,
        surfaceVariant = palette.glassContainer,
        onSurfaceVariant = palette.onSurfaceVariant,
        outline = palette.glassStroke
    )
}

@Composable
fun ZigGravityTheme(
    surface: GravitySurface = TableSurfaces.midnight,
    content: @Composable () -> Unit
) {
    val dark = surface.chromeMode == ChromeMode.DARK
    val palette = if (dark) ZigGravityColor.DarkPalette else ZigGravityColor.LightPalette
    val colorScheme = createColorScheme(palette, dark)

    CompositionLocalProvider(
        LocalGravityPalette provides palette,
        LocalTableSurface provides surface
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

@Composable
fun ZigGravityTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val surface = if (darkTheme) TableSurfaces.charcoal else TableSurfaces.paper
    ZigGravityTheme(surface = surface, content = content)
}

