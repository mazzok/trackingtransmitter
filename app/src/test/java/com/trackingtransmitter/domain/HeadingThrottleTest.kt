package com.trackingtransmitter.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingThrottleTest {
    @Test fun firstValue_passes() = assertTrue(HeadingThrottle().accept(10.0, nowMs = 0))
    @Test fun tooSoon_isDropped() { val t = HeadingThrottle(); t.accept(10.0, 0)
        assertFalse(t.accept(30.0, 50)) }
    @Test fun tinyChange_isDropped() { val t = HeadingThrottle(); t.accept(10.0, 0)
        assertFalse(t.accept(11.0, 500)) }
    @Test fun bigChangeAfterInterval_passes() { val t = HeadingThrottle(); t.accept(10.0, 0)
        assertTrue(t.accept(12.0, 100)) }
    @Test fun wrapAround_countsAsSmall() { val t = HeadingThrottle(); t.accept(359.5, 0)
        assertFalse(t.accept(0.5, 500)) }
}
