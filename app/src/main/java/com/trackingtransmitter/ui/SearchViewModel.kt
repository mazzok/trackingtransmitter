package com.trackingtransmitter.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trackingtransmitter.SchatzApp
import com.trackingtransmitter.audio.BeepPlayer
import com.trackingtransmitter.audio.FANFARE_MS
import com.trackingtransmitter.audio.WAYPOINT_CHIME_MS
import com.trackingtransmitter.audio.celebrate
import com.trackingtransmitter.audio.reachWaypoint
import com.trackingtransmitter.data.Stop
import com.trackingtransmitter.data.StopKind
import com.trackingtransmitter.data.Treasure
import com.trackingtransmitter.domain.BeaconSignal
import com.trackingtransmitter.domain.Fix
import com.trackingtransmitter.domain.HeadingThrottle
import com.trackingtransmitter.domain.LatLon
import com.trackingtransmitter.domain.SearchController
import com.trackingtransmitter.domain.SearchState
import com.trackingtransmitter.domain.distanceM
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

/** Number of stop [index] among the stops of its kind, and how many of that kind there are. */
private fun numbering(kinds: List<StopKind>, index: Int): Pair<Int, Int> {
    val kind = kinds[index]
    return kinds.take(index + 1).count { it == kind } to kinds.count { it == kind }
}

/** What the search screen says about the stop being searched; null for a hunt with a single treasure. */
internal fun routeText(kinds: List<StopKind>, leg: Int): String? {
    if (kinds.size < 2) return null
    val (nr, total) = numbering(kinds, leg)
    return when (kinds[leg]) {
        StopKind.WAYPOINT -> "Suche Wegpunkt $nr von $total"
        StopKind.TREASURE -> if (total == 1) "Jetzt zum Schatz!" else "Suche Schatz $nr von $total"
    }
}

/** Headline once stop [index] is reached. */
internal fun reachedTitle(kinds: List<StopKind>, index: Int): String {
    val (nr, total) = numbering(kinds, index)
    return when (kinds[index]) {
        StopKind.WAYPOINT -> "Wegpunkt $nr erreicht!"
        StopKind.TREASURE -> if (total == 1) "Gefunden! Der Schatz ist hier irgendwo." else "Schatz $nr von $total gefunden!"
    }
}

/**
 * Runs one search: fixes drive the controller, the heading drives the beeper. A null [treasure] (deleted) does nothing.
 * Reaching a stop on the way pauses the beeps for its sound (chime for a waypoint, fanfare for a treasure), then they lead on.
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
    val stops: List<Stop> = treasure?.stops ?: emptyList()
    val kinds: List<StopKind> = stops.map { it.kind }
    /** Index into [stops] of the stop being searched. */
    val leg: StateFlow<Int> = controller?.leg ?: MutableStateFlow(0)
    private val _reached = MutableStateFlow<Int?>(null)
    /** Index of the stop just reached on the way (never the last one), until dismissed or the next one is reached. */
    val reached: StateFlow<Int?> = _reached
    private val trail = mutableListOf<LatLon>()
    private val _walked = MutableStateFlow<List<LatLon>>(emptyList())
    /** The path walked up to the stop found last; the way to the stop still being searched stays hidden. */
    val walked: StateFlow<List<LatLon>> = _walked
    private val _foundCount = MutableStateFlow(0)
    /** How many stops, from the first on, have been found; never shrinks, so "Weitersuchen" keeps the last one shown. */
    val foundCount: StateFlow<Int> = _foundCount

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
                    if (c.state.value != SearchState.WaitingForSignal) {
                        val last = trail.lastOrNull()
                        if (last == null || distanceM(last, fix.position) >= TRAIL_STEP_M) trail += fix.position
                    }
                    if (c.leg.value != legBefore || c.state.value != before && c.state.value == SearchState.Found) {
                        _walked.value = trail.toList()
                        _foundCount.value = maxOf(_foundCount.value, c.leg.value + if (c.state.value == SearchState.Found) 1 else 0)
                    }
                    if (before != SearchState.Found && c.state.value == SearchState.Found) {
                        _reached.value = null
                        device.stopBeeping()
                        device.celebrate()
                    } else if (c.leg.value != legBefore) {
                        _reached.value = legBefore
                        device.stopBeeping()
                        val soundMs = if (kinds[legBefore] == StopKind.TREASURE) {
                            device.celebrate()
                            FANFARE_MS
                        } else {
                            device.reachWaypoint()
                            WAYPOINT_CHIME_MS
                        }
                        // a child of the search job, so pausing meanwhile cancels it (resume restarts the beeps itself)
                        search.launch {
                            delay(soundMs + STOP_PAUSE_MS)
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

    fun dismissReached() {
        _reached.value = null
    }

    fun continueSearching() {
        val c = controller ?: return
        c.continueSearching()
        if (job != null && c.state.value is SearchState.Searching) startBeeping(c)
    }

    private fun startBeeping(c: SearchController) = device.startBeeping { _heading.value?.let(c::signal) }

    override fun onCleared() = pause()

    private companion object {
        /** Silence after a stop's sound before the beeps lead on. */
        const val STOP_PAUSE_MS = 500L
        /** GPS jitter below this does not extend the walked path. */
        const val TRAIL_STEP_M = 2.0
    }
}
