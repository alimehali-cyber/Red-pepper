package com.zig.gravity.physics

import kotlin.math.sqrt

object PhysicsTestUtils {

    fun totalEnergy(state: SimArrays): Double {
        val count = state.count
        var kinetic = 0.0
        for (i in 0 until count) {
            val v2 = state.vx[i] * state.vx[i] + state.vy[i] * state.vy[i]
            kinetic += 0.5 * state.mass[i] * v2
        }

        var potential = 0.0
        val G = EngineConstants.G
        val eps2 = EngineConstants.EPS_SOFT * EngineConstants.EPS_SOFT
        for (i in 0 until count) {
            val xi = state.x[i]; val yi = state.y[i]; val mi = state.mass[i]
            for (j in i + 1 until count) {
                val dx = state.x[j] - xi
                val dy = state.y[j] - yi
                val rSoft = sqrt(dx * dx + dy * dy + eps2)
                potential -= G * mi * state.mass[j] / rSoft
            }
        }
        return kinetic + potential
    }

    fun totalMomentum(state: SimArrays): Pair<Double, Double> {
        var px = 0.0
        var py = 0.0
        for (i in 0 until state.count) {
            px += state.mass[i] * state.vx[i]
            py += state.mass[i] * state.vy[i]
        }
        return Pair(px, py)
    }

    fun stateHash(state: SimArrays): Long {
        var hash = 17L
        hash = 31L * hash + java.lang.Double.doubleToLongBits(state.simTime)
        hash = 31L * hash + state.count
        for (i in 0 until state.count) {
            hash = 31L * hash + java.lang.Double.doubleToLongBits(state.x[i])
            hash = 31L * hash + java.lang.Double.doubleToLongBits(state.y[i])
            hash = 31L * hash + java.lang.Double.doubleToLongBits(state.vx[i])
            hash = 31L * hash + java.lang.Double.doubleToLongBits(state.vy[i])
            hash = 31L * hash + java.lang.Double.doubleToLongBits(state.ax[i])
            hash = 31L * hash + java.lang.Double.doubleToLongBits(state.ay[i])
            hash = 31L * hash + java.lang.Double.doubleToLongBits(state.mass[i])
            hash = 31L * hash + java.lang.Double.doubleToLongBits(state.radius[i])
            hash = 31L * hash + state.ids[i]
            hash = 31L * hash + state.types[i]
            hash = 31L * hash + (if (state.active[i]) 1L else 0L)
            hash = 31L * hash + (if (state.kinematic[i]) 1L else 0L)
            hash = 31L * hash + state.partnerIds[i]
            hash = 31L * hash + state.lastExitMouth[i]
            hash = 31L * hash + java.lang.Double.doubleToLongBits(state.teleportCooldownUntil[i])
        }
        return hash
    }
}
