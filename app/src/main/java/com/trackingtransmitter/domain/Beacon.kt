package com.trackingtransmitter.domain

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

data class BeaconSignal(val intervalMs: Int, val frequencyHz: Double)

/**
 * Beep timing from heading deviation (faster when facing the target), speeding up
 * regardless of direction in the near zone; pitch rises logarithmically with proximity.
 * A NaN deviation counts as facing away, a NaN distance as far away.
 */
fun beaconSignal(distanceM: Double, deviationDeg: Double): BeaconSignal {
    if (deviationDeg.isNaN()) return beaconSignal(distanceM, 180.0)
    if (distanceM.isNaN()) return beaconSignal(FAR_DISTANCE_M, deviationDeg)
    val f = (1 + cos(Math.toRadians(deviationDeg))) / 2
    val dirInterval = MAX_INTERVAL_MS - f * (MAX_INTERVAL_MS - MIN_INTERVAL_MS)
    val interval = if (distanceM < NEAR_RADIUS_M) {
        val t = ((distanceM - FOUND_RADIUS_M) / (NEAR_RADIUS_M - FOUND_RADIUS_M)).coerceIn(0.0, 1.0)
        val nearInterval = NEAR_MIN_INTERVAL_MS + t * (dirInterval - NEAR_MIN_INTERVAL_MS)
        minOf(dirInterval, nearInterval)
    } else dirInterval
    val d = distanceM.coerceIn(FOUND_RADIUS_M, FAR_DISTANCE_M)
    val frequencyHz = NEAR_HZ * (FAR_HZ / NEAR_HZ).pow(ln(d / FOUND_RADIUS_M) / ln(FAR_DISTANCE_M / FOUND_RADIUS_M))
    return BeaconSignal(interval.roundToInt(), frequencyHz)
}
