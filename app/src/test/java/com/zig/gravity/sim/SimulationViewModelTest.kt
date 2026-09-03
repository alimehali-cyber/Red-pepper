package com.zig.gravity.sim

import com.zig.gravity.physics.BodyType
import com.zig.gravity.physics.EngineConstants
import com.zig.gravity.physics.NBodyEngine
import com.zig.gravity.physics.PhysicsTestUtils
import com.zig.gravity.physics.SimArrays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class SimulationViewModelTest {

    data class StateSnapshot(
        val count: Int,
        val simTime: Double,
        val ids: LongArray,
        val types: ByteArray,
        val active: BooleanArray,
        val kinematic: BooleanArray,
        val mass: DoubleArray,
        val radius: DoubleArray,
        val x: DoubleArray,
        val y: DoubleArray,
        val vx: DoubleArray,
        val vy: DoubleArray,
        val ax: DoubleArray,
        val ay: DoubleArray
    )

    @Test
    fun pauseResumeStateIdentical() {
        val vm = SimulationViewModel()                       // default sunEarth, running = true
        vm.onViewportChanged(1080, 2.75f)
        repeat(3000) { i -> vm.onFrame(1_000_000L * 16_666_667L + i * 16_666_667L) }   // ~50 s at 60 fps
        val before = captureSnapshot(vm.engine.state)         // test-side copy helper
        val beforeSimTime = vm.snapshot.value.simTime
        val beforeHash = PhysicsTestUtils.stateHash(vm.engine.state)

        vm.togglePlayPause()                                  // PAUSE
        repeat(120) { i -> vm.onFrame(1_000_000L * 16_666_667L + (3000 + i) * 16_666_667L) }

        // assert: all arrays + simTime bitwise-identical to `before`; snapshot.simTime unchanged
        assertSnapshotIdentical(before, vm.engine.state)
        assertEquals(beforeHash, PhysicsTestUtils.stateHash(vm.engine.state))
        assertEquals(beforeSimTime, vm.snapshot.value.simTime, 0.0)

        vm.togglePlayPause()                                  // RESUME
        val resumeStart = 1_000_000L * 16_666_667L + 3120 * 16_666_667L
        repeat(10) { i -> vm.onFrame(resumeStart + (i + 1) * 16_666_667L) }

        // assert: simTime advanced AND state differs from `before`  (motion resumed; no time jump:
        // the first resumed frame accrues only its own delta — assert the simTime increase
        // is < 20 * DT * 5                               // <= 5 base steps for 10 short frames
        assertTrue(vm.engine.state.simTime > beforeSimTime)
        assertStatesDiffer(before, vm.engine.state)
        val timeIncrease = vm.engine.state.simTime - beforeSimTime
        assertTrue(
            "Expected time increase < 20 * DT * 5, but was $timeIncrease",
            timeIncrease < 20.0 * EngineConstants.DT * 5.0
        )
    }

    /**
     * Test 11 — resetRestoresPresetExactly
     * VM with viewport set (onViewportChanged(1080, 2.75f)); run 3,000 frames; move a body (beginDrag/dragMoveTo/endDrag)
     * and change a mass; reset(); build a reference engine from the same preset + same viewport sync;
     * assert bitwise equality of ALL arrays + simTime == 0, trails empty, selection cleared.
     */
    @Test
    fun test11_resetRestoresPresetExactly() {
        val vm = SimulationViewModel()
        vm.onViewportChanged(1080, 2.75f)
        repeat(3000) { i -> vm.onFrame(1_000_000L * 16_666_667L + i * 16_666_667L) }

        // Move a body (drag) and change a mass
        val earthId = vm.engine.state.ids[1]
        vm.beginDrag(earthId)
        vm.dragMoveTo(earthId, 2.0 * EngineConstants.AU, 1.0 * EngineConstants.AU)
        vm.endDrag()
        vm.setMass(earthId, 2.0 * EngineConstants.M_EARTH)
        vm.selectBody(earthId)

        // Reset
        vm.reset()

        // Build reference engine
        val refEngine = NBodyEngine()
        val preset = Presets.sunEarth()
        val metersPerDp = EngineConstants.VIEWPORT_WIDTH_AU * EngineConstants.AU / (1080.0 / 2.75)
        for (b in preset.bodies) {
            val r = b.dp * metersPerDp
            refEngine.addBody(b.type, b.massKg, r, b.x, b.y, b.vx, b.vy)
        }
        refEngine.computeAccelerations()

        // Assert bitwise equality of all arrays
        assertEquals(refEngine.state.count, vm.engine.state.count)
        assertEquals(0.0.toBits(), vm.engine.state.simTime.toBits())
        assertEquals(-1L, vm.ui.value.selectedId)

        for (i in 0 until refEngine.state.count) {
            assertEquals(refEngine.state.types[i], vm.engine.state.types[i])
            assertEquals(refEngine.state.active[i], vm.engine.state.active[i])
            assertEquals(refEngine.state.kinematic[i], vm.engine.state.kinematic[i])
            assertEquals(refEngine.state.mass[i].toBits(), vm.engine.state.mass[i].toBits())
            assertEquals(refEngine.state.radius[i].toBits(), vm.engine.state.radius[i].toBits())
            assertEquals(refEngine.state.x[i].toBits(), vm.engine.state.x[i].toBits())
            assertEquals(refEngine.state.y[i].toBits(), vm.engine.state.y[i].toBits())
            assertEquals(refEngine.state.vx[i].toBits(), vm.engine.state.vx[i].toBits())
            assertEquals(refEngine.state.vy[i].toBits(), vm.engine.state.vy[i].toBits())
            assertEquals(refEngine.state.ax[i].toBits(), vm.engine.state.ax[i].toBits())
            assertEquals(refEngine.state.ay[i].toBits(), vm.engine.state.ay[i].toBits())
            assertEquals(0, vm.trails.sizeOf(vm.engine.state.ids[i]))
        }
    }

    /**
     * Test 30 — kinematicDragBypassesIntegration
     * sunEarth() VM; beginDrag(earthId); run 50 frames while calling dragMoveTo to new positions
     * each frame; assert: Earth's position bitwise == last pointer position, kinematic == true,
     * Earth's trail did NOT grow (sizeOf unchanged), while the SUN advanced (simTime increased).
     * Then endDrag() with near-zero pointer velocity: kinematic == false.
     */
    @Test
    fun test30_kinematicDragBypassesIntegration() {
        val vm = SimulationViewModel()
        vm.onViewportChanged(1080, 2.75f)

        // Find earthId (second body in sunEarth preset)
        val earthId = vm.engine.state.ids[1]
        val sunId = vm.engine.state.ids[0]

        // Advance 10 frames first so trail has some initial points
        repeat(10) { i -> vm.onFrame(1_000_000L * 16_666_667L + i * 16_666_667L) }
        val earthTrailSizeBefore = vm.trails.sizeOf(earthId)
        val simTimeBefore = vm.engine.state.simTime

        // Begin kinematic drag
        vm.beginDrag(earthId)
        val earthSlot = vm.engine.state.slotOf(earthId)
        assertTrue(vm.engine.state.kinematic[earthSlot])

        var lastTargetX = 0.0
        var lastTargetY = 0.0

        // Run 50 frames while dragging Earth
        repeat(50) { i ->
            lastTargetX = EngineConstants.AU + (i + 1) * 1000.0
            lastTargetY = (i + 1) * 2000.0
            vm.dragMoveTo(earthId, lastTargetX, lastTargetY)
            vm.onFrame(1_000_000L * 16_666_667L + (10 + i) * 16_666_667L)
        }

        // Assert Earth's position bitwise == last pointer position
        assertEquals(lastTargetX.toBits(), vm.engine.state.x[earthSlot].toBits())
        assertEquals(lastTargetY.toBits(), vm.engine.state.y[earthSlot].toBits())
        assertTrue(vm.engine.state.kinematic[earthSlot])

        // Assert Earth's trail did NOT grow while kinematic
        assertEquals(earthTrailSizeBefore, vm.trails.sizeOf(earthId))

        // Assert the simulation / Sun advanced
        assertTrue(vm.engine.state.simTime > simTimeBefore)

        // End drag with near-zero velocity (holding still)
        vm.dragMoveTo(earthId, lastTargetX, lastTargetY)
        Thread.sleep(100) // Ensure drag history > 80ms yields zero velocity on release
        vm.endDrag()

        assertFalse(vm.engine.state.kinematic[earthSlot])
    }

    /**
     * Test 32 — saveRestoreRoundTripIdentical
     * VM (preset sun_earth_moon, viewport set), run 1,000 frames, setSpeed(4.0), toggleTrails(),
     * toggleMarbleBounce(), setRadius(earthId, custom) (marks userSized); json = exportSave();
     * fresh VM2 (viewport set) importSave(json); assert: body count/types/mass/radius/x/y/vx bitwise equal,
     * simTime equal, speed/trails/bounce equal, running == false; then set both running and run 100
     * identical frames -> bitwise-identical final states (deterministic continuation).
     * Also assert VM2's onViewportChanged (again, same values) does NOT overwrite the user-sized radius.
     */
    @Test
    fun test32_saveRestoreRoundTripIdentical() {
        val vm = SimulationViewModel()
        vm.onViewportChanged(1080, 2.75f)
        vm.loadPreset(Presets.sunEarthMoon())

        repeat(1000) { i -> vm.onFrame(1_000_000L * 16_666_667L + i * 16_666_667L) }

        vm.setSpeed(4.0)
        vm.toggleTrails()
        vm.toggleMarbleBounce()
        val earthId = vm.engine.state.ids[1]
        val customRadius = 5.0e7
        vm.setRadius(earthId, customRadius)

        val json = vm.exportSave()
        assertNotNull(json)

        val vm2 = SimulationViewModel()
        vm2.onViewportChanged(1080, 2.75f)
        val imported = vm2.importSave(json!!)
        assertTrue(imported)

        // Assert count, types, mass, radius, x, y, vx, vy bitwise equal
        assertEquals(vm.engine.state.count, vm2.engine.state.count)
        assertEquals(vm.engine.state.simTime.toBits(), vm2.engine.state.simTime.toBits())
        assertEquals(vm.ui.value.speed, vm2.ui.value.speed, 0.0)
        assertEquals(vm.ui.value.trailsEnabled, vm2.ui.value.trailsEnabled)
        assertEquals(vm.ui.value.marbleBounce, vm2.ui.value.marbleBounce)
        assertFalse(vm2.ui.value.running)

        for (i in 0 until vm.engine.state.count) {
            assertEquals(vm.engine.state.types[i], vm2.engine.state.types[i])
            assertEquals(vm.engine.state.mass[i].toBits(), vm2.engine.state.mass[i].toBits())
            assertEquals(vm.engine.state.radius[i].toBits(), vm2.engine.state.radius[i].toBits())
            assertEquals(vm.engine.state.x[i].toBits(), vm2.engine.state.x[i].toBits())
            assertEquals(vm.engine.state.y[i].toBits(), vm2.engine.state.y[i].toBits())
            assertEquals(vm.engine.state.vx[i].toBits(), vm2.engine.state.vx[i].toBits())
            assertEquals(vm.engine.state.vy[i].toBits(), vm2.engine.state.vy[i].toBits())
            assertEquals(vm.engine.state.ax[i].toBits(), vm2.engine.state.ax[i].toBits())
            assertEquals(vm.engine.state.ay[i].toBits(), vm2.engine.state.ay[i].toBits())
        }

        // Set both running and run 100 identical frames
        if (!vm.ui.value.running) vm.togglePlayPause()
        if (!vm2.ui.value.running) vm2.togglePlayPause()

        val startT = 1_000_000L * 16_666_667L + 1000 * 16_666_667L
        repeat(100) { i ->
            val t = startT + (i + 1) * 16_666_667L
            vm.onFrame(t)
            vm2.onFrame(t)
        }

        assertEquals(vm.engine.state.simTime.toBits(), vm2.engine.state.simTime.toBits())
        for (i in 0 until vm.engine.state.count) {
            assertEquals(vm.engine.state.x[i].toBits(), vm2.engine.state.x[i].toBits())
            assertEquals(vm.engine.state.y[i].toBits(), vm2.engine.state.y[i].toBits())
            assertEquals(vm.engine.state.vx[i].toBits(), vm2.engine.state.vx[i].toBits())
            assertEquals(vm.engine.state.vy[i].toBits(), vm2.engine.state.vy[i].toBits())
            assertEquals(vm.engine.state.ax[i].toBits(), vm2.engine.state.ax[i].toBits())
            assertEquals(vm.engine.state.ay[i].toBits(), vm2.engine.state.ay[i].toBits())
        }

        // Also assert VM2's onViewportChanged does NOT overwrite the user-sized radius
        assertEquals(customRadius.toBits(), vm2.engine.state.radius[1].toBits())
        vm2.onViewportChanged(1080, 2.75f)
        assertEquals(customRadius.toBits(), vm2.engine.state.radius[1].toBits())
    }

    /**
     * figureEightStaysBounded: load figure_eight; run 2 periods (T per §2.1);
     * assert every body stays within 1.0 AU of the origin throughout.
     */
    @Test
    fun figureEightStaysBounded() {
        val preset = Presets.figureEight()
        val engine = NBodyEngine()
        for (b in preset.bodies) {
            engine.addBody(b.type, b.massKg, 1e7, b.x, b.y, b.vx, b.vy)
        }
        engine.computeAccelerations()

        val mu = 0.5 * EngineConstants.M_SUN
        val l = 0.9 * EngineConstants.AU
        val periodT = 6.3259 * sqrt((l * l * l) / (EngineConstants.G * mu))
        val totalTime = 2.0 * periodT
        val steps = (totalTime / EngineConstants.DT).toInt()

        val maxRadiusMeters = 1.0 * EngineConstants.AU
        val maxRadiusSq = maxRadiusMeters * maxRadiusMeters

        for (step in 0 until steps) {
            engine.step(EngineConstants.DT)
            for (i in 0 until engine.state.count) {
                val r2 = engine.state.x[i] * engine.state.x[i] + engine.state.y[i] * engine.state.y[i]
                assertTrue(
                    "Body $i escaped figure-eight boundary at step $step (dist=${sqrt(r2)} m > $maxRadiusMeters m)",
                    r2 <= maxRadiusSq
                )
            }
        }
    }

    /**
     * Test 39 — kinematicReleaseUsesFreshForces
     * Engine with Sun at origin; body placed at (0, AU) — let it accrue acceleration there (force ≈ −y direction);
     * then beginDrag, dragMoveTo to (AU, 0) (force there is −x), hold still, endDrag() with zero velocity.
     * Immediately assert engine.state.ax[slot] equals a freshly computed value at (AU, 0) (recompute on a copy:
     * magnitude ≈ G·M_SUN/AU² softened, direction −x).
     * Then run one step: assert the body's new velocity is dominated by −x (vx < 0 and |vy| < 0.2·|vx|) —
     * proving the kick used the fresh force, not the stale (−y) one from the old position.
     */
    @Test
    fun test39_kinematicReleaseUsesFreshForces() {
        val vm = SimulationViewModel()
        // Reset and create custom setup: Sun at origin, test body at (0, AU)
        while (vm.engine.state.count > 0) {
            vm.engine.removeBody(vm.engine.state.ids[0])
        }

        val sunId = vm.engine.addBody(
            type = BodyType.SUN,
            massKg = EngineConstants.M_SUN,
            radiusMeters = EngineConstants.R_SUN,
            x = 0.0,
            y = 0.0,
            vx = 0.0,
            vy = 0.0
        )
        val bodyId = vm.engine.addBody(
            type = BodyType.TEST_MARBLE,
            massKg = 0.0,
            radiusMeters = 1000.0,
            x = 0.0,
            y = EngineConstants.AU,
            vx = 0.0,
            vy = 0.0
        )

        val bodySlot = vm.engine.state.slotOf(bodyId)
        // Verify initial force at (0, AU) is in -y direction
        assertTrue(vm.engine.state.ay[bodySlot] < 0.0)
        assertEquals(0.0, vm.engine.state.ax[bodySlot], 1e-10)

        // Drag to (AU, 0)
        vm.beginDrag(bodyId)
        vm.dragMoveTo(bodyId, EngineConstants.AU, 0.0)
        Thread.sleep(100) // Ensure zero release velocity
        vm.endDrag()

        // Fresh force check at (AU, 0): force must be in -x direction and ay must be 0
        val axAfterRelease = vm.engine.state.ax[bodySlot]
        val ayAfterRelease = vm.engine.state.ay[bodySlot]
        assertTrue("Expected ax < 0 after moving to (AU, 0), but was $axAfterRelease", axAfterRelease < 0.0)
        assertEquals(0.0, ayAfterRelease, 1e-10)

        // Expected softened gravity magnitude: G * M_SUN / (AU^2 + EPS_SOFT^2)
        val epsSoft2 = EngineConstants.EPS_SOFT * EngineConstants.EPS_SOFT
        val r2Soft = EngineConstants.AU * EngineConstants.AU + epsSoft2
        val expectedAccMag = EngineConstants.G * EngineConstants.M_SUN / r2Soft
        assertEquals(-expectedAccMag, axAfterRelease, expectedAccMag * 1e-4)

        // Step engine once
        vm.engine.step(EngineConstants.DT)

        val newVx = vm.engine.state.vx[bodySlot]
        val newVy = vm.engine.state.vy[bodySlot]

        assertTrue("Expected vx < 0, but was $newVx", newVx < 0.0)
        assertTrue(
            "Expected |vy| < 0.2 * |vx|, but vy=$newVy, vx=$newVx",
            abs(newVy) < 0.2 * abs(newVx)
        )
    }

    @Test
    fun saveRestorePreservesPairLink() {
        val vm = SimulationViewModel()
        vm.onViewportChanged(1080, 2.75f)
        while (vm.engine.state.count > 0) {
            vm.engine.removeBody(vm.engine.state.ids[0])
        }

        vm.addBodyAtCenter(BodyType.WORMHOLE_MOUTH)
        assertEquals(2, vm.engine.state.count)
        val idA = vm.engine.state.ids[0]
        val idB = vm.engine.state.ids[1]

        val json = vm.exportSave()
        assertNotNull(json)

        val vm2 = SimulationViewModel()
        vm2.onViewportChanged(1080, 2.75f)
        val imported = vm2.importSave(json!!)
        assertTrue(imported)

        assertEquals(2, vm2.engine.state.count)
        val idA2 = vm2.engine.state.ids[0]
        val idB2 = vm2.engine.state.ids[1]
        val slotA2 = vm2.engine.state.slotOf(idA2)
        val slotB2 = vm2.engine.state.slotOf(idB2)

        assertEquals(idB2, vm2.engine.state.partnerIds[slotA2])
        assertEquals(idA2, vm2.engine.state.partnerIds[slotB2])

        // Verify traversal works in restored VM
        val mouthRadius = vm2.engine.state.radius[slotA2]
        val marbleId = vm2.engine.addBody(
            type = BodyType.TEST_MARBLE,
            massKg = 0.0,
            radiusMeters = 1000.0,
            x = vm2.engine.state.x[slotA2],
            y = vm2.engine.state.y[slotA2],
            vx = 10.0e3,
            vy = 0.0
        )

        vm2.engine.step(EngineConstants.DT)
        val marbleSlot = vm2.engine.state.slotOf(marbleId)
        val expectedX = vm2.engine.state.x[slotB2] + 1.2 * mouthRadius
        assertEquals(expectedX, vm2.engine.state.x[marbleSlot], 5.0e7)
    }

    @Test
    fun stressTwentyBodiesWithinBudget() {
        val vm = SimulationViewModel()
        vm.onViewportChanged(1080, 2.75f)
        vm.loadPreset(Presets.stress20())
        assertEquals(20, vm.snapshot.value.bodies.size)

        var frameNanos = 1_000_000_000L
        val frameDurationNanos = 16_666_667L

        val startTimeNs = System.nanoTime()
        repeat(1800) {
            frameNanos += frameDurationNanos
            vm.onFrame(frameNanos)
            val currentBodies = vm.snapshot.value.bodies
            assertTrue("Count must be <= 20 at all times", currentBodies.size <= 20)
        }
        val elapsedNs = System.nanoTime() - startTimeNs
        val meanMs = (elapsedNs / 1800.0) / 1_000_000.0

        assertTrue("Mean frame time ($meanMs ms) should be well within 16.0 ms budget", meanMs < 16.0)

        // Check for NaN / Inf in any coordinate
        for (i in 0 until vm.engine.state.count) {
            if (vm.engine.state.active[i]) {
                assertFalse("x must not be NaN", vm.engine.state.x[i].isNaN())
                assertFalse("y must not be NaN", vm.engine.state.y[i].isNaN())
                assertFalse("vx must not be NaN", vm.engine.state.vx[i].isNaN())
                assertFalse("vy must not be NaN", vm.engine.state.vy[i].isNaN())
                assertFalse("x must not be Inf", vm.engine.state.x[i].isInfinite())
                assertFalse("y must not be Inf", vm.engine.state.y[i].isInfinite())
            }
        }
    }

    @Test
    fun saveRestorePreservesThemeAndLanguage() {
        val vm = SimulationViewModel()
        vm.onViewportChanged(1080, 2.75f)
        // Set light theme and english language
        vm.toggleTheme() // dark -> light
        assertEquals(false, vm.ui.value.darkTheme)
        vm.toggleLanguage() // fa -> en
        assertEquals("en", vm.ui.value.language)

        val json = vm.exportSave()!!
        assertTrue("JSON contains version 3", json.contains("\"version\": 3") || json.contains("\"version\":3"))
        assertTrue("JSON contains theme light", json.contains("\"theme\": \"light\"") || json.contains("\"theme\":\"light\""))
        assertTrue("JSON contains language en", json.contains("\"language\": \"en\"") || json.contains("\"language\":\"en\""))

        // Fresh VM import
        val freshVm = SimulationViewModel()
        freshVm.onViewportChanged(1080, 2.75f)
        assertEquals(true, freshVm.ui.value.darkTheme)
        assertEquals("fa", freshVm.ui.value.language)

        freshVm.importSave(json)
        assertEquals(false, freshVm.ui.value.darkTheme)
        assertEquals("en", freshVm.ui.value.language)

        // Test backward compat: v2 save without theme and language
        val legacyV2Json = """
            {
              "version": 2,
              "presetKey": "sun_earth",
              "simTime": 0.0,
              "speed": 1.0,
              "trailsEnabled": true,
              "marbleBounce": false,
              "bodies": [
                {
                  "typeOrdinal": 0,
                  "massKg": 1.989e30,
                  "radiusDp": 40.0,
                  "x": 0.0,
                  "y": 0.0,
                  "vx": 0.0,
                  "vy": 0.0,
                  "userSized": false,
                  "userMass": false
                }
              ]
            }
        """.trimIndent()

        val compatVm = SimulationViewModel()
        compatVm.toggleTheme() // set to light
        compatVm.toggleLanguage() // set to en
        assertEquals(false, compatVm.ui.value.darkTheme)
        assertEquals("en", compatVm.ui.value.language)

        val imported = compatVm.importSave(legacyV2Json)
        assertTrue("Legacy v2 save imported successfully", imported)
        // Defaults should restore to dark and fa
        assertEquals(true, compatVm.ui.value.darkTheme)
        assertEquals("fa", compatVm.ui.value.language)
    }

    private fun captureSnapshot(state: SimArrays): StateSnapshot {
        val count = state.count
        return StateSnapshot(
            count = count,
            simTime = state.simTime,
            ids = state.ids.copyOf(count),
            types = state.types.copyOf(count),
            active = state.active.copyOf(count),
            kinematic = state.kinematic.copyOf(count),
            mass = state.mass.copyOf(count),
            radius = state.radius.copyOf(count),
            x = state.x.copyOf(count),
            y = state.y.copyOf(count),
            vx = state.vx.copyOf(count),
            vy = state.vy.copyOf(count),
            ax = state.ax.copyOf(count),
            ay = state.ay.copyOf(count)
        )
    }

    private fun assertSnapshotIdentical(expected: StateSnapshot, actual: SimArrays) {
        assertEquals(expected.count, actual.count)
        assertEquals(expected.simTime.toBits(), actual.simTime.toBits())
        for (i in 0 until expected.count) {
            assertEquals(expected.ids[i], actual.ids[i])
            assertEquals(expected.types[i], actual.types[i])
            assertEquals(expected.active[i], actual.active[i])
            assertEquals(expected.kinematic[i], actual.kinematic[i])
            assertEquals(expected.mass[i].toBits(), actual.mass[i].toBits())
            assertEquals(expected.radius[i].toBits(), actual.radius[i].toBits())
            assertEquals(expected.x[i].toBits(), actual.x[i].toBits())
            assertEquals(expected.y[i].toBits(), actual.y[i].toBits())
            assertEquals(expected.vx[i].toBits(), actual.vx[i].toBits())
            assertEquals(expected.vy[i].toBits(), actual.vy[i].toBits())
            assertEquals(expected.ax[i].toBits(), actual.ax[i].toBits())
            assertEquals(expected.ay[i].toBits(), actual.ay[i].toBits())
        }
    }

    private fun assertStatesDiffer(before: StateSnapshot, actual: SimArrays) {
        var anyDifferent = (before.simTime != actual.simTime)
        for (i in 0 until actual.count) {
            if (before.x[i] != actual.x[i] || before.y[i] != actual.y[i] ||
                before.vx[i] != actual.vx[i] || before.vy[i] != actual.vy[i]
            ) {
                anyDifferent = true
            }
        }
        assertTrue("Expected state to differ after resuming motion", anyDifferent)
    }
}
