package com.trackingtransmitter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeaconTest {
    @Test fun facingTarget_far_isFastest() = assertEquals(150, beaconSignal(100.0, 0.0).intervalMs)
    @Test fun facingAway_far_isSlowest() = assertEquals(1500, beaconSignal(100.0, 180.0).intervalMs)
    @Test fun sideways_isMiddle() = assertEquals(825, beaconSignal(100.0, 90.0).intervalMs)
    @Test fun turningTowards_isMonotonic() {
        val intervals = (0..180 step 10).map { beaconSignal(100.0, it.toDouble()).intervalMs }
        assertEquals(intervals.sorted(), intervals)
    }
    @Test fun atFoundRadius_isVeryFast_evenFacingAway() = assertEquals(80, beaconSignal(5.0, 180.0).intervalMs)
    @Test fun nearZone_speedsUpWithoutDirection() =
        assertTrue(beaconSignal(8.0, 180.0).intervalMs < beaconSignal(14.0, 180.0).intervalMs)
    @Test fun pitch_farAndNear() {
        assertEquals(600.0, beaconSignal(1000.0, 0.0).frequencyHz, 0.01)
        assertEquals(1600.0, beaconSignal(2.0, 0.0).frequencyHz, 0.01)
        assertTrue(beaconSignal(50.0, 0.0).frequencyHz > beaconSignal(100.0, 0.0).frequencyHz)
    }
    @Test fun nanDeviation_countsAsFacingAway() = assertEquals(1500, beaconSignal(100.0, Double.NaN).intervalMs)
    @Test fun nanDistance_countsAsFar() = assertEquals(beaconSignal(FAR_DISTANCE_M, 0.0), beaconSignal(Double.NaN, 0.0))
}
