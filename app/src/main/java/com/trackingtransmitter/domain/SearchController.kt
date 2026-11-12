package com.trackingtransmitter.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Fix(val position: LatLon, val accuracyM: Float?)

/** Deliberately carries no distance: the UI must not be able to show it. */
sealed interface SearchState {
    data object WaitingForSignal : SearchState
    data class Searching(val position: LatLon) : SearchState
    /** The last stop of the route, the treasure, was reached. */
    data object Found : SearchState
}

/**
 * Leads along [route]: the waypoints in order, the treasure last. Each stop counts as reached exactly like the
 * treasure (see [ArrivalDetector]); reaching a waypoint advances [leg] to the next stop, reaching the last one is [SearchState.Found].
 */
class SearchController(private val route: List<LatLon>, private val detector: ArrivalDetector = ArrivalDetector()) {
    constructor(target: LatLon) : this(listOf(target))

    init {
        require(route.isNotEmpty()) { "route needs at least the treasure" }
    }

    private val _state = MutableStateFlow<SearchState>(SearchState.WaitingForSignal)
    val state: StateFlow<SearchState> = _state
    private val _leg = MutableStateFlow(0)
    /** Index into the route of the stop being searched; the waypoints come first, so `leg == route.size - 1` is the treasure. */
    val leg: StateFlow<Int> = _leg
    private var lastPosition: LatLon? = null

    private val target get() = route[_leg.value]

    fun onFix(fix: Fix) {
        when (_state.value) {
            SearchState.WaitingForSignal -> {
                val acc = fix.accuracyM
                if (acc != null && acc <= START_MAX_ACCURACY_M) {
                    lastPosition = fix.position
                    _state.value = SearchState.Searching(fix.position)
                }
            }
            is SearchState.Searching -> {
                lastPosition = fix.position
                if (!detector.onFix(distanceM(fix.position, target), fix.accuracyM)) {
                    _state.value = SearchState.Searching(fix.position)
                } else if (_leg.value < route.lastIndex) {
                    detector.reset()
                    _leg.value += 1
                    _state.value = SearchState.Searching(fix.position)
                } else {
                    _state.value = SearchState.Found
                }
            }
            SearchState.Found -> Unit
        }
    }

    fun signal(headingDeg: Double): BeaconSignal? {
        val s = _state.value as? SearchState.Searching ?: return null
        return beaconSignal(distanceM(s.position, target), angleDiffDeg(bearingDeg(s.position, target), headingDeg))
    }

    fun continueSearching() {
        if (_state.value != SearchState.Found) return
        val pos = lastPosition ?: return
        detector.reset()
        _state.value = SearchState.Searching(pos)
    }
}
