package com.kurtz.homerecord.housemanual.mapping

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.mapping.AlarmPlayer
import com.kurtz.homerecord.core.mapping.ChargingMonitor
import com.kurtz.homerecord.core.mapping.MappingState
import com.kurtz.homerecord.core.mapping.PowerEvent
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.nio.file.Files

private class FakeMonitor(var charging: Boolean) : ChargingMonitor {
    val flow = MutableSharedFlow<PowerEvent>(extraBufferCapacity = 8)
    override fun isCharging() = charging
    override fun events(): Flow<PowerEvent> = flow
}

private class FakeAlarm : AlarmPlayer {
    override var isPlaying = false
        private set
    override fun start() { isPlaying = true }
    override fun stop() { isPlaying = false }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MappingViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var panels: PanelRepository
    private lateinit var breaker: Breaker
    private lateinit var roomId: String
    private val monitor = FakeMonitor(charging = true)
    private val alarm = FakeAlarm()

    @Before fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        homes = HomeRepository(db, Source.HOUSE_MANUAL)
        panels = PanelRepository(db, MediaFileStore(Files.createTempDirectory("m").toFile()), Source.HOUSE_MANUAL)
        val home = homes.createHome("H")
        roomId = homes.addRoom(home.id, "Kitchen").id
        val panel = panels.createPanel(home.id, "Main", 20, PanelLayout.NA_NUMBERED)
        breaker = panels.saveBreaker(
            Breaker("", panel.id, listOf(3), label = "Kitchen outlets", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL),
        )
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun vm() = MappingViewModel(panels, homes, monitor, alarm).also { it.state.first { s -> s.loaded } }

    @Test fun `AC-35 start is only enabled while charging`() = runTest {
        monitor.charging = false
        val vm = vm()
        assertFalse(vm.state.value.canStart)
        monitor.flow.emit(PowerEvent.Connected)
        assertTrue(vm.state.value.canStart)
    }

    @Test fun `AC-37 power loss while armed sounds the alarm and AC-38 stop silences it`() = runTest {
        val vm = vm()
        vm.onEvent(MappingEvent.Start)
        assertEquals(MappingState.Armed, vm.state.value.mapping)
        assertFalse(alarm.isPlaying)
        monitor.flow.emit(PowerEvent.Disconnected)
        assertEquals(MappingState.Alarm, vm.state.value.mapping)
        assertTrue(alarm.isPlaying)
        vm.onEvent(MappingEvent.Stop)
        assertEquals(MappingState.Confirm, vm.state.value.mapping)
        assertFalse(alarm.isPlaying)
    }

    @Test fun `AC-38 reconnecting power stops the alarm`() = runTest {
        val vm = vm()
        vm.onEvent(MappingEvent.Start)
        monitor.flow.emit(PowerEvent.Disconnected)
        monitor.flow.emit(PowerEvent.Connected)
        assertEquals(MappingState.Confirm, vm.state.value.mapping)
        assertFalse(alarm.isPlaying)
    }

    @Test fun `AC-40 save with a new outlet links it and confirms the breaker`() = runTest {
        val vm = vm()
        vm.onEvent(MappingEvent.Start)
        monitor.flow.emit(PowerEvent.Disconnected)
        vm.onEvent(MappingEvent.Stop)
        vm.onEvent(MappingEvent.BreakerPicked(breaker.id))
        vm.onEvent(MappingEvent.RoomPicked(roomId))
        assertFalse(vm.state.value.canSave)
        vm.onEvent(MappingEvent.NewOutletChanged("Counter outlet"))
        assertTrue(vm.state.value.canSave)
        vm.onEvent(MappingEvent.Save)
        vm.state.first { it.saved }
        val endpoint = panels.observeEndpointsForBreaker(breaker.id).first().single()
        assertEquals("Counter outlet", endpoint.label)
        assertEquals(roomId, endpoint.roomId)
        assertEquals(Confidence.CONFIRMED, panels.getBreaker(breaker.id)?.confidence)
        assertTrue(vm.state.value.saved)
        assertEquals(Confidence.CONFIRMED, vm.state.value.breakers.single().confidence)
    }

    @Test fun `AC-39 existing endpoints in the chosen room are listed and reusable`() = runTest {
        val existing = panels.saveEndpoint(
            com.kurtz.homerecord.core.model.Endpoint("", null, roomId, com.kurtz.homerecord.core.model.EndpointKind.OUTLET, "Island", 0, 0, Source.HOUSE_MANUAL),
        )
        val vm = vm()
        vm.onEvent(MappingEvent.RoomPicked(roomId))
        assertEquals(listOf(existing.id), vm.state.first { it.endpoints.isNotEmpty() }.endpoints.map { it.id })
        vm.onEvent(MappingEvent.BreakerPicked(breaker.id))
        vm.onEvent(MappingEvent.EndpointPicked(existing.id))
        vm.onEvent(MappingEvent.Save)
        vm.state.first { it.saved }
        assertEquals(breaker.id, panels.observeEndpointsForRoom(roomId).first().single().breakerId)
    }

    @Test fun `AC-41 clearing the view model stops the alarm`() = runTest {
        val vm = vm()
        vm.onEvent(MappingEvent.Start)
        monitor.flow.emit(PowerEvent.Disconnected)
        assertTrue(alarm.isPlaying)
        val clear = vm.javaClass.getDeclaredMethod("onCleared").apply { isAccessible = true }
        clear.invoke(vm)
        assertFalse(alarm.isPlaying)
    }
}
