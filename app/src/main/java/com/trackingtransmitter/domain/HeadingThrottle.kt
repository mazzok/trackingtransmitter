package com.trackingtransmitter.domain

/** Thins a ~50 Hz heading for drawing: passes a value only after [minIntervalMs] and when it moved at least [minDeltaDeg]. */
class HeadingThrottle(private val minDeltaDeg: Double = 2.0, private val minIntervalMs: Long = 100) {
    private var lastDeg: Double? = null
    private var lastAtMs = 0L

    fun accept(deg: Double, nowMs: Long): Boolean {
        val last = lastDeg
        if (last != null && (nowMs - lastAtMs < minIntervalMs || angleDiffDeg(last, deg) < minDeltaDeg)) return false
        lastDeg = deg
        lastAtMs = nowMs
        return true
    }
}
