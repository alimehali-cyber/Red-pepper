package com.zig.gravity.sim

/**
 * Per-body trail ring buffers. 240 points (x,y pairs) per body, preallocated.
 * Keyed by body id (ids are never reused, so trails survive slot swaps).
 */
class TrailStore(val capacity: Int = 240) {
    class Ring(val xs: FloatArray, val ys: FloatArray, var head: Int = 0, var size: Int = 0)

    val rings = HashMap<Long, Ring>()

    fun record(id: Long, x: Double, y: Double) {
        var ring = rings[id]
        if (ring == null) {
            ring = Ring(FloatArray(capacity), FloatArray(capacity), 0, 0)
            rings[id] = ring
        }
        ring.xs[ring.head] = x.toFloat()
        ring.ys[ring.head] = y.toFloat()
        ring.head = (ring.head + 1) % capacity
        if (ring.size < capacity) {
            ring.size++
        }
    }

    fun breakAt(id: Long) {
        val ring = rings[id] ?: return
        ring.xs[ring.head] = Float.NaN
        ring.ys[ring.head] = Float.NaN
        ring.head = (ring.head + 1) % capacity
        if (ring.size < capacity) {
            ring.size++
        }
    }

    fun remove(id: Long) {
        rings.remove(id)
    }

    fun clear() {
        rings.clear()
    }

    inline fun forEach(id: Long, action: (i: Int, x: Float, y: Float) -> Unit) {
        val ring = rings[id] ?: return
        val count = ring.size
        if (count == 0) return
        val start = if (count < capacity) 0 else ring.head
        for (i in 0 until count) {
            val idx = (start + i) % capacity
            action(i, ring.xs[idx], ring.ys[idx])
        }
    }

    fun sizeOf(id: Long): Int = rings[id]?.size ?: 0
}
