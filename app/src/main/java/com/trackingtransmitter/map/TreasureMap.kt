package com.trackingtransmitter.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trackingtransmitter.domain.LatLon
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiPoint
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val USER_SRC = "user"
private const val CONE_SRC = "cone"
private const val TREASURE_SRC = "treasure"
private const val WAYPOINTS_SRC = "waypoints"
private const val ROUTE_SRC = "route"
private const val TRACK_SRC = "track"
private const val CHEST_IMAGE = "chest"
private const val CHEST_SIZE_DP = 28
private const val FOLLOW_ZOOM = 17.0
private val GERMANY_CENTER = LatLon(51.16, 10.45)
private const val GERMANY_ZOOM = 5.0

private fun point(p: LatLon): Point = Point.fromLngLat(p.lon, p.lat)

private fun coneFeatures(user: LatLon?, headingDeg: Double?): FeatureCollection =
    if (user == null || headingDeg == null) FeatureCollection.fromFeatures(emptyList())
    else FeatureCollection.fromFeature(Feature.fromGeometry(
        Polygon.fromLngLats(listOf(headingCone(user, headingDeg).map(::point))),
    ))

private fun pointFeatures(p: LatLon?): FeatureCollection =
    if (p == null) FeatureCollection.fromFeatures(emptyList()) else FeatureCollection.fromFeature(Feature.fromGeometry(point(p)))

private fun pointsFeatures(points: List<LatLon>): FeatureCollection =
    if (points.isEmpty()) FeatureCollection.fromFeatures(emptyList())
    else FeatureCollection.fromFeature(Feature.fromGeometry(MultiPoint.fromLngLats(points.map(::point))))

private fun lineFeatures(points: List<LatLon>): FeatureCollection =
    if (points.size < 2) FeatureCollection.fromFeatures(emptyList())
    else FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points.map(::point))))

/**
 * North-up map with the user (blue dot plus view cone along [headingDeg]), [treasures] as small chests (opened ones when
 * [treasuresOpen], i.e. found), orange [waypoints],
 * a dashed line through [route] in search order and a solid line along the walked [track].
 * Pass none of them wherever they must stay hidden.
 */
@Composable
fun TreasureMap(
    modifier: Modifier,
    user: LatLon?,
    headingDeg: Double?,
    followUser: Boolean,
    initialCenter: LatLon?,
    onTap: ((LatLon) -> Unit)?,
    treasures: List<LatLon> = emptyList(),
    waypoints: List<LatLon> = emptyList(),
    route: List<LatLon> = emptyList(),
    track: List<LatLon> = emptyList(),
    treasuresOpen: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val tapNow by rememberUpdatedState(onTap)
    val disposed = remember { AtomicBoolean(false) }
    var zoomApplied by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            // getMapAsync / setStyle callbacks may still arrive after this; they check the flag
            disposed.set(true)
            map = null
            style = null
            lifecycle.removeObserver(observer)
            // leaving the screen while the activity is still visible: MapLibre needs pause/stop before destroy
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            if (disposed.get()) return@getMapAsync
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isAttributionEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.addOnMapClickListener { ll ->
                val handler = tapNow
                handler?.invoke(LatLon(ll.latitude, ll.longitude))
                handler != null
            }
            val start = initialCenter ?: GERMANY_CENTER
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(start.lat, start.lon), if (initialCenter != null) FOLLOW_ZOOM else GERMANY_ZOOM,
            ))
            m.setStyle(Style.Builder().fromJson(MapStyle.json())) { s ->
                if (disposed.get()) return@setStyle
                s.addImage(CHEST_IMAGE, chestBitmap((CHEST_SIZE_DP * context.resources.displayMetrics.density).toInt(), open = treasuresOpen))
                s.addSource(GeoJsonSource(CONE_SRC))
                s.addSource(GeoJsonSource(TRACK_SRC))
                s.addSource(GeoJsonSource(ROUTE_SRC))
                s.addSource(GeoJsonSource(WAYPOINTS_SRC))
                s.addSource(GeoJsonSource(TREASURE_SRC))
                s.addSource(GeoJsonSource(USER_SRC))
                s.addLayer(FillLayer("cone-fill", CONE_SRC).withProperties(
                    PropertyFactory.fillColor("#0066CC"), PropertyFactory.fillOpacity(0.3f),
                ))
                s.addLayer(LineLayer("track-line", TRACK_SRC).withProperties(
                    PropertyFactory.lineColor("#7B1FA2"), PropertyFactory.lineWidth(4f), PropertyFactory.lineOpacity(0.8f),
                    PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
                ))
                s.addLayer(LineLayer("route-line", ROUTE_SRC).withProperties(
                    PropertyFactory.lineColor("#E65100"), PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
                ))
                s.addLayer(CircleLayer("waypoint-dot", WAYPOINTS_SRC).withProperties(
                    PropertyFactory.circleColor("#FB8C00"), PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2f),
                ))
                s.addLayer(SymbolLayer("treasure-chest", TREASURE_SRC).withProperties(
                    PropertyFactory.iconImage(CHEST_IMAGE), PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                ))
                s.addLayer(CircleLayer("user-dot", USER_SRC).withProperties(
                    PropertyFactory.circleColor("#0066CC"), PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2f),
                ))
                style = s
            }
        }
    }

    LaunchedEffect(style, user, headingDeg) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(USER_SRC)?.setGeoJson(pointFeatures(user))
        s.getSourceAs<GeoJsonSource>(CONE_SRC)?.setGeoJson(coneFeatures(user, headingDeg))
    }

    LaunchedEffect(style, track) {
        style?.getSourceAs<GeoJsonSource>(TRACK_SRC)?.setGeoJson(lineFeatures(track))
    }

    LaunchedEffect(style, treasures, waypoints, route) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(TREASURE_SRC)?.setGeoJson(pointsFeatures(treasures))
        s.getSourceAs<GeoJsonSource>(WAYPOINTS_SRC)?.setGeoJson(pointsFeatures(waypoints))
        s.getSourceAs<GeoJsonSource>(ROUTE_SRC)?.setGeoJson(lineFeatures(route))
    }

    LaunchedEffect(map, user, followUser) {
        val m = map ?: return@LaunchedEffect
        if (!followUser) { zoomApplied = false; return@LaunchedEffect }
        if (user == null) return@LaunchedEffect
        val at = LatLng(user.lat, user.lon)
        // zoom is set once when following starts; later fixes keep the user's own zoom
        val update = if (zoomApplied) CameraUpdateFactory.newLatLng(at) else CameraUpdateFactory.newLatLngZoom(at, FOLLOW_ZOOM)
        zoomApplied = true
        m.animateCamera(update, 500)
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        Text(
            MapStyle.ATTRIBUTION,
            fontSize = 10.sp,
            color = Color.Black,
            modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
