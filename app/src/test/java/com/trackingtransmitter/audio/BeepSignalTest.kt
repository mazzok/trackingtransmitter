package com.trackingtransmitter.audio

import com.trackingtransmitter.domain.BeaconSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BeepSignalTest {
    @Test fun failingSignal_isSkipped() = assertNull(readSignal { throw IllegalArgumentException("NaN") })
    @Test fun goodSignal_passesThrough() = assertEquals(BeaconSignal(150, 600.0), readSignal { BeaconSignal(150, 600.0) })
}
