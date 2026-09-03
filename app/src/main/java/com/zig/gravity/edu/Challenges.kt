package com.zig.gravity.edu

import com.alijafari.red.astronomy.R
import com.zig.gravity.physics.BodyType
import com.zig.gravity.physics.EngineConstants
import kotlin.math.sqrt

sealed class ChallengeSetup {
    data class PresetKey(val key: String) : ChallengeSetup()
    data class Custom(val bodies: List<CustomBody>) : ChallengeSetup()
}

data class CustomBody(
    val type: BodyType,
    val massKg: Double,
    val radiusMeters: Double,
    val x: Double,
    val y: Double,
    val vx: Double,
    val vy: Double,
    val isWormholePartnerWithNext: Boolean = false
)

data class ChallengeDef(
    val id: String,
    val titleRes: Int,
    val introRes: Int,
    val optionsRes: List<Int>,
    val correctIndex: Int,
    val setup: ChallengeSetup,
    val resultCardId: String,
    val successLineRes: Int,
    val autoFireEventKey: String? = null,
    val hasMutation: Boolean = false
)

object Challenges {
    val list: List<ChallengeDef> = listOf(
        // 1. Double the mass
        ChallengeDef(
            id = "double_mass",
            titleRes = R.string.zig_gravity_challenge_1_title,
            introRes = R.string.zig_gravity_challenge_1_intro,
            optionsRes = listOf(
                R.string.zig_gravity_challenge_1_opt_1,
                R.string.zig_gravity_challenge_1_opt_2,
                R.string.zig_gravity_challenge_1_opt_3
            ),
            correctIndex = 1, // Option 2
            setup = ChallengeSetup.PresetKey("sun_earth"),
            resultCardId = "orbit",
            successLineRes = R.string.zig_gravity_challenge_1_success,
            hasMutation = true
        ),
        // 2. Distant Earth
        ChallengeDef(
            id = "distant_earth",
            titleRes = R.string.zig_gravity_challenge_2_title,
            introRes = R.string.zig_gravity_challenge_2_intro,
            optionsRes = listOf(
                R.string.zig_gravity_challenge_2_opt_1,
                R.string.zig_gravity_challenge_2_opt_2,
                R.string.zig_gravity_challenge_2_opt_3
            ),
            correctIndex = 1, // Option 2
            setup = ChallengeSetup.Custom(
                listOf(
                    CustomBody(
                        type = BodyType.SUN,
                        massKg = EngineConstants.M_SUN,
                        radiusMeters = EngineConstants.R_SUN,
                        x = 0.0,
                        y = 0.0,
                        vx = 0.0,
                        vy = 0.0
                    ),
                    CustomBody(
                        type = BodyType.PLANET,
                        massKg = EngineConstants.M_EARTH,
                        radiusMeters = EngineConstants.R_EARTH,
                        x = 1.5 * EngineConstants.AU,
                        y = 0.0,
                        vx = 0.0,
                        vy = sqrt(EngineConstants.G * EngineConstants.M_SUN / (1.5 * EngineConstants.AU))
                    )
                )
            ),
            resultCardId = "orbit",
            successLineRes = R.string.zig_gravity_challenge_2_success
        ),
        // 3. Too fast
        ChallengeDef(
            id = "too_fast",
            titleRes = R.string.zig_gravity_challenge_3_title,
            introRes = R.string.zig_gravity_challenge_3_intro,
            optionsRes = listOf(
                R.string.zig_gravity_challenge_3_opt_1,
                R.string.zig_gravity_challenge_3_opt_2,
                R.string.zig_gravity_challenge_3_opt_3
            ),
            correctIndex = 2, // Option 3
            setup = ChallengeSetup.Custom(
                listOf(
                    CustomBody(
                        type = BodyType.SUN,
                        massKg = EngineConstants.M_SUN,
                        radiusMeters = EngineConstants.R_SUN,
                        x = 0.0,
                        y = 0.0,
                        vx = 0.0,
                        vy = 0.0
                    ),
                    CustomBody(
                        type = BodyType.TEST_MARBLE,
                        massKg = 1.0e15,
                        radiusMeters = 1.0e4,
                        x = EngineConstants.AU,
                        y = 0.0,
                        vx = 0.0,
                        vy = 45000.0
                    )
                )
            ),
            resultCardId = "escape",
            successLineRes = R.string.zig_gravity_challenge_3_success,
            autoFireEventKey = "escape"
        ),
        // 4. Two-star dance
        ChallengeDef(
            id = "two_star_dance",
            titleRes = R.string.zig_gravity_challenge_4_title,
            introRes = R.string.zig_gravity_challenge_4_intro,
            optionsRes = listOf(
                R.string.zig_gravity_challenge_4_opt_1,
                R.string.zig_gravity_challenge_4_opt_2,
                R.string.zig_gravity_challenge_4_opt_3
            ),
            correctIndex = 1, // Option 2
            setup = ChallengeSetup.PresetKey("binary_stars"),
            resultCardId = "dance",
            successLineRes = R.string.zig_gravity_challenge_4_success,
            autoFireEventKey = "dance"
        ),
        // 5. Planetary collision
        ChallengeDef(
            id = "planetary_collision",
            titleRes = R.string.zig_gravity_challenge_5_title,
            introRes = R.string.zig_gravity_challenge_5_intro,
            optionsRes = listOf(
                R.string.zig_gravity_challenge_5_opt_1,
                R.string.zig_gravity_challenge_5_opt_2,
                R.string.zig_gravity_challenge_5_opt_3
            ),
            correctIndex = 2, // Option 3
            setup = ChallengeSetup.PresetKey("collision_course"),
            resultCardId = "merge",
            successLineRes = R.string.zig_gravity_challenge_5_success,
            autoFireEventKey = "merge"
        ),
        // 6. Captured by Black Hole
        ChallengeDef(
            id = "captured",
            titleRes = R.string.zig_gravity_challenge_6_title,
            introRes = R.string.zig_gravity_challenge_6_intro,
            optionsRes = listOf(
                R.string.zig_gravity_challenge_6_opt_1,
                R.string.zig_gravity_challenge_6_opt_2,
                R.string.zig_gravity_challenge_6_opt_3
            ),
            correctIndex = 2, // Option 3
            setup = ChallengeSetup.Custom(
                listOf(
                    CustomBody(
                        type = BodyType.BLACK_HOLE,
                        massKg = 5.0 * EngineConstants.M_SUN,
                        radiusMeters = 1.5e4,
                        x = 0.0,
                        y = 0.0,
                        vx = 0.0,
                        vy = 0.0
                    ),
                    CustomBody(
                        type = BodyType.TEST_MARBLE,
                        massKg = 1.0e15,
                        radiusMeters = 1.0e4,
                        x = -1.5 * EngineConstants.AU,
                        y = 0.0,
                        vx = 30000.0,
                        vy = 0.0
                    )
                )
            ),
            resultCardId = "capture",
            successLineRes = R.string.zig_gravity_challenge_6_success,
            autoFireEventKey = "capture"
        ),
        // 7. Wormhole trip
        ChallengeDef(
            id = "wormhole_trip",
            titleRes = R.string.zig_gravity_challenge_7_title,
            introRes = R.string.zig_gravity_challenge_7_intro,
            optionsRes = listOf(
                R.string.zig_gravity_challenge_7_opt_1,
                R.string.zig_gravity_challenge_7_opt_2,
                R.string.zig_gravity_challenge_7_opt_3
            ),
            correctIndex = 1, // Option 2
            setup = ChallengeSetup.Custom(
                listOf(
                    CustomBody(
                        type = BodyType.WORMHOLE_MOUTH,
                        massKg = 0.0,
                        radiusMeters = 1.5e4,
                        x = -0.9 * EngineConstants.AU,
                        y = 0.0,
                        vx = 0.0,
                        vy = 0.0,
                        isWormholePartnerWithNext = true
                    ),
                    CustomBody(
                        type = BodyType.WORMHOLE_MOUTH,
                        massKg = 0.0,
                        radiusMeters = 1.5e4,
                        x = 0.9 * EngineConstants.AU,
                        y = 0.0,
                        vx = 0.0,
                        vy = 0.0
                    ),
                    CustomBody(
                        type = BodyType.TEST_MARBLE,
                        massKg = 1.0e15,
                        radiusMeters = 1.0e4,
                        x = -1.5 * EngineConstants.AU,
                        y = 0.0,
                        vx = 30000.0,
                        vy = 0.0
                    )
                )
            ),
            resultCardId = "wormhole",
            successLineRes = R.string.zig_gravity_challenge_7_success,
            autoFireEventKey = "wormhole"
        ),
        // 8. Why doesn't the Moon fall?
        ChallengeDef(
            id = "why_moon_doesnt_fall",
            titleRes = R.string.zig_gravity_challenge_8_title,
            introRes = R.string.zig_gravity_challenge_8_intro,
            optionsRes = listOf(
                R.string.zig_gravity_challenge_8_opt_1,
                R.string.zig_gravity_challenge_8_opt_2,
                R.string.zig_gravity_challenge_8_opt_3
            ),
            correctIndex = 2, // Option 3
            setup = ChallengeSetup.PresetKey("sun_earth_moon"),
            resultCardId = "orbit",
            successLineRes = R.string.zig_gravity_challenge_8_success
        )
    )

    fun get(id: String): ChallengeDef? = list.find { it.id == id }
}
