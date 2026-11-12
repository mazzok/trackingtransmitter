package com.trackingtransmitter.map

import com.trackingtransmitter.domain.LatLon
import com.trackingtransmitter.domain.bearingDeg
import com.trackingtransmitter.domain.distanceM
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapStyleTest {
    private fun layers() = JSONObject(MapStyle.json()).getJSONArray("layers")

    @Test fun noSymbolLayers() {
        val layers = layers()
        for (i in 0 until layers.length()) assertNotEquals("symbol", layers.getJSONObject(i).getString("type"))
    }

    @Test fun noLabelOrPoiSourceLayers() {
        val forbidden = setOf("poi", "place", "transportation_name", "water_name", "aerodrome_label", "mountain_peak", "housenumber")
        val layers = layers()
        for (i in 0 until layers.length()) assertFalse(layers.getJSONObject(i).optString("source-layer") in forbidden)
    }

    @Test fun hasRoadsGreenWaterBuildings() {
        val layers = layers()
        val src = (0 until layers.length()).map { layers.getJSONObject(it).optString("source-layer") }.toSet()
        assertTrue(src.containsAll(setOf("transportation", "landcover", "park", "water", "building")))
    }

    @Test fun usesOpenFreeMap() = assertEquals(
        "https://tiles.openfreemap.org/planet",
        JSONObject(MapStyle.json()).getJSONObject("sources").getJSONObject("omt").getString("url"),
    )

    @Test fun headingConeApexAndArc() {
        val c = LatLon(48.0, 11.0)
        val cone = headingCone(c, 90.0)
        assertEquals(c, cone.first())
        assertEquals(c, cone.last())
        val mid = cone[cone.size / 2]
        assertEquals(30.0, distanceM(c, mid), 0.5)
        assertEquals(90.0, bearingDeg(c, mid), 0.5)
    }
}
