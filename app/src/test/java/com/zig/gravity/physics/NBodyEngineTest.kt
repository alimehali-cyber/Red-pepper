package com.zig.gravity.physics

import com.zig.gravity.sim.Presets
import com.zig.gravity.sim.toEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class NBodyEngineTest {

    @Test
    fun twoBodyCircularOrbitClosesAfterOnePeriod() {
        // Test 1: twoBodyCircularOrbitClosesAfterOnePeriod
        val engine = Presets.sunEarth().toEngine()
        val mTot = EngineConstants.M_SUN + EngineConstants.M_EARTH
        val T = 2.0 * Math.PI * sqrt((EngineConstants.AU * EngineConstants.AU * EngineConstants.AU) / (EngineConstants.G * mTot))
        val steps = Math.round(T / EngineConstants.DT).toInt()

        val x0 = engine.state.x[1]
        val y0 = engine.state.y[1]

        for (i in 0 until steps) {
            engine.step(EngineConstants.DT)
        }

        val dx = engine.state.x[1] - x0
        val dy = engine.state.y[1] - y0
        val dist = sqrt(dx * dx + dy * dy)
        assertTrue("Distance from initial Earth position < 0.01 AU, actual: ${dist / EngineConstants.AU} AU", dist < 0.01 * EngineConstants.AU)
    }

    @Test
    fun totalEnergyDriftBounded() {
        // Test 2: totalEnergyDriftBounded
        val engine = Presets.sunEarth().toEngine()
        val mTot = EngineConstants.M_SUN + EngineConstants.M_EARTH
        val T = 2.0 * Math.PI * sqrt((EngineConstants.AU * EngineConstants.AU * EngineConstants.AU) / (EngineConstants.G * mTot))
        val steps = Math.round(10.0 * T / EngineConstants.DT).toInt()

        val e0 = PhysicsTestUtils.totalEnergy(engine.state)

        for (i in 0 until steps) {
            engine.step(EngineConstants.DT)
        }

        val eFinal = PhysicsTestUtils.totalEnergy(engine.state)
        val relativeDrift = abs(eFinal - e0) / abs(e0)
        assertTrue("Energy drift bounded: $relativeDrift < 1e-3", relativeDrift < 1e-3)
    }

    @Test
    fun totalMomentumConservedInFreeFlight() {
        // Test 3: totalMomentumConservedInFreeFlight
        val engine = Presets.sunEarthMoon().toEngine()
        val boostX = 1000.0
        for (i in 0 until engine.state.count) {
            engine.state.vx[i] += boostX
        }
        engine.computeAccelerations()

        val (p0x, p0y) = PhysicsTestUtils.totalMomentum(engine.state)
        val p0Mag = sqrt(p0x * p0x + p0y * p0y)
        assertTrue("P0 must be nonzero", p0Mag > 0.0)

        for (i in 0 until 100_000) {
            engine.step(EngineConstants.DT)
        }

        val (pFx, pFy) = PhysicsTestUtils.totalMomentum(engine.state)
        val dp = sqrt((pFx - p0x) * (pFx - p0x) + (pFy - p0y) * (pFy - p0y))
        val relativeError = dp / p0Mag
        assertTrue("Momentum conserved: $relativeError < 1e-9", relativeError < 1e-9)
    }

    @Test
    fun earthPerturbsSun() {
        // Test 4: earthPerturbsSun
        val engine = Presets.sunEarth().toEngine()
        val mTot = EngineConstants.M_SUN + EngineConstants.M_EARTH
        val T = 2.0 * Math.PI * sqrt((EngineConstants.AU * EngineConstants.AU * EngineConstants.AU) / (EngineConstants.G * mTot))
        val steps = Math.round(T / 2.0 / EngineConstants.DT).toInt()

        for (i in 0 until steps) {
            engine.step(EngineConstants.DT)
        }

        val sunX = engine.state.x[0]
        val sunY = engine.state.y[0]
        val sunDisplacement = sqrt(sunX * sunX + sunY * sunY)
        assertTrue("Sun displacement > 1e5 m, actual: $sunDisplacement m", sunDisplacement > 1e5)
    }

    @Test
    fun moonAffectsBoth() {
        // Test 5: moonAffectsBoth
        val engineA = Presets.sunEarthMoon().toEngine()
        val engineB = Presets.sunEarth().toEngine()

        for (i in 0 until 2000) {
            engineA.step(EngineConstants.DT)
            engineB.step(EngineConstants.DT)
        }

        val earthAx = engineA.state.x[1]
        val earthAy = engineA.state.y[1]
        val earthBx = engineB.state.x[1]
        val earthBy = engineB.state.y[1]

        val diff = sqrt((earthAx - earthBx) * (earthAx - earthBx) + (earthAy - earthBy) * (earthAy - earthBy))
        assertTrue("Earth positions differ by > 1e6 m, actual: $diff m", diff > 1e6)

        val moonAx = engineA.state.ax[2]
        val moonAy = engineA.state.ay[2]
        val moonAcc = sqrt(moonAx * moonAx + moonAy * moonAy)
        assertTrue("Moon acceleration is nonzero, actual: $moonAcc m/s²", moonAcc > 0.0)
    }

    @Test
    fun eccentricOrbitPeriodMatchesKepler() {
        // Test 6: eccentricOrbitPeriodMatchesKepler
        val a = EngineConstants.AU
        val e = 0.5
        val rApo = a * (1.0 + e) // 1.5 AU
        val mTot = EngineConstants.M_SUN + EngineConstants.M_EARTH
        val vApo = sqrt(EngineConstants.G * mTot * (2.0 / rApo - 1.0 / a))
        val vSunApo = -(EngineConstants.M_EARTH / EngineConstants.M_SUN) * vApo
        val T = 2.0 * Math.PI * sqrt((a * a * a) / (EngineConstants.G * mTot))
        val steps = Math.round(T / EngineConstants.DT).toInt()

        val engine = NBodyEngine()
        engine.addBody(BodyType.SUN, EngineConstants.M_SUN, EngineConstants.R_SUN, 0.0, 0.0, 0.0, vSunApo)
        engine.addBody(BodyType.PLANET, EngineConstants.M_EARTH, EngineConstants.R_EARTH, rApo, 0.0, 0.0, vApo)

        var prevVr = 0.0
        var measuredPeriod = 0.0
        var foundCrossing = false

        for (k in 1..steps + 500) {
            val tPrev = engine.state.simTime
            engine.step(EngineConstants.DT)
            val tCurr = engine.state.simTime

            val rx = engine.state.x[1] - engine.state.x[0]
            val ry = engine.state.y[1] - engine.state.y[0]
            val rMag = sqrt(rx * rx + ry * ry)
            val vx = engine.state.vx[1] - engine.state.vx[0]
            val vy = engine.state.vy[1] - engine.state.vy[0]
            val currVr = (rx * vx + ry * vy) / rMag

            if (!foundCrossing && tCurr > T * 0.5) {
                // Crossing from outward (prevVr > 0) to inward (currVr <= 0)
                if (prevVr > 0.0 && currVr <= 0.0) {
                    val fraction = prevVr / (prevVr - currVr)
                    measuredPeriod = tPrev + fraction * EngineConstants.DT
                    foundCrossing = true
                }
            }
            prevVr = currVr

            if (k == steps) {
                // (i) Assert after round(T/DT) steps Earth is back within 0.015 AU of its start
                val distFromStart = sqrt((engine.state.x[1] - rApo) * (engine.state.x[1] - rApo) + engine.state.y[1] * engine.state.y[1])
                assertTrue("Earth within 0.015 AU of start, actual: ${distFromStart / EngineConstants.AU} AU", distFromStart < 0.015 * EngineConstants.AU)
            }
        }

        // (ii) Measured period within 1% of T
        assertTrue("Found apoapsis crossing", foundCrossing)
        val periodError = abs(measuredPeriod - T) / T
        assertTrue("Measured period within 1% of Kepler T ($periodError)", periodError < 0.01)
    }

    @Test
    fun hyperbolicFlybyEnergyConserved() {
        // Test 7: hyperbolicFlybyEnergyConserved
        val engine = Presets.sunEarth().toEngine()
        val startX = -2.0 * EngineConstants.AU
        val startY = 0.3 * EngineConstants.AU
        val r0 = sqrt(startX * startX + startY * startY)

        // Inbound velocity with asymptotic speed 20 km/s (or > escape velocity) so specific energy > 0
        val vInf = 20.0e3
        val vx0 = sqrt(vInf * vInf + 2.0 * EngineConstants.G * EngineConstants.M_SUN / r0)

        val marbleId = engine.addBody(
            type = BodyType.TEST_MARBLE,
            massKg = 0.0,
            radiusMeters = 1000.0,
            x = startX,
            y = startY,
            vx = vx0,
            vy = 0.0
        )
        val marbleSlot = engine.state.slotOf(marbleId)

        // Sun-centric relative coordinates specific energy
        fun computeSpecificEnergy(): Double {
            val mx = engine.state.x[marbleSlot] - engine.state.x[0]
            val my = engine.state.y[marbleSlot] - engine.state.y[0]
            val r = sqrt(mx * mx + my * my)
            val mvx = engine.state.vx[marbleSlot] - engine.state.vx[0]
            val mvy = engine.state.vy[marbleSlot] - engine.state.vy[0]
            val v2 = mvx * mvx + mvy * mvy
            return v2 / 2.0 - (EngineConstants.G * EngineConstants.M_SUN) / r
        }

        val e0 = computeSpecificEnergy()
        assertTrue("Initial specific energy > 0", e0 > 0.0)

        var stepCount = 0
        var reachedFlyby = false
        var exited = false

        while (stepCount < 40_000 && !exited) {
            engine.step(EngineConstants.DT)
            stepCount++

            val mx = engine.state.x[marbleSlot] - engine.state.x[0]
            val my = engine.state.y[marbleSlot] - engine.state.y[0]
            val r = sqrt(mx * mx + my * my)

            if (r < 1.0 * EngineConstants.AU) {
                reachedFlyby = true
            }
            if (reachedFlyby && r > 1.9 * EngineConstants.AU) {
                exited = true
            }
        }

        assertTrue("Flyby occurred and exited past 1.9 AU", exited)

        val eFinal = computeSpecificEnergy()
        assertTrue("Final specific energy > 0", eFinal > 0.0)

        val relError = abs(eFinal - e0) / abs(e0)
        // With Earth in the system, 3-body perturbation is within 1e-4, or 1e-6 without Earth
        assertTrue("Specific energy conserved, actual: $relError", relError < 1e-4)
    }

    @Test
    fun deterministicReplay() {
        // Test 12: deterministicReplay
        fun runScript(): Pair<SimArrays, Long> {
            val engine = NBodyEngine()
            engine.addBody(BodyType.SUN, EngineConstants.M_SUN, EngineConstants.R_SUN, 0.0, 0.0)
            engine.addBody(BodyType.PLANET, EngineConstants.M_EARTH, EngineConstants.R_EARTH, EngineConstants.AU, 0.0, 0.0, EngineConstants.EARTH_ORBITAL_SPEED)
            val marbleId = engine.addBody(BodyType.TEST_MARBLE, 0.0, 500.0, 0.5 * EngineConstants.AU, 0.2 * EngineConstants.AU, 1000.0, -1000.0)

            for (i in 0 until 5000) {
                engine.step(EngineConstants.DT)
            }
            engine.removeBody(marbleId)
            for (i in 0 until 5000) {
                engine.step(EngineConstants.DT)
            }
            return Pair(engine.state, PhysicsTestUtils.stateHash(engine.state))
        }

        val (state1, hash1) = runScript()
        val (state2, hash2) = runScript()

        assertEquals(hash1, hash2)
        assertEquals(state1.count, state2.count)
        assertEquals(state1.simTime, state2.simTime, 0.0)
        for (i in 0 until state1.count) {
            assertEquals(state1.x[i], state2.x[i], 0.0)
            assertEquals(state1.y[i], state2.y[i], 0.0)
            assertEquals(state1.vx[i], state2.vx[i], 0.0)
            assertEquals(state1.vy[i], state2.vy[i], 0.0)
            assertEquals(state1.ax[i], state2.ax[i], 0.0)
            assertEquals(state1.ay[i], state2.ay[i], 0.0)
            assertEquals(state1.mass[i], state2.mass[i], 0.0)
            assertEquals(state1.radius[i], state2.radius[i], 0.0)
            assertEquals(state1.ids[i], state2.ids[i])
            assertEquals(state1.types[i], state2.types[i])
            assertEquals(state1.active[i], state2.active[i])
            assertEquals(state1.kinematic[i], state2.kinematic[i])
        }
    }

    @Test
    fun nanGuardRollsBack() {
        // Test 27: nanGuardRollsBack
        val engine = Presets.sunEarth().toEngine()
        for (i in 0 until 100) {
            engine.step(EngineConstants.DT)
        }

        // Capture snapshot copy in test
        val countBefore = engine.state.count
        val timeBefore = engine.state.simTime
        val xBefore = engine.state.x.clone()
        val yBefore = engine.state.y.clone()
        val vxBefore = engine.state.vx.clone()
        val vyBefore = engine.state.vy.clone()
        val axBefore = engine.state.ax.clone()
        val ayBefore = engine.state.ay.clone()

        // Corrupt state
        engine.state.vx[1] = Double.POSITIVE_INFINITY

        // Step once
        engine.step(EngineConstants.DT)

        // (i) Assert a NumericalFailure event is drained
        val events = engine.drainEvents()
        assertTrue("Must contain NumericalFailure", events.any { it is SimEvent.NumericalFailure })

        // (ii) ALL arrays and simTime equal pre-corruption snapshot bitwise
        assertEquals(timeBefore, engine.state.simTime, 0.0)
        assertEquals(countBefore, engine.state.count)
        for (i in 0 until countBefore) {
            assertEquals(xBefore[i], engine.state.x[i], 0.0)
            assertEquals(yBefore[i], engine.state.y[i], 0.0)
            assertEquals(vxBefore[i], engine.state.vx[i], 0.0)
            assertEquals(vyBefore[i], engine.state.vy[i], 0.0)
            assertEquals(axBefore[i], engine.state.ax[i], 0.0)
            assertEquals(ayBefore[i], engine.state.ay[i], 0.0)
        }

        // (iii) Corruption is gone
        assertTrue("Corruption removed", engine.state.vx[1].isFinite())

        // Clear failures and confirm stepping resumes
        engine.clearFailures()
        val innerSteps = engine.step(EngineConstants.DT)
        assertTrue("Stepping resumes", innerSteps > 0)
        assertTrue("State remains finite", engine.state.vx[1].isFinite())
    }

    @Test
    fun softeningPreventsSingularity() {
        // Test 28: softeningPreventsSingularity
        val engine = NBodyEngine()
        engine.addBody(BodyType.SUN, EngineConstants.M_SUN, 1.0e4, 0.0, 0.0)
        engine.addBody(BodyType.SUN, EngineConstants.M_SUN, 1.0e4, 1.0e5, 0.0)

        engine.computeAccelerations()
        for (i in 0 until engine.state.count) {
            assertTrue("ax must be finite", engine.state.ax[i].isFinite())
            assertTrue("ay must be finite", engine.state.ay[i].isFinite())
        }

        for (i in 0 until 10) {
            engine.step(EngineConstants.DT)
        }

        for (i in 0 until engine.state.count) {
            assertTrue("x is finite", engine.state.x[i].isFinite())
            assertTrue("y is finite", engine.state.y[i].isFinite())
            assertTrue("vx is finite", engine.state.vx[i].isFinite())
            assertTrue("vy is finite", engine.state.vy[i].isFinite())
            assertTrue("ax is finite", engine.state.ax[i].isFinite())
            assertTrue("ay is finite", engine.state.ay[i].isFinite())
        }
    }

    @Test
    fun testParticleExertsNoGravity() {
        // Test 29: testParticleExertsNoGravity
        val engineA = Presets.sunEarth().toEngine()
        val engineB = Presets.sunEarth().toEngine()
        engineB.addBody(BodyType.TEST_MARBLE, 0.0, 1000.0, 0.5 * EngineConstants.AU, 0.2 * EngineConstants.AU, 100.0, -100.0)

        engineA.computeAccelerations()
        engineB.computeAccelerations()

        assertEquals(engineA.state.ax[0], engineB.state.ax[0], 0.0)
        assertEquals(engineA.state.ay[0], engineB.state.ay[0], 0.0)
        assertEquals(engineA.state.ax[1], engineB.state.ax[1], 0.0)
        assertEquals(engineA.state.ay[1], engineB.state.ay[1], 0.0)
    }

    @Test
    fun softeningDoesNotDistortKnownOrbits() {
        // Test 35: softeningDoesNotDistortKnownOrbits
        val eps = EngineConstants.EPS_SOFT
        val G = EngineConstants.G

        // (i) Softened circular speed vs unsoftened
        fun softenedSpeed(M: Double, r: Double): Double {
            val denom = sqrt((r * r + eps * eps) * (r * r + eps * eps) * (r * r + eps * eps))
            return sqrt(G * M * r * r / denom)
        }

        // At r = AU, M = M_SUN
        val vsSun = softenedSpeed(EngineConstants.M_SUN, EngineConstants.AU)
        val vExactSun = sqrt(G * EngineConstants.M_SUN / EngineConstants.AU)
        val relErrSun = abs(vsSun - vExactSun) / vExactSun
        assertTrue("Softened speed at 1 AU within 0.1%: $relErrSun", relErrSun < 0.001)

        // At r = MOON_ORBIT_RADIUS, M = M_EARTH
        val vsEarth = softenedSpeed(EngineConstants.M_EARTH, EngineConstants.MOON_ORBIT_RADIUS)
        val vExactEarth = sqrt(G * EngineConstants.M_EARTH / EngineConstants.MOON_ORBIT_RADIUS)
        val relErrEarth = abs(vsEarth - vExactEarth) / vExactEarth
        assertTrue("Softened speed at Moon orbit within 0.1%: $relErrEarth", relErrEarth < 0.001)

        // (ii) Empirical: Earth-mass central body + test marble at r = MOON_ORBIT_RADIUS
        val engine = NBodyEngine()
        engine.addBody(BodyType.PLANET, EngineConstants.M_EARTH, EngineConstants.R_EARTH, 0.0, 0.0, 0.0, 0.0)
        engine.addBody(BodyType.TEST_MARBLE, 0.0, 1000.0, EngineConstants.MOON_ORBIT_RADIUS, 0.0, 0.0, vsEarth)

        val T = 2.0 * Math.PI * EngineConstants.MOON_ORBIT_RADIUS / vsEarth
        val steps = Math.round(T / EngineConstants.DT).toInt()

        for (i in 0 until steps) {
            engine.step(EngineConstants.DT)
            val rx = engine.state.x[1] - engine.state.x[0]
            val ry = engine.state.y[1] - engine.state.y[0]
            val r = sqrt(rx * rx + ry * ry)
            val ratio = r / EngineConstants.MOON_ORBIT_RADIUS
            assertTrue("Radius stays within [0.999, 1.001] at step $i: $ratio", ratio in 0.999..1.001)
        }
    }

    @Test
    fun tightOrbitDoesNotDiverge() {
        // Test 36: tightOrbitDoesNotDiverge
        val engine = NBodyEngine()
        val mass50Sun = 50.0 * EngineConstants.M_SUN
        val r = 28.0 * (3.0 * EngineConstants.AU / 400.0)
        val ringRadius = 14.0 * (3.0 * EngineConstants.AU / 400.0)
        val vOrb = 0.5 * sqrt(EngineConstants.G * (100.0 * EngineConstants.M_SUN) / r)

        engine.addBody(BodyType.BLACK_HOLE, mass50Sun, ringRadius, -r / 2.0, 0.0, 0.0, -vOrb)
        engine.addBody(BodyType.BLACK_HOLE, mass50Sun, ringRadius, r / 2.0, 0.0, 0.0, vOrb)

        val e0 = PhysicsTestUtils.totalEnergy(engine.state)

        for (i in 0 until 10_000) {
            engine.step(EngineConstants.DT)
        }

        val eFinal = PhysicsTestUtils.totalEnergy(engine.state)
        val drift = abs(eFinal - e0) / abs(e0)
        assertTrue("Tight orbit energy bounded: $drift < 0.05", drift < 0.05)
    }

    @Test
    fun fastFlybyDoesNotTunnel() {
        // Test 37: fastFlybyDoesNotTunnel (upgraded from fastFlybyIsSampledNotSkipped)
        val engine = NBodyEngine()
        val mass50Sun = 50.0 * EngineConstants.M_SUN
        val ringRadius = 14.0 * (3.0 * EngineConstants.AU / 400.0)

        val bhId = engine.addBody(BodyType.BLACK_HOLE, mass50Sun, ringRadius, 0.0, 0.0)
        val marbleId = engine.addBody(
            type = BodyType.TEST_MARBLE,
            massKg = 0.0,
            radiusMeters = 1000.0,
            x = -1.0 * EngineConstants.AU,
            y = ringRadius * 0.5,
            vx = 1000.0e3,
            vy = 0.0
        )

        var consecutiveCloseSteps = 0
        var maxConsecutiveCloseSteps = 0
        var merged = false

        for (i in 0 until 20_000) {
            engine.step(EngineConstants.DT)
            val events = engine.drainEvents()
            val mergeEvent = events.filterIsInstance<SimEvent.BodyMerged>().firstOrNull()
            if (mergeEvent != null) {
                merged = true
                assertEquals(bhId, mergeEvent.idKept)
                assertEquals(marbleId, mergeEvent.idGone)
                assertTrue(mergeEvent.blackHole)
                assertEquals(mass50Sun.toBits(), mergeEvent.mergedMassKg.toBits())
                break
            }

            val marbleSlot = engine.state.slotOf(marbleId)
            if (marbleSlot >= 0) {
                val dx = engine.state.x[marbleSlot] - engine.state.x[0]
                val dy = engine.state.y[marbleSlot] - engine.state.y[0]
                val separation = sqrt(dx * dx + dy * dy)

                if (separation < 1.2 * ringRadius) {
                    consecutiveCloseSteps++
                    if (consecutiveCloseSteps > maxConsecutiveCloseSteps) {
                        maxConsecutiveCloseSteps = consecutiveCloseSteps
                    }
                } else {
                    consecutiveCloseSteps = 0
                }
            }
        }

        assertTrue("Marble was absorbed on contact (BodyMerged emitted)", merged)
        assertEquals("Marble was removed, only BH remains", 1, engine.state.count)
        assertEquals(bhId, engine.state.ids[0])
        assertEquals("BH mass bitwise unchanged", mass50Sun.toBits(), engine.state.mass[0].toBits())
        assertEquals(0.0.toBits(), engine.state.vx[0].toBits())
        assertEquals(0.0.toBits(), engine.state.vy[0].toBits())
    }

    @Test
    fun mergeConservesMomentum() {
        // Test 14: mergeConservesMomentum
        val engine = NBodyEngine()
        val m1 = 2.0e24
        val m2 = 3.0e24
        val r1 = 1.0e6
        val r2 = 1.0e6
        val vx1 = 3.0e4
        val vy1 = 1.0e3
        val vx2 = -2.0e4
        val vy2 = 5.0e3

        val id1 = engine.addBody(BodyType.PLANET, m1, r1, 0.0, 0.0, vx1, vy1)
        val id2 = engine.addBody(BodyType.PLANET, m2, r2, 5.0e5, 0.0, vx2, vy2)

        val pxBefore = m1 * vx1 + m2 * vx2
        val pyBefore = m1 * vy1 + m2 * vy2
        val pMagBefore = sqrt(pxBefore * pxBefore + pyBefore * pyBefore)

        engine.resolveCollisions()

        assertEquals(1, engine.state.count)
        val survivorSlot = 0
        val mAfter = engine.state.mass[survivorSlot]
        val vxAfter = engine.state.vx[survivorSlot]
        val vyAfter = engine.state.vy[survivorSlot]

        val pxAfter = mAfter * vxAfter
        val pyAfter = mAfter * vyAfter

        val dPx = pxAfter - pxBefore
        val dPy = pyAfter - pyBefore
        val dP = sqrt(dPx * dPx + dPy * dPy)
        val relError = dP / pMagBefore

        assertTrue("Momentum conserved relative error < 1e-9: $relError", relError < 1e-9)
    }

    @Test
    fun mergeConservesMass() {
        // Test 15: mergeConservesMass
        val engine = NBodyEngine()
        val m1 = 2.0e24
        val m2 = 3.0e24
        val r1 = 1.0e6
        val r2 = 1.0e6

        engine.addBody(BodyType.PLANET, m1, r1, 0.0, 0.0, 3.0e4, 1.0e3)
        engine.addBody(BodyType.PLANET, m2, r2, 5.0e5, 0.0, -2.0e4, 5.0e3)

        engine.resolveCollisions()

        assertEquals(1, engine.state.count)
        assertEquals((m1 + m2).toBits(), engine.state.mass[0].toBits())
    }

    @Test
    fun mergeVelocityFormula() {
        // Test 16: mergeVelocityFormula
        val engine = NBodyEngine()
        val m1 = 2.0e24
        val m2 = 3.0e24
        val r1 = 1.0e6
        val r2 = 1.0e6
        val x1 = 0.0
        val y1 = 0.0
        val x2 = 5.0e5
        val y2 = 2.0e5
        val vx1 = 3.0e4
        val vy1 = 1.0e3
        val vx2 = -2.0e4
        val vy2 = 5.0e3

        engine.addBody(BodyType.PLANET, m1, r1, x1, y1, vx1, vy1)
        engine.addBody(BodyType.PLANET, m2, r2, x2, y2, vx2, vy2)

        val expectedVx = (m1 * vx1 + m2 * vx2) / (m1 + m2)
        val expectedVy = (m1 * vy1 + m2 * vy2) / (m1 + m2)
        val expectedX = (m1 * x1 + m2 * x2) / (m1 + m2)
        val expectedY = (m1 * y1 + m2 * y2) / (m1 + m2)

        engine.resolveCollisions()

        assertEquals(1, engine.state.count)
        assertEquals(expectedVx, engine.state.vx[0], 1e-12)
        assertEquals(expectedVy, engine.state.vy[0], 1e-12)
        assertEquals(expectedX, engine.state.x[0], 1e-6)
        assertEquals(expectedY, engine.state.y[0], 1e-6)
    }

    @Test
    fun mergeRadiusVolumeConserving() {
        // Test 17: mergeRadiusVolumeConserving
        val engine = NBodyEngine()
        val r1 = 1.2e6
        val r2 = 1.8e6

        engine.addBody(BodyType.PLANET, 2.0e24, r1, 0.0, 0.0)
        engine.addBody(BodyType.PLANET, 3.0e24, r2, 1.0e6, 0.0)

        val expectedRadius = Math.cbrt(r1 * r1 * r1 + r2 * r2 * r2)

        engine.resolveCollisions()

        assertEquals(1, engine.state.count)
        val actualRadius = engine.state.radius[0]
        val relError = abs(actualRadius - expectedRadius) / expectedRadius
        assertTrue("Radius volume-conserving within 1e-9: $relError", relError < 1e-9)
    }

    @Test
    fun extremeMassRatioAbsorb() {
        // Test 18: extremeMassRatioAbsorb
        val engine = NBodyEngine()
        val mSun = EngineConstants.M_SUN
        val rSun = EngineConstants.R_SUN
        val mAsteroid = 1.0e18
        val rAsteroid = 1.0e4

        val sunId = engine.addBody(BodyType.SUN, mSun, rSun, 0.0, 0.0, 0.0, 0.0)
        val astId = engine.addBody(BodyType.ASTEROID, mAsteroid, rAsteroid, 0.5 * rSun, 0.0, 4.0e4, 0.0)

        engine.resolveCollisions()

        assertEquals(1, engine.state.count)
        assertEquals(sunId, engine.state.ids[0])
        val dvSun = sqrt(engine.state.vx[0] * engine.state.vx[0] + engine.state.vy[0] * engine.state.vy[0])
        assertTrue("Sun velocity change < 1e-7 m/s: $dvSun", dvSun < 1e-7)

        val events = engine.drainEvents()
        val mergeEvent = events.filterIsInstance<SimEvent.BodyMerged>().firstOrNull()
        assertTrue("BodyMerged event emitted", mergeEvent != null)
        assertEquals(sunId, mergeEvent?.idKept)
        assertEquals(astId, mergeEvent?.idGone)
    }

    @Test
    fun bounceImpulseMath1D() {
        // Test 19: bounceImpulseMath1D
        val engine = NBodyEngine()
        engine.marbleBounceMode = true

        val m1 = 1.0e20
        val m2 = 2.0e20
        val r1 = 1.0e5
        val r2 = 1.0e5
        val x1 = 0.0
        val x2 = 1.0e5 // overlapping: dist = 1e5 < r1 + r2 = 2e5
        val vx1 = 5.0e4
        val vx2 = -3.0e4

        val id1 = engine.addBody(BodyType.TEST_MARBLE, m1, r1, x1, 0.0, vx1, 0.0)
        val id2 = engine.addBody(BodyType.TEST_MARBLE, m2, r2, x2, 0.0, vx2, 0.0)

        val e = EngineConstants.MARBLE_RESTITUTION
        val vn = vx2 - vx1
        val invM1 = 1.0 / m1
        val invM2 = 1.0 / m2
        val j = -(1.0 + e) * vn / (invM1 + invM2)

        val expectedVx1 = vx1 - (j * invM1)
        val expectedVx2 = vx2 + (j * invM2)

        engine.resolveCollisions()

        assertEquals(2, engine.state.count)
        val slot1 = engine.state.slotOf(id1)
        val slot2 = engine.state.slotOf(id2)

        assertEquals(expectedVx1, engine.state.vx[slot1], 1e-12)
        assertEquals(expectedVx2, engine.state.vx[slot2], 1e-12)
    }

    @Test
    fun bounceMomentumConserved2D() {
        // Test 20: bounceMomentumConserved2D
        val engine = NBodyEngine()
        engine.marbleBounceMode = true

        val m1 = 1.5e20
        val m2 = 2.5e20
        val r1 = 1.0e5
        val r2 = 1.0e5
        val x1 = 0.0
        val y1 = 0.0
        val x2 = 1.2e5
        val y2 = 0.8e5
        val vx1 = 2.0e4
        val vy1 = 3.0e4
        val vx2 = -4.0e4
        val vy2 = -1.0e3

        val id1 = engine.addBody(BodyType.TEST_MARBLE, m1, r1, x1, y1, vx1, vy1)
        val id2 = engine.addBody(BodyType.TEST_MARBLE, m2, r2, x2, y2, vx2, vy2)

        val pxBefore = m1 * vx1 + m2 * vx2
        val pyBefore = m1 * vy1 + m2 * vy2
        val pMagBefore = sqrt(pxBefore * pxBefore + pyBefore * pyBefore)

        engine.resolveCollisions()

        assertEquals(2, engine.state.count)
        val slot1 = engine.state.slotOf(id1)
        val slot2 = engine.state.slotOf(id2)

        val pxAfter = m1 * engine.state.vx[slot1] + m2 * engine.state.vx[slot2]
        val pyAfter = m1 * engine.state.vy[slot1] + m2 * engine.state.vy[slot2]

        val dPx = pxAfter - pxBefore
        val dPy = pyAfter - pyBefore
        val dP = sqrt(dPx * dPx + dPy * dPy)
        val relError = dP / pMagBefore

        assertTrue("2D Bounce Momentum conserved < 1e-12: $relError", relError < 1e-12)
    }

    @Test
    fun bhBhMergeDeterministicSurvivor() {
        // Test 40: bhBhMergeDeterministicSurvivor
        // (i) Equal-mass BHs: lower-slot ID survives
        val engine1 = NBodyEngine()
        val r1 = 1.0e5
        val r2 = 1.0e5
        val bh1Id = engine1.addBody(BodyType.BLACK_HOLE, 5.0 * EngineConstants.M_SUN, r1, 0.0, 0.0, 1.0e3, 0.0)
        val bh2Id = engine1.addBody(BodyType.BLACK_HOLE, 5.0 * EngineConstants.M_SUN, r2, 1.0e5, 0.0, -1.0e3, 0.0)

        engine1.resolveCollisions()

        assertEquals(1, engine1.state.count)
        assertEquals(bh1Id, engine1.state.ids[0])
        val events1 = engine1.drainEvents()
        val merge1 = events1.filterIsInstance<SimEvent.BodyMerged>().first()
        assertEquals(bh1Id, merge1.idKept)
        assertEquals(bh2Id, merge1.idGone)
        assertTrue(merge1.blackHole)

        // (ii) 5 M_SUN vs 3 M_SUN: the 5 M_SUN survives, momentum conserved < 1e-9, ring radius volume-conserving
        val engine2 = NBodyEngine()
        val mHeavy = 5.0 * EngineConstants.M_SUN
        val mLight = 3.0 * EngineConstants.M_SUN
        val rHeavy = 2.0e5
        val rLight = 1.5e5
        val vxLight = 2.0e4
        val vyLight = 1.0e4
        val vxHeavy = -1.0e4
        val vyHeavy = 5.0e3

        // Add lighter BH first (slot 0) and heavier BH second (slot 1)
        val lightId = engine2.addBody(BodyType.BLACK_HOLE, mLight, rLight, 0.0, 0.0, vxLight, vyLight)
        val heavyId = engine2.addBody(BodyType.BLACK_HOLE, mHeavy, rHeavy, 1.0e5, 0.0, vxHeavy, vyHeavy)

        val pxBefore = mLight * vxLight + mHeavy * vxHeavy
        val pyBefore = mLight * vyLight + mHeavy * vyHeavy
        val pMagBefore = sqrt(pxBefore * pxBefore + pyBefore * pyBefore)
        val expectedRadius = Math.cbrt(rLight * rLight * rLight + rHeavy * rHeavy * rHeavy)

        engine2.resolveCollisions()

        assertEquals(1, engine2.state.count)
        assertEquals(heavyId, engine2.state.ids[0])

        val pxAfter = engine2.state.mass[0] * engine2.state.vx[0]
        val pyAfter = engine2.state.mass[0] * engine2.state.vy[0]
        val dP = sqrt((pxAfter - pxBefore) * (pxAfter - pxBefore) + (pyAfter - pyBefore) * (pyAfter - pyBefore))
        val relPError = dP / pMagBefore
        assertTrue("Momentum conserved < 1e-9: $relPError", relPError < 1e-9)

        val relRError = abs(engine2.state.radius[0] - expectedRadius) / expectedRadius
        assertTrue("Radius volume-conserving < 1e-9: $relRError", relRError < 1e-9)

        val events2 = engine2.drainEvents()
        val merge2 = events2.filterIsInstance<SimEvent.BodyMerged>().first()
        assertEquals(heavyId, merge2.idKept)
        assertEquals(lightId, merge2.idGone)
        assertTrue(merge2.blackHole)
    }

    @Test
    fun velocityBoundsClamped() {
        // Test 38: velocityBoundsClamped
        // clampVelocity(2e6, 0) -> magnitude == VELOCITY_HARD_CAP within 1e-9, direction preserved
        val (cx, cy) = clampVelocity(2e6, 0.0)
        val cMag = sqrt(cx * cx + cy * cy)
        assertEquals(EngineConstants.VELOCITY_HARD_CAP, cMag, 1e-9)
        assertTrue(cx > 0.0)
        assertEquals(0.0, cy, 1e-9)

        // clampVelocity(3e5, 4e5) (500 km/s) returns components bitwise unchanged
        val (origX, origY) = clampVelocity(3e5, 4e5)
        assertEquals(3e5, origX, 0.0)
        assertEquals(4e5, origY, 0.0)

        // uiSpeedGuidance at 1 AU from a lone Sun ~ 2 * 42.1 km/s within 0.5%
        val singleSunState = SimArrays()
        singleSunState.addBody(BodyType.SUN, EngineConstants.M_SUN, EngineConstants.R_SUN, 0.0, 0.0)
        singleSunState.addBody(BodyType.PLANET, EngineConstants.M_EARTH, EngineConstants.R_EARTH, EngineConstants.AU, 0.0)
        val guidanceSun = uiSpeedGuidance(singleSunState, 1)
        val expectedSun = 2.0 * 42.1e3
        val guidanceRelErr = abs(guidanceSun - expectedSun) / expectedSun
        assertTrue("Guidance at 1 AU from Sun within 0.5%: $guidanceRelErr", guidanceRelErr < 0.005)

        // near a 50*M_SUN body at 1e10 m returns exactly VELOCITY_HARD_CAP
        val heavyState = SimArrays()
        heavyState.addBody(BodyType.BLACK_HOLE, 50.0 * EngineConstants.M_SUN, 1.0e6, 0.0, 0.0)
        heavyState.addBody(BodyType.TEST_MARBLE, 0.0, 1000.0, 1.0e10, 0.0)
        val guidanceHeavy = uiSpeedGuidance(heavyState, 1)
        assertEquals(EngineConstants.VELOCITY_HARD_CAP, guidanceHeavy, 0.0)
    }

    @Test
    fun blackHoleCaptureConservesMomentumAndGrowsMass() {
        // Test 21: blackHoleCaptureConservesMomentumAndGrowsMass
        val engine = NBodyEngine()
        val mBh = 5.0 * EngineConstants.M_SUN
        val vxBh = 2.0e3
        val vyBh = 0.0
        val mPlanet = EngineConstants.M_EARTH
        val vxPlanet = -8.0e4
        val vyPlanet = 3.0e4

        val bhId = engine.addBody(BodyType.BLACK_HOLE, mBh, 1.0e5, 0.0, 0.0, vxBh, vyBh)
        val planetId = engine.addBody(BodyType.PLANET, mPlanet, 1.0e5, 5.0e4, 0.0, vxPlanet, vyPlanet)

        val pxBefore = mBh * vxBh + mPlanet * vxPlanet
        val pyBefore = mBh * vyBh + mPlanet * vyPlanet
        val pMagBefore = sqrt(pxBefore * pxBefore + pyBefore * pyBefore)

        engine.resolveCollisions()

        assertEquals(1, engine.state.count)
        assertEquals(bhId, engine.state.ids[0])
        assertEquals(mBh + mPlanet, engine.state.mass[0], 0.0)

        val pxAfter = engine.state.mass[0] * engine.state.vx[0]
        val pyAfter = engine.state.mass[0] * engine.state.vy[0]
        val dP = sqrt((pxAfter - pxBefore) * (pxAfter - pxBefore) + (pyAfter - pyBefore) * (pyAfter - pyBefore))
        val relPError = dP / pMagBefore
        assertTrue("Momentum conserved < 1e-9: $relPError", relPError < 1e-9)

        val events = engine.drainEvents()
        val merge = events.filterIsInstance<SimEvent.BodyMerged>().first()
        assertEquals(bhId, merge.idKept)
        assertEquals(planetId, merge.idGone)
        assertTrue(merge.blackHole)
    }

    @Test
    fun blackHoleRingRadiusIsCaptureRadius() {
        // Test 22: blackHoleRingRadiusIsCaptureRadius
        val rBh = 1.0e5
        val rMarble = 1.0e3

        // Case A: separation = (r_b + r_m) + 1.0 m -> NO merge
        val engineA = NBodyEngine()
        engineA.addBody(BodyType.BLACK_HOLE, 5.0 * EngineConstants.M_SUN, rBh, 0.0, 0.0, 0.0, 0.0)
        engineA.addBody(BodyType.TEST_MARBLE, 0.0, rMarble, rBh + rMarble + 1.0, 0.0, 0.0, 0.0)
        engineA.resolveCollisions()
        assertEquals(2, engineA.state.count)

        // Case B: fresh identical setup, separation = (r_b + r_m) - 1.0 m -> merge
        val engineB = NBodyEngine()
        val bhId = engineB.addBody(BodyType.BLACK_HOLE, 5.0 * EngineConstants.M_SUN, rBh, 0.0, 0.0, 0.0, 0.0)
        engineB.addBody(BodyType.TEST_MARBLE, 0.0, rMarble, rBh + rMarble - 1.0, 0.0, 0.0, 0.0)
        engineB.resolveCollisions()
        assertEquals(1, engineB.state.count)
        assertEquals(bhId, engineB.state.ids[0])
        assertEquals(5.0 * EngineConstants.M_SUN, engineB.state.mass[0], 0.0)
    }

    @Test
    fun schwarzschildRadiusSelfConsistent() {
        // Test 23: schwarzschildRadiusSelfConsistent
        val rsSunDirect = 2.0 * EngineConstants.G * EngineConstants.M_SUN / (EngineConstants.C_LIGHT * EngineConstants.C_LIGHT)
        val rsSun = schwarzschildRadius(EngineConstants.M_SUN)
        val relDiff = abs(rsSun - rsSunDirect) / rsSunDirect
        assertTrue("schwarzschildRadius(M_SUN) within 1e-9: $relDiff", relDiff < 1e-9)
        assertTrue("rsSun in [2900, 3000] m, actual: $rsSun", rsSun in 2900.0..3000.0)

        val rs5Sun = schwarzschildRadius(5.0 * EngineConstants.M_SUN)
        assertEquals(14770.6, rs5Sun, 0.1)

        val rsEarth = schwarzschildRadius(EngineConstants.M_EARTH)
        assertEquals(0.00887, rsEarth, 0.00002)
    }

    @Test
    fun wormholeTeleportPreservesVelocity() {
        // Test 24: wormholeTeleportPreservesVelocity
        val engine = NBodyEngine()
        val mouthRadius = 1.0e8
        val mouthAId = engine.addBody(BodyType.WORMHOLE_MOUTH, 0.0, mouthRadius, 0.0, 0.0)
        val mouthBId = engine.addBody(BodyType.WORMHOLE_MOUTH, 0.0, mouthRadius, EngineConstants.AU, 0.0)
        engine.linkPair(mouthAId, mouthBId)

        val vx = 10.0e3
        val vy = 5.0e3
        val marbleId = engine.addBody(BodyType.TEST_MARBLE, 0.0, 1000.0, 0.0, 0.0, vx, vy)

        // Step engine
        engine.step(EngineConstants.DT)

        val marbleSlot = engine.state.slotOf(marbleId)
        val mouthASlot = engine.state.slotOf(mouthAId)
        val mouthBSlot = engine.state.slotOf(mouthBId)

        // Velocity bitwise unchanged
        assertEquals(vx.toBits(), engine.state.vx[marbleSlot].toBits())
        assertEquals(vy.toBits(), engine.state.vy[marbleSlot].toBits())

        // Position teleported near mouth B
        val speed = sqrt(vx * vx + vy * vy)
        val ux = vx / speed
        val uy = vy / speed
        val expectedX = EngineConstants.AU + ux * 1.2 * mouthRadius
        val expectedY = 0.0 + uy * 1.2 * mouthRadius
        assertEquals(expectedX, engine.state.x[marbleSlot], 5.0e7)
        assertEquals(expectedY, engine.state.y[marbleSlot], 5.0e7)

        // Mouth positions and velocities unchanged
        assertEquals(0.0.toBits(), engine.state.x[mouthASlot].toBits())
        assertEquals(0.0.toBits(), engine.state.y[mouthASlot].toBits())
        assertEquals(0.0.toBits(), engine.state.vx[mouthASlot].toBits())
        assertEquals(0.0.toBits(), engine.state.vy[mouthASlot].toBits())
        assertEquals(EngineConstants.AU.toBits(), engine.state.x[mouthBSlot].toBits())
        assertEquals(0.0.toBits(), engine.state.y[mouthBSlot].toBits())

        // WormholeTraversal event emitted
        val events = engine.drainEvents()
        val traversal = events.filterIsInstance<SimEvent.WormholeTraversal>().firstOrNull()
        assertNotNull(traversal)
        assertEquals(marbleId, traversal?.bodyId)
        assertEquals(mouthAId, traversal?.fromMouthId)
        assertEquals(mouthBId, traversal?.toMouthId)

        // Mouths exert NO gravity (mass = 0)
        assertEquals(0.0.toBits(), engine.state.mass[mouthASlot].toBits())
        assertEquals(0.0.toBits(), engine.state.mass[mouthBSlot].toBits())
    }

    @Test
    fun wormholeCooldownPreventsOscillation() {
        // Test 25: wormholeCooldownPreventsOscillation
        val engine = NBodyEngine()
        val mouthRadius = 1.0e8
        val mouthAId = engine.addBody(BodyType.WORMHOLE_MOUTH, 0.0, mouthRadius, 0.0, 0.0)
        val mouthBId = engine.addBody(BodyType.WORMHOLE_MOUTH, 0.0, mouthRadius, EngineConstants.AU, 0.0)
        engine.linkPair(mouthAId, mouthBId)

        val vx = 10.0e3
        val vy = 0.0
        val marbleId = engine.addBody(BodyType.TEST_MARBLE, 0.0, 1000.0, 0.0, 0.0, vx, vy)

        // Step 1: marble teleports from A to B
        engine.step(EngineConstants.DT)
        val events1 = engine.drainEvents()
        assertEquals(1, events1.filterIsInstance<SimEvent.WormholeTraversal>().size)

        // Step next 10 steps: marble is moving away from B; cooldown prevents immediate ping-pong
        for (i in 0 until 10) {
            engine.step(EngineConstants.DT)
            val events = engine.drainEvents()
            assertEquals(0, events.filterIsInstance<SimEvent.WormholeTraversal>().size)
        }

        val marbleSlot = engine.state.slotOf(marbleId)
        assertTrue("Marble continued along trajectory away from B", engine.state.x[marbleSlot] > EngineConstants.AU)
    }

    @Test
    fun kinematicBodiesDoNotTeleport() {
        val engine = NBodyEngine()
        val mouthRadius = 1.0e8
        val mouthAId = engine.addBody(BodyType.WORMHOLE_MOUTH, 0.0, mouthRadius, 0.0, 0.0)
        val mouthBId = engine.addBody(BodyType.WORMHOLE_MOUTH, 0.0, mouthRadius, EngineConstants.AU, 0.0)
        engine.linkPair(mouthAId, mouthBId)

        // Add a kinematic body at mouth A
        val starId = engine.addBody(BodyType.SUN, EngineConstants.M_SUN, 1.0e7, 0.0, 0.0, 0.0, 0.0)
        val starSlot = engine.state.slotOf(starId)
        engine.state.kinematic[starSlot] = true

        engine.step(EngineConstants.DT)

        // Kinematic body remains at mouth A, does not teleport
        assertEquals(0.0, engine.state.x[starSlot], 1e3)
        assertEquals(0.0, engine.state.y[starSlot], 1e3)
        val events = engine.drainEvents()
        assertEquals(0, events.filterIsInstance<SimEvent.WormholeTraversal>().size)
    }

    @Test
    fun wormholePairLifecycle() {
        val state = SimArrays()
        val aId = state.addBody(BodyType.WORMHOLE_MOUTH, 0.0, 1.0e6, 0.0, 0.0)
        val bId = state.addBody(BodyType.WORMHOLE_MOUTH, 0.0, 1.0e6, 1.0e8, 0.0)
        state.linkPair(aId, bId)

        val aSlot = state.slotOf(aId)
        val bSlot = state.slotOf(bId)
        assertEquals(bId, state.partnerIds[aSlot])
        assertEquals(aId, state.partnerIds[bSlot])
        assertTrue(state.kinematic[aSlot])
        assertTrue(state.kinematic[bSlot])
        assertEquals(0.0.toBits(), state.mass[aSlot].toBits())
        assertEquals(0.0.toBits(), state.mass[bSlot].toBits())

        // Removing a removes and unlinks
        state.removeBody(aId)
        val bSlotAfter = state.slotOf(bId)
        assertEquals(0L, state.partnerIds[bSlotAfter])
    }
}
