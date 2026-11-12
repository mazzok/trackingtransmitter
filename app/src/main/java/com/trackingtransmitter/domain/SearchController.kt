package com.trackingtransmitter.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Fix(val position: LatLon, val accuracyM: Float?)

/** Deliberately carries no distance: the UI must not be able to show it. */
sealed interface SearchState {
    data object WaitingForSignal : SearchState
    data class Searching(val position: LatLon) : SearchState
    data object Found : SearchState
}

class SearchController(private val target: LatLon, private val detector: ArrivalDetector = ArrivalDetector()) {
    private val _state = MutableStateFlow<SearchState>(SearchState.WaitingForSignal)
    val state: StateFlow<SearchState> = _state
    private var lastPosition: LatLon? = null

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
                _state.value = if (detector.onFix(distanceM(fix.position, target), fix.accuracyM)) {
                    SearchState.Found
                } else SearchState.Searching(fix.position)
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
