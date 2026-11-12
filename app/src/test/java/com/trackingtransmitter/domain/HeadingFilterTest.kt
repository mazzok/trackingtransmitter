package com.trackingtransmitter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingFilterTest {
    @Test fun firstValue_passesThrough() = assertEquals(42.0, HeadingFilter().update(42.0), 1e-9)
    @Test fun wrapAroundNorth_staysNearNorth() {
        val f = HeadingFilter(); f.update(350.0)
        val out = f.update(10.0)
        assertTrue("got $out", angleDiffDeg(out, 0.0) < 10.0)   // nicht ~180
    }
    @Test fun converges() { val f = HeadingFilter(); f.update(0.0)
        var out = 0.0; repeat(40) { out = f.update(90.0) }; assertEquals(90.0, out, 0.5) }
    @Test fun outputInRange() { val f = HeadingFilter(); f.update(359.0)
        val out = f.update(358.0); assertTrue(out >= 0.0 && out < 360.0) }
    @Test fun nonFinite_isIgnored() { val f = HeadingFilter(); f.update(42.0)
        assertEquals(42.0, f.update(Double.NaN), 1e-9)
        assertEquals(42.0, f.update(Double.POSITIVE_INFINITY), 1e-9)
        assertEquals(42.0, f.update(42.0), 1e-9) }
}
