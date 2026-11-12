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

    private val W = north(200.0)
    private fun arriveAt(c: SearchController, p: LatLon) = repeat(3) { c.onFix(Fix(p, 5f)) }

    @Test fun route_leadsToWaypointFirst() { val c = SearchController(listOf(W, T))
        c.onFix(Fix(north(100.0), 8f)); assertEquals(0, c.leg.value)
        // the waypoint lies north, the treasure south: facing north is fastest
        assertEquals(150, c.signal(0.0)!!.intervalMs) }
    @Test fun route_waypointReached_advancesWithoutFinding() { val c = SearchController(listOf(W, T))
        c.onFix(Fix(north(100.0), 8f)); arriveAt(c, W)
        assertEquals(1, c.leg.value); assertTrue(c.state.value is SearchState.Searching)
        assertEquals(150, c.signal(180.0)!!.intervalMs) }
    @Test fun route_treasureBeforeWaypoint_doesNotCount() { val c = SearchController(listOf(W, T))
        c.onFix(Fix(north(100.0), 8f)); arriveAt(c, T)
        assertEquals(0, c.leg.value); assertTrue(c.state.value is SearchState.Searching) }
    @Test fun route_lastStop_isFound() { val c = SearchController(listOf(W, T))
        c.onFix(Fix(north(100.0), 8f)); arriveAt(c, W); arriveAt(c, T)
        assertEquals(SearchState.Found, c.state.value); assertEquals(1, c.leg.value) }
    @Test fun route_needsTwoFreshFixesPerStop() { val c = SearchController(listOf(W, north(201.0), T))
        c.onFix(Fix(north(100.0), 8f)); arriveAt(c, W); assertEquals(1, c.leg.value)
        // the next stop is 1 m away, but its streak starts anew
        c.onFix(Fix(W, 5f)); c.onFix(Fix(W, 5f)); assertEquals(1, c.leg.value)
        c.onFix(Fix(W, 5f)); assertEquals(2, c.leg.value) }
}
