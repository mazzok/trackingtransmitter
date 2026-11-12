package com.trackingtransmitter.map

import com.trackingtransmitter.domain.LatLon
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Reduced OpenFreeMap style: roads, green, water and buildings only. No labels, POIs, glyphs or sprites. */
object MapStyle {
    const val ATTRIBUTION = "© OpenStreetMap contributors · OpenFreeMap"
    const val SOURCE = "omt"

    private fun arr(vararg v: Any): JSONArray = JSONArray().also { a -> v.forEach { a.put(it) } }

    private fun layer(id: String, type: String, sourceLayer: String, paint: JSONObject, filter: JSONArray? = null) =
        JSONObject().put("id", id).put("type", type).put("source", SOURCE).put("source-layer", sourceLayer)
            .put("paint", paint).also { if (filter != null) it.put("filter", filter) }

    private fun roadClass(vararg classes: String): JSONArray =
        arr("match", arr("get", "class"), JSONArray(classes.toList()), true, false)

    private fun roadLine(id: String, classes: JSONArray, color: String, width: JSONArray, dashed: Boolean = false): JSONObject {
        val paint = JSONObject().put("line-color", color).put("line-width", width)
        if (dashed) paint.put("line-dasharray", arr(2, 2))
        return layer(id, "line", "transportation", paint, classes)
    }

    private fun widthAt(z10: Double, z18: Double): JSONArray =
        arr("interpolate", arr("exponential", 1.5), arr("zoom"), 10, z10, 18, z18)

    fun json(): String {
        val landcoverColor = arr(
            "match", arr("get", "class"),
            "grass", "#D8E8C8",
            "wood", "#C2DDB0",
            "farmland", "#EAF0D5",
            "#E4EDD3",
        )
        val layers = JSONArray()
            .put(JSONObject().put("id", "background").put("type", "background")
                .put("paint", JSONObject().put("background-color", "#F2EFE9")))
            .put(layer("landcover", "fill", "landcover", JSONObject().put("fill-color", landcoverColor),
                arr("match", arr("get", "class"), arr("grass", "wood", "farmland"), true, false)))
            .put(layer("park", "fill", "park", JSONObject().put("fill-color", "#C8E6B0").put("fill-opacity", 0.8)))
            .put(layer("landuse", "fill", "landuse", JSONObject().put("fill-color", "#E8E4DC")))
            .put(layer("water", "fill", "water", JSONObject().put("fill-color", "#AAD3DF")))
            .put(layer("waterway", "line", "waterway", JSONObject().put("line-color", "#AAD3DF").put("line-width", widthAt(0.8, 4.0))))
            .put(layer("building", "fill", "building", JSONObject().put("fill-color", "#D9D0C9")))
            .put(roadLine("road-minor", roadClass("minor", "service"), "#FFFFFF", widthAt(0.8, 8.0)))
            .put(roadLine("road-path", roadClass("path", "track"), "#8B6B4A", widthAt(0.6, 2.0), dashed = true))
            .put(roadLine("road-secondary", roadClass("secondary", "tertiary"), "#F7E27A", widthAt(1.2, 12.0)))
            .put(roadLine("road-major", roadClass("motorway", "trunk", "primary"), "#F5A85A", widthAt(1.8, 16.0)))
        val source = JSONObject().put("type", "vector").put("url", "https://tiles.openfreemap.org/planet")
            .put("attribution", ATTRIBUTION)
        return JSONObject()
            .put("version", 8)
            .put("sources", JSONObject().put(SOURCE, source))
            .put("layers", layers)
            .toString()
    }
}

private const val EARTH_RADIUS_M = 6_371_000.0

/** Point [distanceM] metres from [from] along [bearingDeg]. */
private fun destination(from: LatLon, bearingDeg: Double, distanceM: Double): LatLon {
    val d = distanceM / EARTH_RADIUS_M
    val brg = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(from.lat)
    val lon1 = Math.toRadians(from.lon)
    val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brg))
    val lon2 = lon1 + atan2(sin(brg) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
    return LatLon(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

/** Closed ring of a view cone: apex at [center], an arc of [radiusM] spanning [openingDeg] around [headingDeg]. */
fun headingCone(center: LatLon, headingDeg: Double, radiusM: Double = 30.0, openingDeg: Double = 60.0): List<LatLon> {
    val steps = 12
    val arc = (0..steps).map { destination(center, headingDeg - openingDeg / 2 + openingDeg * it / steps, radiusM) }
    return listOf(center) + arc + center
}
