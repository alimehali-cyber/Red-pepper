package com.zig.gravity.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun tableSurfacesCatalogContainsAllSevenSurfaces() {
        val expectedKeys = listOf("midnight", "charcoal", "ocean", "lavender", "paper", "porcelain", "blush")
        assertEquals(7, TableSurfaces.allSurfaces.size)
        for (k in expectedKeys) {
            assertTrue("Catalog contains $k", TableSurfaces.contains(k))
            val surface = TableSurfaces.get(k)
            assertEquals(k, surface.key)
            assertTrue("Gradient colors not empty for $k", surface.gradientColors.size >= 2)
        }
        // Fallback for unknown key
        val fallback = TableSurfaces.get("non_existent_key")
        assertEquals("midnight", fallback.key)
    }

    @Test
    fun starPatternsDeterministicAndBounded() {
        val midnight = TableSurfaces.get("midnight")
        val pattern = midnight.pattern
        assertNotNull(pattern)
        val dots1 = generateStarDots(pattern!!)
        val dots2 = generateStarDots(pattern)
        assertEquals(pattern.dotCount + pattern.extraBrightCount, dots1.size)
        assertEquals(dots1.size, dots2.size)
        for (i in dots1.indices) {
            assertEquals(dots1[i].normX, dots2[i].normX, 0f)
            assertEquals(dots1[i].normY, dots2[i].normY, 0f)
            assertEquals(dots1[i].radiusDp, dots2[i].radiusDp, 0f)
            assertEquals(dots1[i].alpha, dots2[i].alpha, 0f)
            assertTrue(dots1[i].normX in 0f..1f)
            assertTrue(dots1[i].normY in 0f..1f)
        }
    }
}
