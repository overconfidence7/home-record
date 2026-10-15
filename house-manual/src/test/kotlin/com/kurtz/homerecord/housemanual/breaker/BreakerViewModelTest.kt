package com.kurtz.homerecord.housemanual.breaker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BreakerViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var panels: PanelRepository
    private lateinit var panel: Panel

    @Before fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        homes = HomeRepository(db, Source.HOUSE_MANUAL)
        panels = PanelRepository(db, MediaFileStore(Files.createTempDirectory("m").toFile()), Source.HOUSE_MANUAL)
        panel = panels.createPanel(homes.createHome("H").id, "Main", 20, PanelLayout.NA_NUMBERED)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun vm(slot: Int, breakerId: String? = null, tandem: Int? = null) =
        BreakerViewModel(panel.id, slot, breakerId, tandem, panels).also { it.state.first { s -> s.loaded } }

    @Test fun `AC-8 saving on an empty slot creates a breaker there with unknown confidence`() = runTest {
        val vm = vm(5)
        assertFalse(vm.state.value.canSave)
        vm.onEvent(BreakerEvent.LabelChanged("Kitchen"))
        vm.onEvent(BreakerEvent.AmpsChanged("20"))
        vm.onEvent(BreakerEvent.Save)
        vm.done.first()
        val b = panels.observeBreakers(panel.id).first().single()
        assertEquals(listOf(5), b.slots)
        assertEquals("Kitchen", b.label)
        assertEquals(20, b.amps)
        assertEquals(Confidence.UNKNOWN, b.confidence)
    }

    @Test fun `invalid amps blocks save`() = runTest {
        val vm = vm(5)
        vm.onEvent(BreakerEvent.LabelChanged("X"))
        vm.onEvent(BreakerEvent.AmpsChanged("abc"))
        assertFalse(vm.state.value.canSave)
    }

    @Test fun `AC-9 two-pole occupies N and N+2`() = runTest {
        val vm = vm(1)
        vm.onEvent(BreakerEvent.LabelChanged("Dryer"))
        vm.onEvent(BreakerEvent.PolesChanged(2))
        vm.onEvent(BreakerEvent.Save)
        vm.done.first()
        assertEquals(listOf(1, 3), panels.observeBreakers(panel.id).first().single().slots)
    }

    @Test fun `AC-9 two-pole is blocked with a message when N+2 is occupied`() = runTest {
        panels.saveBreaker(Breaker("", panel.id, listOf(3), label = "Den", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL))
        val vm = vm(1)
        vm.onEvent(BreakerEvent.LabelChanged("Dryer"))
        vm.onEvent(BreakerEvent.PolesChanged(2))
        vm.onEvent(BreakerEvent.Save)
        assertEquals(SlotProblem.Occupied(3), vm.state.first { it.problem != null }.problem)
        assertEquals(1, panels.observeBreakers(panel.id).first().size)
    }

    @Test fun `editing an existing two-pole breaker keeps it saveable and updates fields`() = runTest {
        val b = panels.saveBreaker(Breaker("", panel.id, listOf(1, 3), 2, label = "Dryer", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL))
        val vm = vm(3, b.id)
        assertEquals(1, vm.state.value.slot)
        vm.onEvent(BreakerEvent.ConfidenceChanged(Confidence.CONFIRMED))
        vm.onEvent(BreakerEvent.Save)
        vm.done.first()
        val saved = panels.observeBreakers(panel.id).first().single()
        assertEquals(listOf(1, 3), saved.slots)
        assertEquals(Confidence.CONFIRMED, saved.confidence)
    }

    @Test fun `filling an empty tandem half saves a one-pole breaker with that index`() = runTest {
        val vm = vm(7, tandem = 1)
        vm.onEvent(BreakerEvent.LabelChanged("Bath fan"))
        vm.onEvent(BreakerEvent.Save)
        vm.done.first()
        val b = panels.observeBreakers(panel.id).first().single()
        assertEquals(listOf(7), b.slots)
        assertEquals(1, b.tandemIndex)
        assertEquals(1, b.poles)
    }

    @Test fun `AC-12 delete clears the slots and detaches endpoints`() = runTest {
        val b = panels.saveBreaker(Breaker("", panel.id, listOf(2), label = "Garage", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL))
        val ep = panels.saveEndpoint(Endpoint("", b.id, homes.addRoom(panel.homeId, "Garage").id, EndpointKind.OUTLET, "Freezer", 0, 0, Source.HOUSE_MANUAL))
        val vm = vm(2, b.id)
        vm.onEvent(BreakerEvent.DeleteClicked)
        assertTrue(vm.state.value.confirmingDelete)
        vm.onEvent(BreakerEvent.DeleteConfirmed)
        vm.done.first()
        assertTrue(panels.observeBreakers(panel.id).first().isEmpty())
        assertNull(panels.observeEndpointsForBreaker(b.id).first().firstOrNull())
        assertEquals(ep.id, db.endpointDao().get(ep.id)?.id)
        assertNull(db.endpointDao().get(ep.id)?.breakerId)
    }
}
