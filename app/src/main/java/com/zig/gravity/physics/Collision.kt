package com.zig.gravity.physics

import kotlin.math.sqrt

object Collision {
    /**
     * In-place merge of slot [a] into slot [b] or vice versa per the survivor rule.
     * Returns Pair(survivorSlot, gonerId). Pure array math; no allocation.
     */
    fun merge(state: SimArrays, a: Int, b: Int): Pair<Int, Long> {
        val typeA = BodyType.entries[state.types[a].toInt()]
        val typeB = BodyType.entries[state.types[b].toInt()]
        val isBhA = typeA == BodyType.BLACK_HOLE
        val isBhB = typeB == BodyType.BLACK_HOLE

        val survivorSlot: Int
        val gonerSlot: Int

        if (isBhA && !isBhB) {
            survivorSlot = a
            gonerSlot = b
        } else if (!isBhA && isBhB) {
            survivorSlot = b
            gonerSlot = a
        } else {
            val ma = state.mass[a]
            val mb = state.mass[b]
            if (ma > mb) {
                survivorSlot = a
                gonerSlot = b
            } else if (mb > ma) {
                survivorSlot = b
                gonerSlot = a
            } else {
                if (a < b) {
                    survivorSlot = a
                    gonerSlot = b
                } else {
                    survivorSlot = b
                    gonerSlot = a
                }
            }
        }

        val m1 = state.mass[survivorSlot]
        val m2 = state.mass[gonerSlot]
        val r1 = state.radius[survivorSlot]
        val r2 = state.radius[gonerSlot]
        val x1 = state.x[survivorSlot]
        val y1 = state.y[survivorSlot]
        val x2 = state.x[gonerSlot]
        val y2 = state.y[gonerSlot]
        val vx1 = state.vx[survivorSlot]
        val vy1 = state.vy[survivorSlot]
        val vx2 = state.vx[gonerSlot]
        val vy2 = state.vy[gonerSlot]

        if (m2 == 0.0) {
            // Massless marble vanishes: target mass/velocity/position bitwise unchanged
            state.radius[survivorSlot] = Math.cbrt(r1 * r1 * r1 + r2 * r2 * r2)
        } else if (m1 == 0.0) {
            state.mass[survivorSlot] = 0.0
            state.radius[survivorSlot] = Math.cbrt(r1 * r1 * r1 + r2 * r2 * r2)
        } else {
            val mSum = m1 + m2
            state.mass[survivorSlot] = mSum
            state.x[survivorSlot] = (m1 * x1 + m2 * x2) / mSum
            state.y[survivorSlot] = (m1 * y1 + m2 * y2) / mSum
            state.vx[survivorSlot] = (m1 * vx1 + m2 * vx2) / mSum
            state.vy[survivorSlot] = (m1 * vy1 + m2 * vy2) / mSum
            state.radius[survivorSlot] = Math.cbrt(r1 * r1 * r1 + r2 * r2 * r2)
        }

        val gonerId = state.ids[gonerSlot]
        val lastSlot = state.count - 1
        state.removeBody(gonerId)
        val finalSurvivorSlot = if (survivorSlot == lastSlot) gonerSlot else survivorSlot

        return Pair(finalSurvivorSlot, gonerId)
    }

    /**
     * Elastic-ish impulse bounce (e = MARBLE_RESTITUTION) + positional correction.
     * Both slots valid, both masses > 0. No allocation.
     */
    fun bounce(state: SimArrays, a: Int, b: Int) {
        val x1 = state.x[a]; val y1 = state.y[a]
        val x2 = state.x[b]; val y2 = state.y[b]
        val m1 = state.mass[a]; val m2 = state.mass[b]
        val r1 = state.radius[a]; val r2 = state.radius[b]

        val dx = x2 - x1
        val dy = y2 - y1
        val dist = sqrt(dx * dx + dy * dy)
        val nx: Double
        val ny: Double
        val distSafe: Double
        if (dist <= 0.0) {
            nx = 1.0
            ny = 0.0
            distSafe = 0.0
        } else {
            nx = dx / dist
            ny = dy / dist
            distSafe = dist
        }

        val dvx = state.vx[b] - state.vx[a]
        val dvy = state.vy[b] - state.vy[a]
        val vn = dvx * nx + dvy * ny

        if (vn < 0.0) {
            val e = EngineConstants.MARBLE_RESTITUTION
            val invM1 = 1.0 / m1
            val invM2 = 1.0 / m2
            val j = -(1.0 + e) * vn / (invM1 + invM2)

            state.vx[a] -= (j * invM1) * nx
            state.vy[a] -= (j * invM1) * ny
            state.vx[b] += (j * invM2) * nx
            state.vy[b] += (j * invM2) * ny
        }

        val rSum = r1 + r2
        val penetration = rSum - distSafe
        if (penetration > 0.0) {
            val invM1 = 1.0 / m1
            val invM2 = 1.0 / m2
            val totalInvM = invM1 + invM2
            val correction = 0.8 * penetration
            val delta1 = correction * invM1 / totalInvM
            val delta2 = correction * invM2 / totalInvM

            if (!state.kinematic[a]) {
                state.x[a] -= delta1 * nx
                state.y[a] -= delta1 * ny
            }
            if (!state.kinematic[b]) {
                state.x[b] += delta2 * nx
                state.y[b] += delta2 * ny
            }
        }
    }
}
