package com.zig.gravity.physics

import com.zig.gravity.sim.Presets
import com.zig.gravity.sim.toEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeAccumulatorTest {

    @Test
    fun substepGroupingInvariant() {
        // Test 8: substepGroupingInvariant
        val engineA = Presets.sunEarth().toEngine()
        val engineB = Presets.sunEarth().toEngine()

        // A: call step(DT) 20 times directly
        for (i in 0 until 20) {
            engineA.step(EngineConstants.DT)
        }

        // B: TimeAccumulator with onFrame(20 * DT / base)
        val acc = TimeAccumulator()
        val frameTime = 20.0 * EngineConstants.DT / EngineConstants.BASE_SIM_SECONDS_PER_REAL_SECOND
        acc.onFrame(frameTime)
        val stepsRun = acc.pump { dt -> engineB.step(dt) }

        assertEquals(20, stepsRun)
        assertEquals(engineA.state.simTime, engineB.state.simTime, 0.0)

        val count = engineA.state.count
        assertEquals(count, engineB.state.count)
        for (i in 0 until count) {
            assertEquals(engineA.state.x[i], engineB.state.x[i], 0.0)
            assertEquals(engineA.state.y[i], engineB.state.y[i], 0.0)
            assertEquals(engineA.state.vx[i], engineB.state.vx[i], 0.0)
            assertEquals(engineA.state.vy[i], engineB.state.vy[i], 0.0)
            assertEquals(engineA.state.ax[i], engineB.state.ax[i], 0.0)
            assertEquals(engineA.state.ay[i], engineB.state.ay[i], 0.0)
            assertEquals(engineA.state.mass[i], engineB.state.mass[i], 0.0)
            assertEquals(engineA.state.radius[i], engineB.state.radius[i], 0.0)
            assertEquals(engineA.state.ids[i], engineB.state.ids[i])
            assertEquals(engineA.state.types[i], engineB.state.types[i])
        }
    }

    @Test
    fun speedEquivalence16xVs1x() {
        // Test 9: speedEquivalence16xVs1x
        val engineA = Presets.sunEarth().toEngine()
        val accA = TimeAccumulator().apply { speedMultiplier = 16.0 }

        val engineB = Presets.sunEarth().toEngine()
        val accB = TimeAccumulator().apply { speedMultiplier = 1.0 }

        // A: speed 16, onFrame(0.0045) (= 0.072/16)
        accA.onFrame(0.0045)
        // B: speed 1, onFrame(0.072)
        accB.onFrame(0.072)

        val stepsA = accA.pump { dt -> engineA.step(dt) }
        val stepsB = accB.pump { dt -> engineB.step(dt) }

        assertEquals(20, stepsA)
        assertEquals(20, stepsB)
        assertEquals(engineA.state.simTime, engineB.state.simTime, 0.0)

        for (i in 0 until engineA.state.count) {
            assertEquals(engineA.state.x[i], engineB.state.x[i], 0.0)
            assertEquals(engineA.state.y[i], engineB.state.y[i], 0.0)
            assertEquals(engineA.state.vx[i], engineB.state.vx[i], 0.0)
            assertEquals(engineA.state.vy[i], engineB.state.vy[i], 0.0)
            assertEquals(engineA.state.ax[i], engineB.state.ax[i], 0.0)
            assertEquals(engineA.state.ay[i], engineB.state.ay[i], 0.0)
        }
    }

    @Test
    fun accumulatorNeverExplodes() {
        // Test 13: accumulatorNeverExplodes
        val acc = TimeAccumulator()
        for (cycle in 0 until 3) {
            acc.onFrame(5.0)
            acc.onFrame(5.0)
            acc.onFrame(5.0)

            var stepsCount = 0
            val stepsRun = acc.pump {
                stepsCount++
                1
            }

            assertTrue("Base steps returned <= 96, actual: $stepsRun", stepsRun <= EngineConstants.MAX_SUBSTEPS)
            assertEquals(stepsCount, stepsRun)

            // Immediate second pump runs 0 steps because debt is discarded
            val secondPumpSteps = acc.pump { 1 }
            assertEquals(0, secondPumpSteps)
        }
    }
}
