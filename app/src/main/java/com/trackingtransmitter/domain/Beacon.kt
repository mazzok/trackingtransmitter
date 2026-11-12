package com.trackingtransmitter.domain

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** [volume] is a linear gain 0..1 applied to the beep. */
data class BeaconSignal(val intervalMs: Int, val frequencyHz: Double, val volume: Double = 1.0)

/**
 * Direction is heard in pitch and pace: facing the target beeps higher and faster (the pace also speeds up
 * regardless of direction in the near zone). Distance is heard in loudness, rising logarithmically with proximity
 * (evenly in dB, so every halving of the distance sounds equally much louder).
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
    // 0 at the found radius, 1 at the far distance
    val farness = ln(d / FOUND_RADIUS_M) / ln(FAR_DISTANCE_M / FOUND_RADIUS_M)
    // geometric between the two, so equal turns sound like equal musical steps
    val frequencyHz = AWAY_HZ * (FACING_HZ / AWAY_HZ).pow(f)
    val volume = 10.0.pow(FAR_VOLUME_DB * farness / 20)
    return BeaconSignal(interval.roundToInt(), frequencyHz, volume)
}
