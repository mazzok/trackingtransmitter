package com.trackingtransmitter.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalDetectorTest {
    @Test fun threeGoodFixesInside_found() { val d = ArrivalDetector()
        assertFalse(d.onFix(4.0, 5f)); assertFalse(d.onFix(3.0, 5f)); assertTrue(d.onFix(4.5, 5f)) }
    @Test fun singleNoisyFix_notFound() { val d = ArrivalDetector()
        assertFalse(d.onFix(4.0, 5f)); assertFalse(d.onFix(12.0, 5f)); assertFalse(d.onFix(4.0, 5f)); assertFalse(d.onFix(4.0, 5f)) }
    @Test fun poorAccuracy_notFound() { val d = ArrivalDetector()
        repeat(5) { assertFalse(d.onFix(2.0, 15f)) } }
    @Test fun missingAccuracy_notFound() { val d = ArrivalDetector()
        repeat(5) { assertFalse(d.onFix(2.0, null)) } }
    @Test fun reset_requiresFreshRun() { val d = ArrivalDetector()
        d.onFix(1.0, 5f); d.onFix(1.0, 5f); d.reset(); assertFalse(d.onFix(1.0, 5f)) }
}
