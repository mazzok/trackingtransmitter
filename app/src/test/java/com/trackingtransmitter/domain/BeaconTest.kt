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
    @Test fun pitch_neverChanges() {
        for (d in listOf(2.0, 10.0, 100.0, 1000.0)) for (dev in listOf(0.0, 90.0, 180.0))
            assertEquals(BEEP_HZ, beaconSignal(d, dev).frequencyHz, 0.0)
    }
    @Test fun volume_farAndNear() {
        assertEquals(1.0, beaconSignal(2.0, 0.0).volume, 1e-9)
        assertEquals(1.0, beaconSignal(5.0, 180.0).volume, 1e-9)
        assertEquals(Math.pow(10.0, FAR_VOLUME_DB / 20), beaconSignal(1000.0, 0.0).volume, 1e-9)
    }
    @Test fun volume_risesWhenCloser_independentOfDirection() {
        val volumes = listOf(300.0, 200.0, 100.0, 50.0, 20.0, 10.0, 5.0).map { beaconSignal(it, 90.0).volume }
        assertEquals(volumes.sorted(), volumes)
        assertEquals(beaconSignal(50.0, 0.0).volume, beaconSignal(50.0, 180.0).volume, 0.0)
    }
    @Test fun nanDeviation_countsAsFacingAway() = assertEquals(1500, beaconSignal(100.0, Double.NaN).intervalMs)
    @Test fun nanDistance_countsAsFar() = assertEquals(beaconSignal(FAR_DISTANCE_M, 0.0), beaconSignal(Double.NaN, 0.0))
}
