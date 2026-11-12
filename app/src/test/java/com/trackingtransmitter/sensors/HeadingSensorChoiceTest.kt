package com.trackingtransmitter.sensors

import android.hardware.Sensor
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingSensorChoiceTest {
    @Test fun prefersRotationVector() = assertEquals(HeadingSensor.ROTATION_VECTOR,
        chooseHeadingSensor(setOf(Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD)))

    @Test fun noGyro_usesGeomagnetic() = assertEquals(HeadingSensor.GEOMAGNETIC_ROTATION_VECTOR,
        chooseHeadingSensor(setOf(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD)))

    @Test fun rawFallback() = assertEquals(HeadingSensor.ACCEL_MAGNETOMETER,
        chooseHeadingSensor(setOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD)))

    @Test fun noMagnetometer_none() = assertEquals(HeadingSensor.NONE, chooseHeadingSensor(setOf(Sensor.TYPE_ACCELEROMETER)))
}
