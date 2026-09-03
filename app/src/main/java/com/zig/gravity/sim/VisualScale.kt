package com.zig.gravity.sim

import com.zig.gravity.physics.BodyType
import com.zig.gravity.physics.EngineConstants

/**
 * Visual sizes in dp (Roadmap v5 §3.6a). Collision radius == visual radius at
 * scene scale, so these defaults are written INTO state.radius (meters) via
 * metersPerDp whenever the scene scale is (re)established.
 */
object VisualScale {
    const val DEFAULT_METERS_PER_DP: Double = EngineConstants.VIEWPORT_WIDTH_AU * EngineConstants.AU / (1080.0 / 2.75)

    fun defaultDp(type: BodyType): Float = when (type) {
        BodyType.SUN -> 26f
        BodyType.PLANET -> 10f
        BodyType.MOON -> 6f
        BodyType.ASTEROID -> 4f
        BodyType.TEST_MARBLE -> 5f
        BodyType.BLACK_HOLE -> 14f
        BodyType.WORMHOLE_MOUTH -> 8f
    }

    fun minDp(type: BodyType): Float = when (type) {
        BodyType.ASTEROID -> 3f
        BodyType.TEST_MARBLE -> 4f
        BodyType.MOON -> 5f
        BodyType.PLANET -> 8f
        BodyType.SUN -> 20f
        BodyType.BLACK_HOLE -> 10f
        BodyType.WORMHOLE_MOUTH -> 6f
    }

    fun maxDp(type: BodyType): Float = when (type) {
        BodyType.ASTEROID -> 6f
        BodyType.TEST_MARBLE -> 7f
        BodyType.MOON -> 8f
        BodyType.PLANET -> 16f
        BodyType.SUN -> 32f
        BodyType.BLACK_HOLE -> 20f
        BodyType.WORMHOLE_MOUTH -> 10f
    }
}
