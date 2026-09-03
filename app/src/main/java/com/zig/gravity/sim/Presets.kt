package com.zig.gravity.sim

import com.zig.gravity.physics.BodyType
import com.zig.gravity.physics.EngineConstants
import com.zig.gravity.physics.NBodyEngine
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class PresetBody(
    val type: BodyType,
    val massKg: Double,
    val x: Double,
    val y: Double,          // meters, origin = viewport center
    val vx: Double = 0.0,
    val vy: Double = 0.0,
    val dp: Float = VisualScale.defaultDp(type),   // visual size override
    val isWormholePartnerWithNext: Boolean = false
)

data class PresetDef(val key: String, val bodies: List<PresetBody>)

object Presets {

    private fun barycentric(key: String, list: List<PresetBody>): PresetDef {
        var totalMass = 0.0
        var totalPx = 0.0
        var totalPy = 0.0
        for (b in list) {
            totalMass += b.massKg
            totalPx += b.massKg * b.vx
            totalPy += b.massKg * b.vy
        }
        val vcmX = if (totalMass > 0.0) totalPx / totalMass else 0.0
        val vcmY = if (totalMass > 0.0) totalPy / totalMass else 0.0

        val corrected = list.map { b ->
            b.copy(vx = b.vx - vcmX, vy = b.vy - vcmY)
        }
        return PresetDef(key, corrected)
    }

    /**
     * SUN (1 M_SUN) at (0,0); PLANET (1 M_E) at (AU, 0) v (0, 29.78 km/s).
     */
    fun sunEarth(): PresetDef {
        val bodies = listOf(
            PresetBody(
                type = BodyType.SUN,
                massKg = EngineConstants.M_SUN,
                x = 0.0,
                y = 0.0,
                vx = 0.0,
                vy = 0.0
            ),
            PresetBody(
                type = BodyType.PLANET,
                massKg = EngineConstants.M_EARTH,
                x = EngineConstants.AU,
                y = 0.0,
                vx = 0.0,
                vy = EngineConstants.EARTH_ORBITAL_SPEED
            )
        )
        return barycentric("sun_earth", bodies)
    }

    /**
     * Sun + Earth + Moon at Earth + (MOON_ORBIT_RADIUS, 0), v = earthV + (0, 1.022 km/s).
     */
    fun sunEarthMoon(): PresetDef {
        val bodies = listOf(
            PresetBody(
                type = BodyType.SUN,
                massKg = EngineConstants.M_SUN,
                x = 0.0,
                y = 0.0,
                vx = 0.0,
                vy = 0.0
            ),
            PresetBody(
                type = BodyType.PLANET,
                massKg = EngineConstants.M_EARTH,
                x = EngineConstants.AU,
                y = 0.0,
                vx = 0.0,
                vy = EngineConstants.EARTH_ORBITAL_SPEED
            ),
            PresetBody(
                type = BodyType.MOON,
                massKg = EngineConstants.M_MOON,
                x = EngineConstants.AU + EngineConstants.MOON_ORBIT_RADIUS,
                y = 0.0,
                vx = 0.0,
                vy = EngineConstants.EARTH_ORBITAL_SPEED + EngineConstants.MOON_ORBITAL_SPEED
            )
        )
        return barycentric("sun_earth_moon", bodies)
    }

    /**
     * Two SUNs (0.9 M_SUN each), separation 1.2 AU, mutual circular orbit (each at ±0.6 AU, speed 0.5·√(G·1.8 M_SUN/1.2 AU), tangential, opposite);
     * PLANET (1 M_E) at 2.6 AU, v = √(G·1.8 M_SUN/2.6 AU) tangential.
     */
    fun binaryStars(): PresetDef {
        val mPair = 1.8 * EngineConstants.M_SUN
        val d = 1.2 * EngineConstants.AU
        val vStar = 0.5 * sqrt(EngineConstants.G * mPair / d)
        val rPlanet = 2.6 * EngineConstants.AU
        val vPlanet = sqrt(EngineConstants.G * mPair / rPlanet)
        val bodies = listOf(
            PresetBody(
                type = BodyType.SUN,
                massKg = 0.9 * EngineConstants.M_SUN,
                x = -0.6 * EngineConstants.AU,
                y = 0.0,
                vx = 0.0,
                vy = vStar
            ),
            PresetBody(
                type = BodyType.SUN,
                massKg = 0.9 * EngineConstants.M_SUN,
                x = 0.6 * EngineConstants.AU,
                y = 0.0,
                vx = 0.0,
                vy = -vStar
            ),
            PresetBody(
                type = BodyType.PLANET,
                massKg = EngineConstants.M_EARTH,
                x = rPlanet,
                y = 0.0,
                vx = 0.0,
                vy = vPlanet
            )
        )
        return barycentric("binary_stars", bodies)
    }

    /**
     * Three SUN-type bodies, equal mass 0.5 M_SUN, dp = 10, length scale ℓ = 0.9 AU.
     * Unit ICs (Chenciner–Montgomery, G=m=1):
     * r₁ = (0.97000436, −0.24308753), r₂ = −r₁, r₃ = (0,0);
     * v₃ = (−0.93240737, −0.86473146), v₁ = v₂ = −v₃/2.
     * Scale: r = ℓ·r_unit, v = v_unit·√(G·μ/ℓ) with μ = 0.5 M_SUN.
     * Period T = 6.3259·√(ℓ³/(Gμ)) ≈ 3.83×10⁷ s
     */
    fun figureEight(): PresetDef {
        val mu = 0.5 * EngineConstants.M_SUN
        val l = 0.9 * EngineConstants.AU
        val vScale = sqrt(EngineConstants.G * mu / l)

        val r1x = 0.97000436
        val r1y = -0.24308753
        val v3x = -0.93240737
        val v3y = -0.86473146

        val bodies = listOf(
            PresetBody(
                type = BodyType.SUN,
                massKg = mu,
                x = l * r1x,
                y = l * r1y,
                vx = vScale * (-v3x / 2.0),
                vy = vScale * (-v3y / 2.0),
                dp = 10f
            ),
            PresetBody(
                type = BodyType.SUN,
                massKg = mu,
                x = -l * r1x,
                y = -l * r1y,
                vx = vScale * (-v3x / 2.0),
                vy = vScale * (-v3y / 2.0),
                dp = 10f
            ),
            PresetBody(
                type = BodyType.SUN,
                massKg = mu,
                x = 0.0,
                y = 0.0,
                vx = vScale * v3x,
                vy = vScale * v3y,
                dp = 10f
            )
        )
        return barycentric("figure_eight", bodies)
    }

    /**
     * SUN at (0,0); PLANET A (1.0 M_E) at (−0.25 AU, 0) v (4 km/s, +0.3 km/s); PLANET B (0.6 M_E) at (+0.25 AU, 0) v (−4 km/s, −0.3 km/s).
     */
    fun collisionCourse(): PresetDef {
        val bodies = listOf(
            PresetBody(
                type = BodyType.SUN,
                massKg = EngineConstants.M_SUN,
                x = 0.0,
                y = 0.0,
                vx = 0.0,
                vy = 0.0
            ),
            PresetBody(
                type = BodyType.PLANET,
                massKg = EngineConstants.M_EARTH,
                x = -0.25 * EngineConstants.AU,
                y = 0.0,
                vx = 4.0e3,
                vy = 0.3e3
            ),
            PresetBody(
                type = BodyType.PLANET,
                massKg = 0.6 * EngineConstants.M_EARTH,
                x = 0.25 * EngineConstants.AU,
                y = 0.0,
                vx = -4.0e3,
                vy = -0.3e3
            )
        )
        return barycentric("collision_course", bodies)
    }

    /**
     * SUN + 6 massless TEST_MARBLES at (r, angle, speed factor of local circular √(G·M_SUN/r), direction):
     * (0.6 AU, 0°, 0.6×, CCW) · (0.8 AU, 60°, 0.9×, CW) · (1.0 AU, 120°, 1.0×, CCW) · (1.15 AU, 180°, 1.25×, CW) · (1.3 AU, 240°, 1.5×, CCW) · (1.4 AU, 300°, 1.05×, CW)
     */
    fun marbleShower(): PresetDef {
        val marbles = listOf(
            Triple(0.6, 0.0, Pair(0.6, true)),      // 0.6 AU, 0°, 0.6x, CCW
            Triple(0.8, 60.0, Pair(0.9, false)),   // 0.8 AU, 60°, 0.9x, CW
            Triple(1.0, 120.0, Pair(1.0, true)),   // 1.0 AU, 120°, 1.0x, CCW
            Triple(1.15, 180.0, Pair(1.25, false)), // 1.15 AU, 180°, 1.25x, CW
            Triple(1.3, 240.0, Pair(1.5, true)),   // 1.3 AU, 240°, 1.5x, CCW
            Triple(1.4, 300.0, Pair(1.05, false))  // 1.4 AU, 300°, 1.05x, CW
        )

        val list = ArrayList<PresetBody>()
        list.add(
            PresetBody(
                type = BodyType.SUN,
                massKg = EngineConstants.M_SUN,
                x = 0.0,
                y = 0.0,
                vx = 0.0,
                vy = 0.0
            )
        )

        for ((rAu, deg, speedDir) in marbles) {
            val (factor, isCcw) = speedDir
            val r = rAu * EngineConstants.AU
            val rad = Math.toRadians(deg)
            val x = r * cos(rad)
            val y = r * sin(rad)
            val vCirc = sqrt(EngineConstants.G * EngineConstants.M_SUN / r)
            val v = factor * vCirc
            val vx = if (isCcw) -v * sin(rad) else v * sin(rad)
            val vy = if (isCcw) v * cos(rad) else -v * cos(rad)

            list.add(
                PresetBody(
                    type = BodyType.TEST_MARBLE,
                    massKg = 0.0,
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy
                )
            )
        }

        return barycentric("marble_shower", list)
    }

    /**
     * Stress preset: exactly 20 bodies.
     * SUN (center) + 7 PLANETs + 6 MARBLES + 3 ASTEROIDS + 1 BLACK_HOLE + 1 WORMHOLE PAIR (2 mouths)
     */
    fun stress20(): PresetDef {
        val list = mutableListOf<PresetBody>()

        // 1. SUN at center
        list.add(
            PresetBody(
                type = BodyType.SUN,
                massKg = EngineConstants.M_SUN,
                x = 0.0,
                y = 0.0,
                vx = 0.0,
                vy = 0.0
            )
        )

        // 2. 7 PLANETs at 0.6–1.4 AU circular CCW
        for (i in 0 until 7) {
            val frac = i / 6.0
            val r = (0.6 + frac * 0.8) * EngineConstants.AU
            val angle = i * (2.0 * Math.PI / 7.0)
            val x = r * cos(angle)
            val y = r * sin(angle)
            val vCirc = sqrt(EngineConstants.G * EngineConstants.M_SUN / r)
            val vx = -vCirc * sin(angle)
            val vy = vCirc * cos(angle)
            list.add(
                PresetBody(
                    type = BodyType.PLANET,
                    massKg = EngineConstants.M_EARTH,
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy
                )
            )
        }

        // 3. 6 TEST_MARBLES massless at 0.5–1.5 AU various elliptical
        for (j in 0 until 6) {
            val frac = j / 5.0
            val r = (0.5 + frac * 1.0) * EngineConstants.AU
            val angle = j * (2.0 * Math.PI / 6.0) + 0.4
            val x = r * cos(angle)
            val y = r * sin(angle)
            val vCirc = sqrt(EngineConstants.G * EngineConstants.M_SUN / r)
            val factor = if (j % 2 == 0) 1.15 else 0.85
            val vx = -factor * vCirc * sin(angle)
            val vy = factor * vCirc * cos(angle)
            list.add(
                PresetBody(
                    type = BodyType.TEST_MARBLE,
                    massKg = 0.0,
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy
                )
            )
        }

        // 4. 3 ASTEROIDs eccentric
        for (k in 0 until 3) {
            val r = (1.1 + k * 0.3) * EngineConstants.AU
            val angle = k * (2.0 * Math.PI / 3.0) + 0.8
            val x = r * cos(angle)
            val y = r * sin(angle)
            val vCirc = sqrt(EngineConstants.G * EngineConstants.M_SUN / r)
            val vx = -0.80 * vCirc * sin(angle)
            val vy = 0.80 * vCirc * cos(angle)
            list.add(
                PresetBody(
                    type = BodyType.ASTEROID,
                    massKg = 1.0e19,
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy
                )
            )
        }

        // 5. 1 BLACK_HOLE (5 M_SUN) at 2.0 AU wide orbit
        val rBh = 2.0 * EngineConstants.AU
        val vBh = sqrt(EngineConstants.G * EngineConstants.M_SUN / rBh)
        list.add(
            PresetBody(
                type = BodyType.BLACK_HOLE,
                massKg = 5.0 * EngineConstants.M_SUN,
                x = rBh,
                y = 0.0,
                vx = 0.0,
                vy = vBh
            )
        )

        // 6. 1 WORMHOLE pair (2 mouths)
        list.add(
            PresetBody(
                type = BodyType.WORMHOLE_MOUTH,
                massKg = 0.0,
                x = -1.8 * EngineConstants.AU,
                y = 0.0,
                vx = 0.0,
                vy = 0.0,
                isWormholePartnerWithNext = true
            )
        )
        list.add(
            PresetBody(
                type = BodyType.WORMHOLE_MOUTH,
                massKg = 0.0,
                x = 1.8 * EngineConstants.AU,
                y = 0.0,
                vx = 0.0,
                vy = 0.0
            )
        )

        return barycentric("stress_20", list)
    }

    val all: List<PresetDef> by lazy {
        listOf(
            sunEarth(),
            sunEarthMoon(),
            binaryStars(),
            figureEight(),
            collisionCourse(),
            marbleShower(),
            stress20()
        )
    }

    fun byKey(key: String): PresetDef = when (key) {
        "sun_earth" -> sunEarth()
        "sun_earth_moon" -> sunEarthMoon()
        "binary_stars" -> binaryStars()
        "figure_eight" -> figureEight()
        "collision_course" -> collisionCourse()
        "marble_shower" -> marbleShower()
        "stress_20" -> stress20()
        else -> sunEarth()
    }
}

fun PresetDef.toEngine(): NBodyEngine {
    val engine = NBodyEngine()
    var lastAddedId = 0L
    for (b in bodies) {
        val r = when (b.type) {
            BodyType.SUN -> EngineConstants.R_SUN
            BodyType.PLANET -> EngineConstants.R_EARTH
            BodyType.MOON -> EngineConstants.R_MOON
            BodyType.ASTEROID -> 1e5
            BodyType.TEST_MARBLE -> 1e4
            BodyType.BLACK_HOLE -> 1.5e4
            BodyType.WORMHOLE_MOUTH -> 1.5e4
        }
        val id = engine.addBody(b.type, b.massKg, r, b.x, b.y, b.vx, b.vy)
        if (b.isWormholePartnerWithNext) {
            lastAddedId = id
        } else if (lastAddedId > 0L && b.type == BodyType.WORMHOLE_MOUTH) {
            engine.linkPair(lastAddedId, id)
            lastAddedId = 0L
        }
    }
    engine.computeAccelerations()
    return engine
}

