package com.zig.gravity.physics

/**
 * Structure-of-arrays simulation state. Capacity is fixed at MAX_BODIES = 20.
 *
 * Conventions:
 *  - Slots [0, count) hold active bodies; slots >= count are dead and must
 *    never be read.
 *  - Units: SI, Double. x/y in meters (scene coordinates, origin at viewport
 *    center), vx/vy in m/s, ax/ay in m/s², mass in kg (0.0 = massless test
 *    particle / wormhole mouth), radius in meters (collision radius = visual
 *    radius at scene scale).
 *  - ids are strictly increasing, unique per session, and NEVER reused —
 *    merge-survivor tie-breaks and event correlation depend on this.
 *  - simTime is part of the state (it is rolled back together with the arrays
 *    by the numerical-integrity layer in a later phase).
 */
class SimArrays(val capacity: Int = EngineConstants.MAX_BODIES) {

    val x  = DoubleArray(capacity)
    val y  = DoubleArray(capacity)
    val vx = DoubleArray(capacity)
    val vy = DoubleArray(capacity)
    val ax = DoubleArray(capacity)
    val ay = DoubleArray(capacity)
    val mass   = DoubleArray(capacity)
    val radius = DoubleArray(capacity)

    val ids       = LongArray(capacity)
    val types     = ByteArray(capacity)
    val active    = BooleanArray(capacity)   // slots < count are true
    val kinematic = BooleanArray(capacity)   // true while a body is dragged (used from Phase 4)

    val partnerIds = LongArray(capacity)     // 0L = unpaired, partner mouth id
    val lastExitMouth = LongArray(capacity)  // 0L = none, id of mouth exited from
    val teleportCooldownUntil = DoubleArray(capacity) // simTime until next traversal permitted

    var count: Int = 0
        private set
    var simTime: Double = 0.0

    private var nextId: Long = 1L

    /**
     * Adds a body. Returns its new unique id, or -1 if the table is full
     * (count == capacity) — this is the SINGLE enforcement point of the
     * 20-body cap. The UI layer will surface a friendly message later;
     * the engine never throws for a full table.
     *
     * @throws IllegalArgumentException if massKg < 0, radiusMeters <= 0,
     *         or any double argument is NaN or Infinite (fail fast at the
     *         state boundary; physics-origin corruption is handled by the
     *         integrity layer in Phase 2).
     */
    fun addBody(
        type: BodyType,
        massKg: Double,
        radiusMeters: Double,
        x: Double,
        y: Double,
        vx: Double = 0.0,
        vy: Double = 0.0,
        partnerId: Long = 0L,
    ): Long {
        require(massKg >= 0.0 && massKg.isFinite()) { "massKg must be finite and >= 0" }
        require(radiusMeters.isFinite() && radiusMeters > 0.0) { "radiusMeters must be finite and > 0" }
        require(x.isFinite() && y.isFinite() && vx.isFinite() && vy.isFinite()) { "positions/velocities must be finite" }
        if (count == capacity) return -1L
        val slot = count
        this.x[slot] = x; this.y[slot] = y
        this.vx[slot] = vx; this.vy[slot] = vy
        this.ax[slot] = 0.0; this.ay[slot] = 0.0
        mass[slot] = massKg; radius[slot] = radiusMeters
        ids[slot] = nextId; types[slot] = type.ordinal.toByte()
        active[slot] = true; kinematic[slot] = (type == BodyType.WORMHOLE_MOUTH)
        partnerIds[slot] = partnerId
        lastExitMouth[slot] = 0L
        teleportCooldownUntil[slot] = 0.0
        count += 1
        return nextId++
    }

    /** Slot index of [id], or -1 if absent. Linear scan is fine at n <= 20. */
    fun slotOf(id: Long): Int {
        for (i in 0 until count) if (ids[i] == id) return i
        return -1
    }

    /** Links two bodies mutually as wormhole partners. */
    fun linkPair(id1: Long, id2: Long) {
        val s1 = slotOf(id1)
        val s2 = slotOf(id2)
        if (s1 >= 0) partnerIds[s1] = id2
        if (s2 >= 0) partnerIds[s2] = id1
    }

    /**
     * Removes the body with [id] by SWAPPING the last slot into its place
     * (order is not preserved; this is deliberate, deterministic, O(1)).
     * Returns true if a body was removed.
     */
    fun removeBody(id: Long): Boolean {
        val slot = slotOf(id)
        if (slot < 0) return false
        val partnerId = partnerIds[slot]
        if (partnerId != 0L) {
            val pSlot = slotOf(partnerId)
            if (pSlot >= 0) {
                partnerIds[pSlot] = 0L
            }
        }
        val last = count - 1
        if (slot != last) {
            x[slot] = x[last]; y[slot] = y[last]
            vx[slot] = vx[last]; vy[slot] = vy[last]
            ax[slot] = ax[last]; ay[slot] = ay[last]
            mass[slot] = mass[last]; radius[slot] = radius[last]
            ids[slot] = ids[last]; types[slot] = types[last]
            active[slot] = active[last]; kinematic[slot] = kinematic[last]
            partnerIds[slot] = partnerIds[last]
            lastExitMouth[slot] = lastExitMouth[last]
            teleportCooldownUntil[slot] = teleportCooldownUntil[last]
        }
        partnerIds[last] = 0L
        lastExitMouth[last] = 0L
        teleportCooldownUntil[last] = 0.0
        count = last
        return true
    }

    /** Advances simulation time. Called only by NBodyEngine. */
    fun advanceTime(dt: Double) {
        simTime += dt
    }

    internal fun restoreState(newCount: Int, newSimTime: Double) {
        count = newCount
        simTime = newSimTime
    }

    /** Clears all bodies and resets simTime to 0. Ids are still never reused. */
    fun clear() {
        count = 0
        simTime = 0.0
        partnerIds.fill(0L)
        lastExitMouth.fill(0L)
        teleportCooldownUntil.fill(0.0)
    }
}
