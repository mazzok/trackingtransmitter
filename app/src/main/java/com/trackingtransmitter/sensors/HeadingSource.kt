package com.trackingtransmitter.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.trackingtransmitter.domain.HeadingFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.abs

enum class HeadingSensor { ROTATION_VECTOR, GEOMAGNETIC_ROTATION_VECTOR, ACCEL_MAGNETOMETER, NONE }

/** Best available heading source, given the `Sensor.TYPE_*` constants the phone offers. */
fun chooseHeadingSensor(available: Set<Int>): HeadingSensor = when {
    Sensor.TYPE_ROTATION_VECTOR in available -> HeadingSensor.ROTATION_VECTOR
    Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR in available -> HeadingSensor.GEOMAGNETIC_ROTATION_VECTOR
    Sensor.TYPE_ACCELEROMETER in available && Sensor.TYPE_MAGNETIC_FIELD in available -> HeadingSensor.ACCEL_MAGNETOMETER
    else -> HeadingSensor.NONE
}

private const val UPRIGHT_ABOVE_DEG = 50.0
private const val FLAT_BELOW_DEG = 40.0

/** Upright/flat with hysteresis, so holding the phone near 45° does not flip the heading axis back and forth. */
fun isUpright(pitchDeg: Double, wasUpright: Boolean): Boolean =
    if (wasUpright) abs(pitchDeg) >= FLAT_BELOW_DEG else abs(pitchDeg) > UPRIGHT_ABOVE_DEG

/** A `SensorManager.SENSOR_STATUS_*` accuracy at which the compass should be recalibrated. */
fun isCompassUncalibrated(accuracy: Int): Boolean =
    accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW

/** Smoothed true heading (0..<360) the back of the phone points to when upright, the top edge when flat. */
class HeadingSource(private val context: Context) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val locations = LocationSource(context)

    val sensor: HeadingSensor = chooseHeadingSensor(
        sensors.getSensorList(Sensor.TYPE_ALL).map { it.type }.toSet(),
    )

    private val _needsCalibration = MutableStateFlow(false)
    /** True while the magnetometer (or rotation vector) reports unreliable or low accuracy. */
    val needsCalibration: StateFlow<Boolean> = _needsCalibration

    fun headings(): Flow<Double> {
        if (sensor == HeadingSensor.NONE) return emptyFlow()
        return callbackFlow {
            val declination = locations.lastKnown()?.let {
                GeomagneticField(it.lat.toFloat(), it.lon.toFloat(), 0f, System.currentTimeMillis()).declination
            } ?: 0f
            val filter = HeadingFilter(if (sensor == HeadingSensor.ROTATION_VECTOR) 0.25 else 0.1)
            val rotation = FloatArray(9)
            val remapped = FloatArray(9)
            val orientation = FloatArray(3)
            val gravity = FloatArray(3)
            val magnetic = FloatArray(3)
            var haveGravity = false
            var haveMagnetic = false
            var upright = false

            fun emitFromRotation() {
                SensorManager.getOrientation(rotation, orientation)
                upright = isUpright(Math.toDegrees(orientation[1].toDouble()), upright)
                val azimuth = if (upright) {
                    SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                    SensorManager.getOrientation(remapped, orientation)
                    orientation[0]
                } else orientation[0]
                val trueDeg = (Math.toDegrees(azimuth.toDouble()) + declination + 360.0) % 360.0
                val smoothed = filter.update(trueDeg)
                if (smoothed.isFinite()) trySend(smoothed)
            }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                            SensorManager.getRotationMatrixFromVector(rotation, event.values)
                            emitFromRotation()
                        }
                        Sensor.TYPE_ACCELEROMETER -> {
                            System.arraycopy(event.values, 0, gravity, 0, 3)
                            haveGravity = true
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            System.arraycopy(event.values, 0, magnetic, 0, 3)
                            haveMagnetic = true
                        }
                    }
                    if (sensor == HeadingSensor.ACCEL_MAGNETOMETER && haveGravity && haveMagnetic &&
                        event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD &&
                        SensorManager.getRotationMatrix(rotation, null, gravity, magnetic)
                    ) emitFromRotation()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    if (sensor?.type == Sensor.TYPE_ACCELEROMETER) return
                    _needsCalibration.value = isCompassUncalibrated(accuracy)
                }
            }

            val types = when (sensor) {
                HeadingSensor.ROTATION_VECTOR -> listOf(Sensor.TYPE_ROTATION_VECTOR)
                HeadingSensor.GEOMAGNETIC_ROTATION_VECTOR -> listOf(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
                else -> listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD)
            }
            types.forEach { type ->
                sensors.getDefaultSensor(type)?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            }
            awaitClose { sensors.unregisterListener(listener) }
        }.conflate()
    }
}
