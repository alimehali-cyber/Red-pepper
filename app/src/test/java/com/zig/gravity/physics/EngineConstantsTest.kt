package com.zig.gravity.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class EngineConstantsTest {

    @Test
    fun constantsAreExactSI() {
        // Physical constants (CODATA 2018 / educational roundings)
        assertEquals(6.67430e-11, EngineConstants.G, 0.0)
        assertEquals(2.99792458e8, EngineConstants.C_LIGHT, 0.0)
        assertEquals(1.989e30, EngineConstants.M_SUN, 0.0)
        assertEquals(6.957e8, EngineConstants.R_SUN, 0.0)
        assertEquals(5.972e24, EngineConstants.M_EARTH, 0.0)
        assertEquals(6.371e6, EngineConstants.R_EARTH, 0.0)
        assertEquals(7.348e22, EngineConstants.M_MOON, 0.0)
        assertEquals(1.737e6, EngineConstants.R_MOON, 0.0)
        assertEquals(1.496e11, EngineConstants.AU, 0.0)

        // Reference values
        assertEquals(29.78e3, EngineConstants.EARTH_ORBITAL_SPEED, 0.0)
        assertEquals(3.844e8, EngineConstants.MOON_ORBIT_RADIUS, 0.0)
        assertEquals(27.32 * 24.0 * 3600.0, EngineConstants.MOON_PERIOD_SECONDS, 0.0)
        assertEquals(1.022e3, EngineConstants.MOON_ORBITAL_SPEED, 0.0)

        // Numerical stability parameters
        assertEquals(1.0e6, EngineConstants.EPS_SOFT, 0.0)
        assertEquals(3600.0, EngineConstants.DT, 0.0)
        assertEquals(1.0e6, EngineConstants.BASE_SIM_SECONDS_PER_REAL_SECOND, 0.0)
        assertEquals(96, EngineConstants.MAX_SUBSTEPS)
        assertEquals(20, EngineConstants.MAX_BODIES)
        assertEquals(1000.0e3, EngineConstants.VELOCITY_HARD_CAP, 0.0)
        assertEquals(0.2, EngineConstants.REFINEMENT_TRIGGER_FRACTION, 0.0)
        assertEquals(3, EngineConstants.REFINEMENT_MAX_DEPTH)

        // Scene scale
        assertEquals(3.0, EngineConstants.VIEWPORT_WIDTH_AU, 0.0)

        // Derived-value cross-checks (relative tolerance 1e-3)
        val calculatedEarthSpeed = sqrt(EngineConstants.G * EngineConstants.M_SUN / EngineConstants.AU)
        val relativeErrorEarthSpeed = Math.abs(calculatedEarthSpeed - EngineConstants.EARTH_ORBITAL_SPEED) / EngineConstants.EARTH_ORBITAL_SPEED
        assertTrue("Earth orbital speed cross-check within 1e-3 relative error", relativeErrorEarthSpeed < 1e-3)

        val moonPeriodSecondsExpected = 27.32 * 24.0 * 3600.0
        val relativeErrorMoonPeriod = Math.abs(EngineConstants.MOON_PERIOD_SECONDS - moonPeriodSecondsExpected) / moonPeriodSecondsExpected
        assertTrue("Moon period seconds cross-check within 1e-3 relative error", relativeErrorMoonPeriod < 1e-3)

        // Schwarzschild sanity bands: 2*G*M_SUN/C_LIGHT² in [2900, 3000] m
        val rSchwarzschildSun = 2.0 * EngineConstants.G * EngineConstants.M_SUN / (EngineConstants.C_LIGHT * EngineConstants.C_LIGHT)
        assertTrue("Sun Schwarzschild radius in [2900, 3000] m, actual: $rSchwarzschildSun", rSchwarzschildSun in 2900.0..3000.0)

        // Schwarzschild sanity band: 2*G*M_EARTH/C_LIGHT² in [0.0085, 0.0090] m
        val rSchwarzschildEarth = 2.0 * EngineConstants.G * EngineConstants.M_EARTH / (EngineConstants.C_LIGHT * EngineConstants.C_LIGHT)
        assertTrue("Earth Schwarzschild radius in [0.0085, 0.0090] m, actual: $rSchwarzschildEarth", rSchwarzschildEarth in 0.0085..0.0090)
    }
}
