package com.zig.gravity.sim

import com.zig.gravity.physics.BodyType
import com.zig.gravity.physics.EngineConstants

object BodyDefaults {
    fun massKg(type: BodyType): Double = when (type) {
        BodyType.SUN -> EngineConstants.M_SUN
        BodyType.PLANET -> EngineConstants.M_EARTH
        BodyType.MOON -> EngineConstants.M_MOON
        BodyType.ASTEROID -> 1.0e-4 * EngineConstants.M_EARTH
        BodyType.TEST_MARBLE -> 0.0                     // massless test particle
        BodyType.BLACK_HOLE -> 5.0 * EngineConstants.M_SUN
        BodyType.WORMHOLE_MOUTH -> 0.0                  // Phase 8
    }

    /** Inspector mass-slider ranges (kg): min, max. */
    fun massRangeKg(type: BodyType): Pair<Double, Double> = when (type) {
        BodyType.SUN -> 0.1 * EngineConstants.M_SUN to 10.0 * EngineConstants.M_SUN
        BodyType.PLANET -> 0.01 * EngineConstants.M_EARTH to 100.0 * EngineConstants.M_EARTH
        BodyType.MOON -> 0.001 * EngineConstants.M_EARTH to 10.0 * EngineConstants.M_EARTH
        BodyType.ASTEROID, BodyType.TEST_MARBLE -> 1e-6 * EngineConstants.M_EARTH to 1e-3 * EngineConstants.M_EARTH
        BodyType.BLACK_HOLE -> 1.0 * EngineConstants.M_SUN to 50.0 * EngineConstants.M_SUN
        BodyType.WORMHOLE_MOUTH -> 0.0 to 0.0
    }
}
