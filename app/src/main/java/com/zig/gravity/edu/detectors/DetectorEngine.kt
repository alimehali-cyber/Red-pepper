package com.zig.gravity.edu.detectors

import com.zig.gravity.physics.BodyType
import com.zig.gravity.physics.EngineConstants
import com.zig.gravity.sim.BodyRender
import com.zig.gravity.sim.SimSnapshot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

sealed class EduEvent {
    data class OrbitStabilized(val bodyId: Long, val attractorId: Long) : EduEvent()
    data class BodyEscaped(val bodyId: Long) : EduEvent()
    data class OrbitDecayed(val bodyId: Long, val attractorId: Long) : EduEvent()
    data class TwoBodyDance(val idA: Long, val idB: Long) : EduEvent()
}

class DetectorEngine {
    private class BodyHistory {
        var lastAttractorId: Long = 0L
        var accumulatedSweep: Double = 0.0
        var lastAngle: Double = Double.NaN
        var orbitLatched: Boolean = false

        var escapeLatched: Boolean = false
        var escapeArmDistance: Double = 0.0
        var maxDistanceSeen: Double = 0.0
        var lastDistance: Double = 0.0

        var decayLatched: Boolean = false
        var lastRdot: Double = Double.NaN
        val recentApoapsisRp = DoubleArray(4)
        var apoapsisCount: Int = 0

        fun reset() {
            lastAttractorId = 0L
            accumulatedSweep = 0.0
            lastAngle = Double.NaN
            orbitLatched = false
            escapeLatched = false
            escapeArmDistance = 0.0
            maxDistanceSeen = 0.0
            lastDistance = 0.0
            decayLatched = false
            lastRdot = Double.NaN
            apoapsisCount = 0
        }
    }

    private class PairHistory {
        var accumulatedSweep: Double = 0.0
        var lastAngle: Double = Double.NaN
        var danceLatched: Boolean = false
    }

    private val bodyHistories = HashMap<Long, BodyHistory>()
    private val pairHistories = HashMap<Long, PairHistory>()

    fun reset() {
        bodyHistories.clear()
        pairHistories.clear()
    }

    fun update(snapshot: SimSnapshot): List<EduEvent> {
        return update(snapshot.bodies)
    }

    fun update(bodies: List<BodyRender>): List<EduEvent> {
        if (bodies.size < 2) return emptyList()

        val events = ArrayList<EduEvent>()
        val count = bodies.size

        // Find dominant attractor for each body
        val dominantAttractorIndex = IntArray(count) { -1 }
        for (i in 0 until count) {
            val bi = bodies[i]
            var maxAccel = 0.0
            var bestJ = -1
            for (j in 0 until count) {
                if (i == j) continue
                val bj = bodies[j]
                if (bj.massKg <= 0.0) continue
                val dx = bj.x - bi.x
                val dy = bj.y - bi.y
                val distSq = dx * dx + dy * dy + EngineConstants.EPS_SOFT * EngineConstants.EPS_SOFT
                val accel = EngineConstants.G * bj.massKg / distSq
                if (accel > maxAccel) {
                    maxAccel = accel
                    bestJ = j
                }
            }
            dominantAttractorIndex[i] = bestJ
        }

        // 1. Per-body detectors (OrbitStabilized, BodyEscaped, OrbitDecayed)
        for (i in 0 until count) {
            val bi = bodies[i]
            val jIdx = dominantAttractorIndex[i]
            if (jIdx < 0) continue
            val bj = bodies[jIdx]

            val hist = bodyHistories.getOrPut(bi.id) { BodyHistory() }

            val dx = bi.x - bj.x
            val dy = bi.y - bj.y
            val r = sqrt(dx * dx + dy * dy)
            if (r <= 0.0) continue

            val dvx = bi.vx - bj.vx
            val dvy = bi.vy - bj.vy
            val vRelSq = dvx * dvx + dvy * dvy
            val gM = EngineConstants.G * bj.massKg
            val epsOrb = 0.5 * vRelSq - gM / r

            val rDot = if (r > 0.0) (dx * dvx + dy * dvy) / r else 0.0

            // --- OrbitStabilized ---
            if (epsOrb < 0.0) {
                val currentAngle = atan2(dy, dx)
                if (hist.lastAttractorId == bj.id && !hist.lastAngle.isNaN()) {
                    var dAngle = currentAngle - hist.lastAngle
                    while (dAngle > PI) dAngle -= 2.0 * PI
                    while (dAngle < -PI) dAngle += 2.0 * PI
                    hist.accumulatedSweep += abs(dAngle)

                    if (hist.accumulatedSweep >= 300.0 * PI / 180.0) {
                        if (!hist.orbitLatched) {
                            hist.orbitLatched = true
                            events.add(EduEvent.OrbitStabilized(bi.id, bj.id))
                        }
                    }
                } else {
                    hist.lastAttractorId = bj.id
                    hist.accumulatedSweep = 0.0
                    hist.orbitLatched = false
                }
                hist.lastAngle = currentAngle
            } else {
                // epsOrb >= 0 -> re-arm
                hist.accumulatedSweep = 0.0
                hist.orbitLatched = false
                hist.lastAngle = Double.NaN
            }

            // --- BodyEscaped ---
            if (epsOrb >= 0.0) {
                if (hist.escapeArmDistance <= 0.0) {
                    hist.escapeArmDistance = r
                    hist.maxDistanceSeen = r
                }
                if (r > hist.maxDistanceSeen) {
                    hist.maxDistanceSeen = r
                }
                val isReceding = rDot > 0.0 || (hist.lastDistance > 0.0 && r > hist.lastDistance)
                if (isReceding && r > 1.6 * hist.escapeArmDistance && !hist.escapeLatched) {
                    hist.escapeLatched = true
                    events.add(EduEvent.BodyEscaped(bi.id))
                }
            } else {
                // Bound again -> re-arm
                hist.escapeLatched = false
                hist.escapeArmDistance = 0.0
                hist.maxDistanceSeen = 0.0
            }
            hist.lastDistance = r

            // --- OrbitDecayed ---
            if (epsOrb < 0.0 && gM > 0.0) {
                val h = abs(dx * dvy - dy * dvx)
                val a = -gM / (2.0 * epsOrb)
                val eccTerm = 1.0 + (2.0 * epsOrb * h * h) / (gM * gM)
                val ecc = sqrt(max(0.0, eccTerm))
                val rp = a * (1.0 - ecc)
                val contactDist = bi.radiusMeters + bj.radiusMeters

                // Apoapsis detection: rDot crosses from positive to negative
                if (!hist.lastRdot.isNaN() && hist.lastRdot > 0.0 && rDot <= 0.0) {
                    // Record rp
                    val idx = hist.apoapsisCount % 4
                    hist.recentApoapsisRp[idx] = rp
                    hist.apoapsisCount++

                    if (hist.apoapsisCount >= 3) {
                        val count = hist.apoapsisCount
                        val rp3 = hist.recentApoapsisRp[(count - 1) % 4]
                        val rp2 = hist.recentApoapsisRp[(count - 2) % 4]
                        val rp1 = hist.recentApoapsisRp[(count - 3) % 4]

                        if (rp1 > rp2 && rp2 > rp3 && rp3 < 2.0 * contactDist && !hist.decayLatched) {
                            hist.decayLatched = true
                            events.add(EduEvent.OrbitDecayed(bi.id, bj.id))
                        }
                    }
                }
                hist.lastRdot = rDot
            } else {
                hist.lastRdot = Double.NaN
            }
        }

        // 2. TwoBodyDance Detector
        for (i in 0 until count) {
            val j = dominantAttractorIndex[i]
            if (j < 0 || j <= i) continue
            if (dominantAttractorIndex[j] == i) {
                // Mutual dominant attractors
                val bi = bodies[i]
                val bj = bodies[j]
                if (bi.massKg <= 0.0 || bj.massKg <= 0.0) continue

                val dx = bj.x - bi.x
                val dy = bj.y - bi.y
                val r = sqrt(dx * dx + dy * dy)
                if (r <= 0.0) continue

                val dvx = bj.vx - bi.vx
                val dvy = bj.vy - bi.vy
                val vRelSq = dvx * dvx + dvy * dvy
                val totalMass = bi.massKg + bj.massKg
                val epsOrb = 0.5 * vRelSq - EngineConstants.G * totalMass / r

                val minId = if (bi.id < bj.id) bi.id else bj.id
                val maxId = if (bi.id < bj.id) bj.id else bi.id
                val pairKey = (minId shl 32) xor maxId

                val pairHist = pairHistories.getOrPut(pairKey) { PairHistory() }

                if (epsOrb < 0.0) {
                    val angle = atan2(dy, dx)
                    if (!pairHist.lastAngle.isNaN()) {
                        var dAngle = angle - pairHist.lastAngle
                        while (dAngle > PI) dAngle -= 2.0 * PI
                        while (dAngle < -PI) dAngle += 2.0 * PI
                        pairHist.accumulatedSweep += abs(dAngle)

                        if (pairHist.accumulatedSweep >= 300.0 * PI / 180.0) {
                            if (!pairHist.danceLatched) {
                                pairHist.danceLatched = true
                                events.add(EduEvent.TwoBodyDance(minId, maxId))
                            }
                        }
                    }
                    pairHist.lastAngle = angle
                } else {
                    pairHist.accumulatedSweep = 0.0
                    pairHist.danceLatched = false
                    pairHist.lastAngle = Double.NaN
                }
            }
        }

        return events
    }
}
