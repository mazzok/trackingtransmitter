package com.trackingtransmitter.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Circular exponential smoothing of a heading in degrees (averages sin/cos, so 359°/1° wrap correctly). */
class HeadingFilter(private val alpha: Double = 0.25) {
    private var sinAcc = 0.0
    private var cosAcc = 0.0
    private var initialized = false

    /** A non-finite [rawDeg] is ignored; the result is then the last value (NaN before the first finite one). */
    fun update(rawDeg: Double): Double {
        if (!rawDeg.isFinite()) return if (initialized) current() else Double.NaN
        val rad = Math.toRadians(rawDeg)
        if (!initialized) {
            sinAcc = sin(rad)
            cosAcc = cos(rad)
            initialized = true
        } else {
            sinAcc += alpha * (sin(rad) - sinAcc)
            cosAcc += alpha * (cos(rad) - cosAcc)
        }
        return current()
    }

    private fun current() = (Math.toDegrees(atan2(sinAcc, cosAcc)) + 360.0) % 360.0

    fun reset() {
        initialized = false
    }
}
