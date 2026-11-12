package com.trackingtransmitter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test fun distance_oneHundredthDegreeLatitude() =
        assertEquals(1111.95, distanceM(LatLon(48.0, 11.0), LatLon(48.01, 11.0)), 0.5)
    @Test fun bearing_north() = assertEquals(0.0, bearingDeg(LatLon(48.0, 11.0), LatLon(48.001, 11.0)), 0.01)
    @Test fun bearing_east() = assertEquals(90.0, bearingDeg(LatLon(48.0, 11.0), LatLon(48.0, 11.001)), 0.01)
    @Test fun bearing_west_isPositive() = assertEquals(270.0, bearingDeg(LatLon(48.0, 11.0), LatLon(48.0, 10.999)), 0.01)
    @Test fun angleDiff_acrossNorth() = assertEquals(2.0, angleDiffDeg(359.0, 1.0), 1e-9)
    @Test fun angleDiff_opposite() = assertEquals(180.0, angleDiffDeg(90.0, 270.0), 1e-9)
    @Test fun angleDiff_symmetric() = assertEquals(angleDiffDeg(10.0, 350.0), angleDiffDeg(350.0, 10.0), 1e-9)
}
