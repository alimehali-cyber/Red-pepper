package com.zig.gravity.edu

import com.zig.gravity.edu.detectors.DetectorEngine
import com.zig.gravity.edu.detectors.EduEvent
import com.zig.gravity.physics.BodyType
import com.zig.gravity.physics.EngineConstants
import com.zig.gravity.sim.BodyRender
import com.zig.gravity.sim.ChallengePhase
import com.zig.gravity.sim.SimSnapshot
import com.zig.gravity.sim.SimulationViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class EducationSystemTest {

    /**
     * Test 33 — orbitDetectorFiresOnClosedSweep
     * Verifies that closed orbital sweep >= 300° fires OrbitStabilized event and latches.
     */
    @Test
    fun orbitDetectorFiresOnClosedSweep() {
        val detector = DetectorEngine()

        val sun = BodyRender(1L, BodyType.SUN, 0.0, 0.0, 0.0, 0.0, EngineConstants.M_SUN, EngineConstants.R_SUN)
        val r = EngineConstants.AU
        val v = 29780.0
        val dt = 3600.0 * 24.0 // 1 day steps

        var angle = 0.0
        var simTime = 0.0
        var orbitDetected = false

        for (step in 0..600) {
            angle += (v * dt) / r
            val x = r * cos(angle)
            val y = r * sin(angle)
            val vx = -v * sin(angle)
            val vy = v * cos(angle)
            val earth = BodyRender(2L, BodyType.PLANET, x, y, vx, vy, EngineConstants.M_EARTH, EngineConstants.R_EARTH)
            simTime += dt

            val snap = SimSnapshot(listOf(sun, earth), simTime, -1L, 1e9)
            val events = detector.update(snap)
            if (events.any { it is EduEvent.OrbitStabilized }) {
                orbitDetected = true
                // Latch check: next step should NOT re-fire OrbitStabilized
                val nextSnap = SimSnapshot(listOf(sun, earth), simTime + dt, -1L, 1e9)
                val nextEvents = detector.update(nextSnap)
                assertFalse("Orbit detector must latch and not re-fire on next step", nextEvents.any { it is EduEvent.OrbitStabilized })
                break
            }
        }
        assertTrue("Orbit stabilization should be detected on closed sweep", orbitDetected)
    }

    /**
     * Test 34 — escapeDetectorFiresOnUnbound
     * Verifies that an unbound body with positive radial velocity beyond threshold fires BodyEscaped and latches.
     */
    @Test
    fun escapeDetectorFiresOnUnbound() {
        val detector = DetectorEngine()
        val sun = BodyRender(1L, BodyType.SUN, 0.0, 0.0, 0.0, 0.0, EngineConstants.M_SUN, EngineConstants.R_SUN)

        // Arm escape distance
        val escapingEarth1 = BodyRender(2L, BodyType.PLANET, 1.0 * EngineConstants.AU, 0.0, 50000.0, 0.0, EngineConstants.M_EARTH, EngineConstants.R_EARTH)
        detector.update(SimSnapshot(listOf(sun, escapingEarth1), 0.0, -1L, 1e9))

        // Move beyond 1.6x arm distance with outward velocity
        val escapingEarth2 = BodyRender(2L, BodyType.PLANET, 2.0 * EngineConstants.AU, 0.0, 50000.0, 0.0, EngineConstants.M_EARTH, EngineConstants.R_EARTH)
        val escapeEvents = detector.update(SimSnapshot(listOf(sun, escapingEarth2), 100.0, -1L, 1e9))
        assertTrue("Body escape should be detected for unbound trajectory", escapeEvents.any { it is EduEvent.BodyEscaped })

        // Latch verification: further outward motion should not re-fire
        val escapingEarth3 = BodyRender(2L, BodyType.PLANET, 2.5 * EngineConstants.AU, 0.0, 50000.0, 0.0, EngineConstants.M_EARTH, EngineConstants.R_EARTH)
        val nextEvents = detector.update(SimSnapshot(listOf(sun, escapingEarth3), 200.0, -1L, 1e9))
        assertFalse("Escape detector must latch and not re-fire", nextEvents.any { it is EduEvent.BodyEscaped })
    }

    /**
     * Test S3 — twoBodyDanceDetectorFires
     * Verifies that mutual dominant attractors orbiting mutual barycenter fires TwoBodyDance and latches.
     */
    @Test
    fun twoBodyDanceDetectorFires() {
        val detector = DetectorEngine()
        val sep = EngineConstants.AU
        val vOrbit = 21000.0
        val dtDance = 3600.0 * 24.0 * 2.0 // 2 day steps
        var danceDetected = false
        var danceAngle = 0.0

        for (step in 0..400) {
            danceAngle += (2.0 * vOrbit * dtDance) / sep
            val x1 = -0.5 * sep * cos(danceAngle)
            val y1 = -0.5 * sep * sin(danceAngle)
            val vx1 = vOrbit * sin(danceAngle)
            val vy1 = -vOrbit * cos(danceAngle)

            val x2 = 0.5 * sep * cos(danceAngle)
            val y2 = 0.5 * sep * sin(danceAngle)
            val vx2 = -vOrbit * sin(danceAngle)
            val vy2 = vOrbit * cos(danceAngle)

            val s1 = BodyRender(1L, BodyType.SUN, x1, y1, vx1, vy1, EngineConstants.M_SUN, EngineConstants.R_SUN)
            val s2 = BodyRender(2L, BodyType.SUN, x2, y2, vx2, vy2, EngineConstants.M_SUN, EngineConstants.R_SUN)
            val snapDance = SimSnapshot(listOf(s1, s2), step * dtDance, -1L, 1e9)
            val danceEvents = detector.update(snapDance)
            if (danceEvents.any { it is EduEvent.TwoBodyDance }) {
                danceDetected = true
                val nextSnap = SimSnapshot(listOf(s1, s2), (step + 1) * dtDance, -1L, 1e9)
                val nextEvents = detector.update(nextSnap)
                assertFalse("TwoBodyDance must latch and not re-fire", nextEvents.any { it is EduEvent.TwoBodyDance })
                break
            }
        }
        assertTrue("Two-body dance should be detected", danceDetected)
    }

    /**
     * Test S4 — orbitDecayedDetectorFires
     * Verifies that shrinking apoapsis with periapsis dropping below collision threshold fires OrbitDecayed.
     * Also validates teaching catalog completeness and challenge lifecycle.
     */
    @Test
    fun orbitDecayedDetectorFires() {
        val detector = DetectorEngine()
        val sun = BodyRender(1L, BodyType.SUN, 0.0, 0.0, 0.0, 0.0, EngineConstants.M_SUN, EngineConstants.R_SUN)
        val contactDist = EngineConstants.R_SUN + EngineConstants.R_EARTH

        // Helper to simulate an apoapsis transition (rDot > 0 -> rDot <= 0) with target rp
        fun simulateApoapsisPass(targetRp: Double, baseTime: Double) {
            val gm = EngineConstants.G * EngineConstants.M_SUN
            val ra = 1.5 * EngineConstants.AU
            // a = (ra + rp) / 2
            val a = (ra + targetRp) / 2.0
            val epsOrb = -gm / (2.0 * a)
            // at distance ra, vSq = 2 * (epsOrb + gm / ra)
            val vSq = 2.0 * (epsOrb + gm / ra)
            val vTan = sqrt(max(0.0, vSq - 1e6))
            val vRad = sqrt(1e6)

            // Step A: moving outward (rDot > 0)
            val p1 = BodyRender(2L, BodyType.PLANET, ra - 1000.0, 0.0, vRad, vTan, EngineConstants.M_EARTH, EngineConstants.R_EARTH)
            detector.update(SimSnapshot(listOf(sun, p1), baseTime, -1L, 1e9))

            // Step B: apex/inward (rDot <= 0)
            val p2 = BodyRender(2L, BodyType.PLANET, ra, 0.0, -100.0, vTan, EngineConstants.M_EARTH, EngineConstants.R_EARTH)
            detector.update(SimSnapshot(listOf(sun, p2), baseTime + 10.0, -1L, 1e9))
        }

        // Pass 1: rp1 = 3.0 * contactDist
        simulateApoapsisPass(3.0 * contactDist, 100.0)
        // Pass 2: rp2 = 2.2 * contactDist (rp1 > rp2)
        simulateApoapsisPass(2.2 * contactDist, 200.0)
        // Pass 3: rp3 = 1.5 * contactDist (rp2 > rp3 and rp3 < 2.0 * contactDist)
        val gm = EngineConstants.G * EngineConstants.M_SUN
        val ra = 1.5 * EngineConstants.AU
        val targetRp3 = 1.5 * contactDist
        val a3 = (ra + targetRp3) / 2.0
        val epsOrb3 = -gm / (2.0 * a3)
        val vSq3 = 2.0 * (epsOrb3 + gm / ra)
        val vTan3 = sqrt(max(0.0, vSq3 - 1e6))
        val vRad3 = sqrt(1e6)

        detector.update(SimSnapshot(listOf(sun, BodyRender(2L, BodyType.PLANET, ra - 1000.0, 0.0, vRad3, vTan3, EngineConstants.M_EARTH, EngineConstants.R_EARTH)), 300.0, -1L, 1e9))
        val decayEvents = detector.update(SimSnapshot(listOf(sun, BodyRender(2L, BodyType.PLANET, ra, 0.0, -100.0, vTan3, EngineConstants.M_EARTH, EngineConstants.R_EARTH)), 310.0, -1L, 1e9))

        assertTrue("OrbitDecayed event should fire when apoapses decay below threshold", decayEvents.any { it is EduEvent.OrbitDecayed })

        // Validate Catalog Completeness
        val expectedCardKeys = listOf("orbit", "escape", "dance", "decay", "merge", "wormhole", "capture")
        for (catalog in listOf(TeachingCatalog.cardsFa, TeachingCatalog.cardsEn)) {
            for (key in expectedCardKeys) {
                val card = catalog[key]
                assertNotNull("Card '$key' must exist in catalog", card)
                assertTrue("Tier 1 text must not be empty for card '$key'", card!!.t1.isNotBlank())
                assertTrue("Tier 2 text must not be empty for card '$key'", card.t2.isNotBlank())
                assertTrue("Tier 3 text must not be empty for card '$key'", card.t3.isNotBlank())
            }
        }

        // Validate Challenges Catalog & VM Lifecycle
        assertEquals(8, Challenges.list.size)
        val vm = SimulationViewModel()
        vm.onViewportChanged(1080, 2.75f)
        assertTrue(vm.ui.value.teachingMode)
        val ch = Challenges.get("double_mass")
        assertNotNull(ch)
        vm.startChallenge(ch!!)
        assertEquals(ChallengePhase.PREDICTION, vm.activeChallengeState.value?.phase)
        vm.submitChallengePrediction(1)
        assertEquals(ChallengePhase.OBSERVATION, vm.activeChallengeState.value?.phase)
        vm.completeChallengeObservation { "Result $it" }
        assertEquals(ChallengePhase.COMPLETED, vm.activeChallengeState.value?.phase)
        assertNotNull(vm.activeCard.value)
        vm.dismissCard()
        vm.dismissChallenge()
    }
}
