package com.trackingtransmitter.domain

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** [volume] is a linear gain 0..1 applied to the beep. */
data class BeaconSignal(val intervalMs: Int, val frequencyHz: Double, val volume: Double = 1.0)

/**
 * Direction alone is heard in the pace: facing the target beeps faster, at any distance. Distance is heard in loudness, rising logarithmically with proximity (evenly in dB, so every
 * halving of the distance sounds equally much louder). The pitch stays the same.
 * A NaN deviation counts as facing away, a NaN distance as far away.
 */
fun beaconSignal(distanceM: Double, deviationDeg: Double): BeaconSignal {
    if (deviationDeg.isNaN()) return beaconSignal(distanceM, 180.0)
    if (distanceM.isNaN()) return beaconSignal(FAR_DISTANCE_M, deviationDeg)
    val f = (1 + cos(Math.toRadians(deviationDeg))) / 2
    val interval = MAX_INTERVAL_MS - f * (MAX_INTERVAL_MS - MIN_INTERVAL_MS)
    val d = distanceM.coerceIn(FOUND_RADIUS_M, FAR_DISTANCE_M)
    // 0 at the found radius, 1 at the far distance
    val farness = ln(d / FOUND_RADIUS_M) / ln(FAR_DISTANCE_M / FOUND_RADIUS_M)
    val volume = 10.0.pow(FAR_VOLUME_DB * farness / 20)
    return BeaconSignal(interval.roundToInt(), BEEP_HZ, volume)
}
