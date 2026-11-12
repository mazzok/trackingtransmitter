package com.trackingtransmitter.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest {
    private val target = LatLon(48.1, 11.5)
    private val treasure = Treasure("t1", "Schatz 1", target, 0L)
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
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

    @Test fun unknownTreasure_doesNothing() {
        val device = FakeDevice()
        val vm = SearchViewModel(app, null, device)
        assertFalse(vm.treasureKnown)
        vm.resume()
        assertFalse(device.beeping)
        assertEquals(0, device.fixFlow.subscriptionCount.value)
    }
}
