package com.trackingtransmitter.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

private const val EARTH_RADIUS_M = 6_371_000.0

/** Haversine distance in metres. */
fun distanceM(a: LatLon, b: LatLon): Double {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

/** Initial great-circle bearing in degrees, 0..<360 (0 = north, 90 = east). */
fun bearingDeg(from: LatLon, to: LatLon): Double {
    val lat1 = Math.toRadians(from.lat)
    val lat2 = Math.toRadians(to.lat)
    val dLon = Math.toRadians(to.lon - from.lon)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** Smallest angular distance between two bearings, 0..180. */
fun angleDiffDeg(a: Double, b: Double): Double {
    val d = abs(a - b) % 360.0
    return if (d > 180.0) 360.0 - d else d
}
