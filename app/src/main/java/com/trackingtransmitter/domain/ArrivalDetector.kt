package com.trackingtransmitter.domain

/** Reports "found" once enough consecutive accurate fixes lie inside the found radius. */
class ArrivalDetector {
    private var streak = 0

    fun onFix(distanceM: Double, accuracyM: Float?): Boolean {
        val good = distanceM <= FOUND_RADIUS_M && accuracyM != null && accuracyM <= FOUND_MAX_ACCURACY_M
        streak = if (good) streak + 1 else 0
        return streak >= FOUND_CONSECUTIVE_FIXES
    }

    fun reset() {
        streak = 0
    }
}
