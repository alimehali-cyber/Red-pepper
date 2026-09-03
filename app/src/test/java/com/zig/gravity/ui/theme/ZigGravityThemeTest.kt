package com.zig.gravity.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZigGravityThemeTest {

    @Test
    fun lightThemePaletteDistinctAndComplete() {
        val dark = ZigGravityColor.DarkPalette
        val light = ZigGravityColor.LightPalette

        // Canvas background / surfaces differ
        assertNotEquals(dark.surfaceCenter, light.surfaceCenter)
        assertNotEquals(dark.surfaceEdge, light.surfaceEdge)
        assertNotEquals(dark.glassContainer, light.glassContainer)
        assertNotEquals(dark.glassStroke, light.glassStroke)
        assertNotEquals(dark.onSurface, light.onSurface)
        assertNotEquals(dark.onSurfaceVariant, light.onSurfaceVariant)
        assertNotEquals(dark.accent, light.accent)

        // No unexpected fully transparent colors for main UI surfaces
        assertNotEquals(Color.Transparent, light.surfaceCenter)
        assertNotEquals(Color.Transparent, light.surfaceEdge)
        assertNotEquals(Color.Transparent, light.onSurface)
        assertNotEquals(Color.Transparent, light.glassContainer)

        // Body colors remain recognizable and valid non-transparent
        assertNotEquals(Color.Transparent, light.bodySunBase)
        assertNotEquals(Color.Transparent, light.bodyEarthBase)
        assertNotEquals(Color.Transparent, light.bodyMoonBase)
        assertNotEquals(Color.Transparent, light.bodyAsteroidBase)
        assertNotEquals(Color.Transparent, light.bodyMarbleBase)
        assertNotEquals(Color.Transparent, light.bodyBlackHoleDisk)
        assertNotEquals(Color.Transparent, light.accent)
    }
}
