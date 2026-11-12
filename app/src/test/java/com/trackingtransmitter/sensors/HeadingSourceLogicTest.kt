package com.trackingtransmitter.sensors

import android.hardware.SensorManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingSourceLogicTest {
    @Test fun flat_turnsUprightAbove50() {
        assertFalse(isUpright(pitchDeg = 48.0, wasUpright = false))
        assertTrue(isUpright(pitchDeg = 51.0, wasUpright = false))
        assertTrue(isUpright(pitchDeg = -51.0, wasUpright = false))
    }
    @Test fun upright_turnsFlatBelow40() {
        assertTrue(isUpright(pitchDeg = 42.0, wasUpright = true))
        assertFalse(isUpright(pitchDeg = 39.0, wasUpright = true))
    }

    @Test fun unreliableOrLow_needsCalibration() {
        assertTrue(isCompassUncalibrated(SensorManager.SENSOR_STATUS_UNRELIABLE))
        assertTrue(isCompassUncalibrated(SensorManager.SENSOR_STATUS_ACCURACY_LOW))
    }
    @Test fun mediumOrHigh_isFine() {
        assertFalse(isCompassUncalibrated(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM))
        assertFalse(isCompassUncalibrated(SensorManager.SENSOR_STATUS_ACCURACY_HIGH))
    }
}
