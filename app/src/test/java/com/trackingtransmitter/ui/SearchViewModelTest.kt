package com.trackingtransmitter.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.trackingtransmitter.data.Stop
import com.trackingtransmitter.data.StopKind
import com.trackingtransmitter.data.Treasure
import com.trackingtransmitter.domain.BeaconSignal
import com.trackingtransmitter.domain.Fix
import com.trackingtransmitter.domain.LatLon
import com.trackingtransmitter.domain.SearchState
import com.trackingtransmitter.sensors.HeadingSensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeDevice(override val headingSensor: HeadingSensor = HeadingSensor.ROTATION_VECTOR) : SearchDevice {
    override val needsCalibration = MutableStateFlow(false)
    val fixFlow = MutableSharedFlow<Fix>(extraBufferCapacity = 16)
    val headingFlow = MutableSharedFlow<Double>(extraBufferCapacity = 16)
    var beeping = false
    var beepStarts = 0
    var celebrations = 0
    var waypointChimes = 0
    var signal: (() -> BeaconSignal?)? = null

    override fun fixes(): Flow<Fix> = fixFlow
    override fun headings(): Flow<Double> = headingFlow
    override fun startBeeping(signal: () -> BeaconSignal?) {
        beeping = true
        beepStarts++
        this.signal = signal
    }
    override fun stopBeeping() {
        beeping = false
    }
    override fun celebrate() {
        celebrations++
    }
    override fun reachWaypoint() {
        waypointChimes++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest {
    private val target = LatLon(48.1, 11.5)
    private val treasure = Treasure("t1", "Schatz 1", 0L, listOf(Stop(target, StopKind.TREASURE)))
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun atTarget() = Fix(target, 5f)

    @Test fun found_stopsBeeperAndCelebratesOnce() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, treasure, device)
        vm.resume()
        assertTrue(device.beeping)
        repeat(4) { device.fixFlow.tryEmit(atTarget()) }
        assertEquals(SearchState.Found, vm.state.value)
        assertFalse(device.beeping)
        assertEquals(1, device.celebrations)
        repeat(3) { device.fixFlow.tryEmit(atTarget()) }
        assertEquals(1, device.celebrations)
    }

    @Test fun pause_stopsBeeperAndListening() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, treasure, device)
        vm.resume()
        vm.pause()
        assertFalse(device.beeping)
        assertEquals(0, device.fixFlow.subscriptionCount.value)
        assertEquals(0, device.headingFlow.subscriptionCount.value)
    }

    @Test fun headingAndFix_feedTheBeeper() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, treasure, device)
        vm.resume()
        device.fixFlow.tryEmit(Fix(LatLon(48.0, 11.5), 5f))
        device.headingFlow.tryEmit(0.0)
        assertEquals(LatLon(48.0, 11.5), vm.user.value)
        assertEquals(0.0, vm.heading.value!!, 0.0)
        assertNotNull(device.signal!!())
    }

    @Test fun mapHeading_isThrottled_beeperHeadingIsNot() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, treasure, device)
        vm.resume()
        device.headingFlow.tryEmit(10.0)
        device.headingFlow.tryEmit(40.0)   // same instant: too soon for the map
        assertEquals(40.0, vm.heading.value!!, 0.0)
        assertEquals(10.0, vm.mapHeading.value!!, 0.0)
    }

    @Test fun continueSearching_restartsBeeper() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, treasure, device)
        vm.resume()
        repeat(4) { device.fixFlow.tryEmit(atTarget()) }
        vm.continueSearching()
        assertTrue(vm.state.value is SearchState.Searching)
        assertTrue(device.beeping)
    }

    @Test fun resumeWhileFound_staysSilent() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, treasure, device)
        vm.resume()
        repeat(4) { device.fixFlow.tryEmit(atTarget()) }
        vm.pause()
        vm.resume()
        assertFalse(device.beeping)
        assertEquals(1, device.celebrations)
    }

    private val waypoint = LatLon(48.2, 11.5)
    private val withWaypoint = Treasure("t2", "Schatz 2", 0L, listOf(Stop(waypoint, StopKind.WAYPOINT, "Unter der Bank"), Stop(target, StopKind.TREASURE)))

    @Test fun waypointReached_chimesThenBeepsOnTowardsTreasure() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, withWaypoint, device)
        vm.resume()
        repeat(4) { device.fixFlow.tryEmit(Fix(waypoint, 5f)) }
        assertEquals(1, vm.leg.value)
        assertEquals(0, vm.reached.value)
        assertEquals("Unter der Bank", vm.stops[vm.reached.value!!].message)
        assertEquals(1, device.waypointChimes)
        assertEquals(0, device.celebrations)
        assertFalse(device.beeping)
        dispatcher.scheduler.advanceTimeBy(5_000)
        assertTrue(device.beeping)
        repeat(3) { device.fixFlow.tryEmit(atTarget()) }
        assertEquals(SearchState.Found, vm.state.value)
        assertEquals(1, device.celebrations)
        assertEquals(1, device.waypointChimes)
    }

    @Test fun pauseDuringWaypointChime_staysSilent() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, withWaypoint, device)
        vm.resume()
        repeat(4) { device.fixFlow.tryEmit(Fix(waypoint, 5f)) }
        vm.pause()
        dispatcher.scheduler.advanceTimeBy(5_000)
        assertFalse(device.beeping)
    }

    private val second = LatLon(48.3, 11.5)
    private val twoTreasures = Treasure("t3", "Jagd", 0L, listOf(Stop(second, StopKind.TREASURE, "Weiter!"), Stop(target, StopKind.TREASURE, "Ende")))

    @Test fun treasureOnTheWay_playsFanfareThenBeepsOn() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, twoTreasures, device)
        vm.resume()
        repeat(4) { device.fixFlow.tryEmit(Fix(second, 5f)) }
        assertTrue(vm.state.value is SearchState.Searching)
        assertEquals(1, device.celebrations)
        assertEquals(0, device.waypointChimes)
        assertEquals(0, vm.reached.value)
        assertFalse(device.beeping)
        dispatcher.scheduler.advanceTimeBy(5_000)
        assertTrue(device.beeping)
        repeat(3) { device.fixFlow.tryEmit(atTarget()) }
        assertEquals(SearchState.Found, vm.state.value)
        assertEquals(2, device.celebrations)
        assertEquals(null, vm.reached.value)
    }

    @Test fun walkedPath_showsOnlyOnceAStopIsFound() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, withWaypoint, device)
        vm.resume()
        device.fixFlow.tryEmit(Fix(LatLon(48.15, 11.5), 5f))
        device.fixFlow.tryEmit(Fix(LatLon(48.18, 11.5), 5f))
        assertTrue(vm.walked.value.isEmpty())
        repeat(3) { device.fixFlow.tryEmit(Fix(waypoint, 5f)) }
        assertEquals(listOf(LatLon(48.15, 11.5), LatLon(48.18, 11.5), waypoint), vm.walked.value)
        device.fixFlow.tryEmit(Fix(LatLon(48.15, 11.5), 5f))
        assertEquals(3, vm.walked.value.size)
        repeat(3) { device.fixFlow.tryEmit(atTarget()) }
        assertEquals(target, vm.walked.value.last())
    }

    @Test fun dismissReached_hidesCard() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, withWaypoint, device)
        vm.resume()
        repeat(4) { device.fixFlow.tryEmit(Fix(waypoint, 5f)) }
        vm.dismissReached()
        assertEquals(null, vm.reached.value)
    }

    @Test fun foundCount_growsPerStop_andStaysAfterContinueSearching() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, withWaypoint, device)
        vm.resume()
        device.fixFlow.tryEmit(Fix(LatLon(48.15, 11.5), 5f))
        assertEquals(0, vm.foundCount.value)
        repeat(3) { device.fixFlow.tryEmit(Fix(waypoint, 5f)) }
        assertEquals(1, vm.foundCount.value)
        repeat(3) { device.fixFlow.tryEmit(atTarget()) }
        assertEquals(2, vm.foundCount.value)
        vm.continueSearching()
        assertEquals(2, vm.foundCount.value)
    }

    @Test fun routeText_perLeg() {
        val w = StopKind.WAYPOINT
        val t = StopKind.TREASURE
        assertEquals("Suche Schatz 1", routeText(listOf(t), 0))
        assertEquals("Suche Wegpunkt 1", routeText(listOf(w, w, t), 0))
        assertEquals("Suche Wegpunkt 2", routeText(listOf(w, w, t), 1))
        assertEquals("Suche Schatz 1", routeText(listOf(w, w, t), 2))
        assertEquals("Suche Schatz 2", routeText(listOf(t, w, t), 2))
    }

    @Test fun reachedTitle_perKind() {
        val w = StopKind.WAYPOINT
        val t = StopKind.TREASURE
        assertEquals("Wegpunkt 2 erreicht!", reachedTitle(listOf(w, w, t), 1))
        assertEquals("Schatz 1 gefunden!", reachedTitle(listOf(t, w, t), 0))
        assertEquals("Schatz 2 gefunden!", reachedTitle(listOf(t, w, t), 2))
    }

    @Test fun unknownTreasure_doesNothing() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, null, device)
        assertFalse(vm.treasureKnown)
        vm.resume()
        assertFalse(device.beeping)
        assertEquals(0, device.fixFlow.subscriptionCount.value)
    }
}
