package com.zig.gravity.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SimArraysTest {

    @Test
    fun bodyCapEnforcedAt20() {
        val state = SimArrays()
        val ids = LongArray(20)

        // Add 20 bodies (any valid values) -> all return distinct positive ids, count == 20
        for (i in 0 until 20) {
            val id = state.addBody(
                type = BodyType.PLANET,
                massKg = 1.0e24 + i,
                radiusMeters = 1.0e6 + i,
                x = i * 1.0e9,
                y = -i * 1.0e9,
                vx = 100.0,
                vy = 200.0
            )
            assertTrue("Id must be positive", id > 0L)
            ids[i] = id
        }
        assertEquals(20, state.count)

        // Verify all 20 ids are distinct
        assertEquals(20, ids.toSet().size)

        // The 21st addBody returns -1 and count stays 20
        val overflowId = state.addBody(
            type = BodyType.ASTEROID,
            massKg = 1.0e15,
            radiusMeters = 1000.0,
            x = 0.0,
            y = 0.0
        )
        assertEquals(-1L, overflowId)
        assertEquals(20, state.count)

        // removeBody one -> count == 19 -> a new addBody succeeds
        val removed = state.removeBody(ids[5])
        assertTrue("removeBody should return true for existing id", removed)
        assertEquals(19, state.count)

        val newId = state.addBody(
            type = BodyType.TEST_MARBLE,
            massKg = 0.0,
            radiusMeters = 500.0,
            x = 1.0e8,
            y = 2.0e8
        )
        assertTrue("New body add should succeed after remove", newId > 0L)
        assertEquals(20, state.count)
    }

    @Test
    fun idsAreUniqueAndNeverReused() {
        val state = SimArrays()
        val idA = state.addBody(BodyType.SUN, 1.989e30, 6.957e8, 0.0, 0.0)
        val idB = state.addBody(BodyType.PLANET, 5.972e24, 6.371e6, 1.496e11, 0.0)
        val idC = state.addBody(BodyType.MOON, 7.348e22, 1.737e6, 1.496e11 + 3.844e8, 0.0)

        assertTrue(idB > idA)
        assertTrue(idC > idB)

        val removedB = state.removeBody(idB)
        assertTrue(removedB)

        val idD = state.addBody(BodyType.ASTEROID, 1e18, 5e4, 2e11, 0.0)
        assertTrue("D's id must be strictly greater than all previous ids", idD > idC)
        assertTrue(idD > idB)
        assertTrue(idD > idA)
    }

    @Test
    fun removeCompactsState() {
        val state = SimArrays()
        val idA = state.addBody(BodyType.SUN, 1.989e30, 6.957e8, 10.0, 20.0, 1.0, 2.0)
        val idB = state.addBody(BodyType.PLANET, 5.972e24, 6.371e6, 30.0, 40.0, 3.0, 4.0)
        val idC = state.addBody(BodyType.MOON, 7.348e22, 1.737e6, 50.0, 60.0, 5.0, 6.0)

        assertEquals(3, state.count)

        val removedA = state.removeBody(idA)
        assertTrue(removedA)

        assertEquals(-1, state.slotOf(idA))
        assertEquals(2, state.count)

        // Slot 0 now holds C's data exactly (x, y, mass, radius, id)
        assertEquals(idC, state.ids[0])
        assertEquals(50.0, state.x[0], 0.0)
        assertEquals(60.0, state.y[0], 0.0)
        assertEquals(5.0, state.vx[0], 0.0)
        assertEquals(6.0, state.vy[0], 0.0)
        assertEquals(7.348e22, state.mass[0], 0.0)
        assertEquals(1.737e6, state.radius[0], 0.0)
        assertEquals(BodyType.MOON.ordinal.toByte(), state.types[0])
        assertTrue(state.active[0])
        assertFalse(state.kinematic[0])

        // Slot 1 holds B's data
        assertEquals(idB, state.ids[1])
        assertEquals(30.0, state.x[1], 0.0)
        assertEquals(40.0, state.y[1], 0.0)
    }

    @Test
    fun clearResetsState() {
        val state = SimArrays()
        val id1 = state.addBody(BodyType.SUN, 1.989e30, 6.957e8, 0.0, 0.0)
        val id2 = state.addBody(BodyType.PLANET, 5.972e24, 6.371e6, 1.0, 2.0)
        assertEquals(2, state.count)

        state.clear()
        assertEquals(0, state.count)
        assertEquals(0.0, state.simTime, 0.0)

        val id3 = state.addBody(BodyType.ASTEROID, 1.0e10, 100.0, 0.0, 0.0)
        assertTrue("Subsequent add must receive a fresh never-reused id", id3 > id2)
        assertEquals(1, state.count)
    }

    @Test
    fun invalidBodyArgumentsRejected() {
        val state = SimArrays()

        // NaN mass
        try {
            state.addBody(BodyType.PLANET, Double.NaN, 1000.0, 0.0, 0.0)
            fail("Expected IllegalArgumentException for NaN mass")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // Negative mass
        try {
            state.addBody(BodyType.PLANET, -1.0, 1000.0, 0.0, 0.0)
            fail("Expected IllegalArgumentException for negative mass")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // Zero radius
        try {
            state.addBody(BodyType.PLANET, 1000.0, 0.0, 0.0, 0.0)
            fail("Expected IllegalArgumentException for zero radius")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // Negative radius
        try {
            state.addBody(BodyType.PLANET, 1000.0, -10.0, 0.0, 0.0)
            fail("Expected IllegalArgumentException for negative radius")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // Infinite x
        try {
            state.addBody(BodyType.PLANET, 1000.0, 1000.0, Double.POSITIVE_INFINITY, 0.0)
            fail("Expected IllegalArgumentException for infinite x")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // Infinite y
        try {
            state.addBody(BodyType.PLANET, 1000.0, 1000.0, 0.0, Double.NEGATIVE_INFINITY)
            fail("Expected IllegalArgumentException for infinite y")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // Infinite vx
        try {
            state.addBody(BodyType.PLANET, 1000.0, 1000.0, 0.0, 0.0, vx = Double.POSITIVE_INFINITY)
            fail("Expected IllegalArgumentException for infinite vx")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // Infinite vy
        try {
            state.addBody(BodyType.PLANET, 1000.0, 1000.0, 0.0, 0.0, vy = Double.NaN)
            fail("Expected IllegalArgumentException for NaN vy")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // Full table returns -1 WITHOUT throwing
        val fullState = SimArrays()
        for (i in 0 until 20) {
            fullState.addBody(BodyType.PLANET, 1.0, 1.0, 0.0, 0.0)
        }
        assertEquals(20, fullState.count)
        val overflowResult = fullState.addBody(BodyType.PLANET, 1.0, 1.0, 0.0, 0.0)
        assertEquals(-1L, overflowResult)
    }
}
