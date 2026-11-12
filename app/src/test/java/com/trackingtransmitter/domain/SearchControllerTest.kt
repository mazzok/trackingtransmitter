package com.trackingtransmitter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchControllerTest {
    private val T = LatLon(48.0, 11.0)
    private fun north(m: Double) = LatLon(48.0 + m / 111_195.0, 11.0)

    @Test fun startsWaiting() = assertEquals(SearchState.WaitingForSignal, SearchController(T).state.value)
    @Test fun poorAccuracy_keepsWaiting_andSilent() { val c = SearchController(T)
        c.onFix(Fix(north(100.0), 35f)); assertEquals(SearchState.WaitingForSignal, c.state.value); assertNull(c.signal(0.0)) }
    @Test fun goodFix_startsSearching() { val c = SearchController(T)
        c.onFix(Fix(north(100.0), 8f)); assertTrue(c.state.value is SearchState.Searching) }
    @Test fun facingTarget_fromNorth_isFast() { val c = SearchController(T)
        c.onFix(Fix(north(100.0), 8f)); assertEquals(150, c.signal(180.0)!!.intervalMs); assertEquals(1500, c.signal(0.0)!!.intervalMs) }
    @Test fun arrival_found_andSilent() { val c = SearchController(T)
        c.onFix(Fix(north(100.0), 8f)); repeat(3) { c.onFix(Fix(north(2.0), 5f)) }
        assertEquals(SearchState.Found, c.state.value); assertNull(c.signal(180.0))
        c.onFix(Fix(north(100.0), 5f)); assertEquals(SearchState.Found, c.state.value) }
    @Test fun continueSearching_reArms() { val c = SearchController(T)
        c.onFix(Fix(north(100.0), 8f)); repeat(3) { c.onFix(Fix(north(2.0), 5f)) }
        c.continueSearching(); assertTrue(c.state.value is SearchState.Searching)
        c.onFix(Fix(north(2.0), 5f)); assertTrue(c.state.value is SearchState.Searching) }
}
