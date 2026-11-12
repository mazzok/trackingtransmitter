package com.trackingtransmitter.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trackingtransmitter.SchatzApp
import com.trackingtransmitter.audio.BeepPlayer
import com.trackingtransmitter.audio.WAYPOINT_CHIME_MS
import com.trackingtransmitter.audio.celebrate
import com.trackingtransmitter.audio.reachWaypoint
import com.trackingtransmitter.data.Treasure
import com.trackingtransmitter.domain.BeaconSignal
import com.trackingtransmitter.domain.Fix
import com.trackingtransmitter.domain.HeadingThrottle
import com.trackingtransmitter.domain.LatLon
import com.trackingtransmitter.domain.SearchController
import com.trackingtransmitter.domain.SearchState
import com.trackingtransmitter.sensors.HeadingSensor
import com.trackingtransmitter.sensors.HeadingSource
import com.trackingtransmitter.sensors.LocationSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The phone parts a search uses; replaced by a fake in tests. */
internal interface SearchDevice {
    val headingSensor: HeadingSensor
    val needsCalibration: StateFlow<Boolean>
    fun fixes(): Flow<Fix>
    fun headings(): Flow<Double>
    fun startBeeping(signal: () -> BeaconSignal?)
    fun stopBeeping()
    fun celebrate()
    fun reachWaypoint()
}

private class PhoneSearchDevice(private val app: Application) : SearchDevice {
    private val locations = LocationSource(app)
    private val headingSource = HeadingSource(app)
    private val beeper = BeepPlayer()

    override val headingSensor get() = headingSource.sensor
    override val needsCalibration get() = headingSource.needsCalibration
    override fun fixes() = locations.fixes()
    override fun headings() = headingSource.headings()
    override fun startBeeping(signal: () -> BeaconSignal?) = beeper.start(signal)
    override fun stopBeeping() = beeper.stop()
    override fun celebrate() = celebrate(app)
    override fun reachWaypoint() = reachWaypoint(app)
}

/** What the search screen says about the route; null for a treasure without waypoints. */
internal fun routeText(leg: Int, waypointCount: Int): String? = when {
    waypointCount == 0 -> null
    leg == 0 -> "Suche Wegpunkt 1 von $waypointCount"
    leg < waypointCount -> "Wegpunkt $leg erreicht! Weiter zu Wegpunkt ${leg + 1} von $waypointCount"
    else -> "Alle Wegpunkte gefunden – jetzt zum Schatz!"
}

/**
 * Runs one search: fixes drive the controller, the heading drives the beeper. A null [treasure] (deleted) does nothing.
 * Reaching a waypoint pauses the beeps for its chime, then they lead to the next stop.
 */
class SearchViewModel internal constructor(
    app: Application,
    treasure: Treasure?,
    private val device: SearchDevice,
) : AndroidViewModel(app) {
    constructor(app: Application, treasureId: String) :
        this(app, (app as SchatzApp).treasures.get(treasureId), PhoneSearchDevice(app))

    private val controller = treasure?.let { SearchController(it.route) }
    val treasureKnown: Boolean = controller != null
    val waypointCount: Int = treasure?.waypoints?.size ?: 0
    /** Index of the stop being searched: 0 until [waypointCount] - 1 are waypoints, [waypointCount] is the treasure. */
    val leg: StateFlow<Int> = controller?.leg ?: MutableStateFlow(0)

    val state: StateFlow<SearchState> = controller?.state ?: MutableStateFlow(SearchState.WaitingForSignal)
    private val _user = MutableStateFlow<LatLon?>(null)
    val user: StateFlow<LatLon?> = _user
    private val _heading = MutableStateFlow<Double?>(null)
    val heading: StateFlow<Double?> = _heading
    private val _mapHeading = MutableStateFlow<Double?>(null)
    /** [heading] thinned out for drawing the view cone; the beeper uses the unthrottled one. */
    val mapHeading: StateFlow<Double?> = _mapHeading
    private val mapThrottle = HeadingThrottle()
    val headingSensor: HeadingSensor = device.headingSensor
    val needsCalibration: StateFlow<Boolean> = device.needsCalibration

    private var job: Job? = null

    fun resume() {
        val c = controller ?: return
        if (job != null || headingSensor == HeadingSensor.NONE) return
        job = viewModelScope.launch {
            val search = this
            launch {
                device.fixes().collect { fix ->
                    _user.value = fix.position
                    val before = c.state.value
                    val legBefore = c.leg.value
                    c.onFix(fix)
                    if (before != SearchState.Found && c.state.value == SearchState.Found) {
                        device.stopBeeping()
                        device.celebrate()
                    } else if (c.leg.value != legBefore) {
                        device.stopBeeping()
                        device.reachWaypoint()
                        // a child of the search job, so pausing meanwhile cancels it (resume restarts the beeps itself)
                        search.launch {
                            delay(WAYPOINT_CHIME_MS + WAYPOINT_PAUSE_MS)
                            if (c.state.value is SearchState.Searching) startBeeping(c)
                        }
                    }
                }
            }
            launch {
                device.headings().collect {
                    _heading.value = it
                    if (mapThrottle.accept(it, SystemClock.elapsedRealtime())) _mapHeading.value = it
                }
            }
        }
        if (c.state.value != SearchState.Found) startBeeping(c)
    }

    fun pause() {
        job?.cancel()
        job = null
        device.stopBeeping()
    }

    fun continueSearching() {
        val c = controller ?: return
        c.continueSearching()
        if (job != null && c.state.value is SearchState.Searching) startBeeping(c)
    }

    private fun startBeeping(c: SearchController) = device.startBeeping { _heading.value?.let(c::signal) }

    override fun onCleared() = pause()

    private companion object {
        /** Silence after the waypoint chime before the beeps lead on. */
        const val WAYPOINT_PAUSE_MS = 500L
    }
}
