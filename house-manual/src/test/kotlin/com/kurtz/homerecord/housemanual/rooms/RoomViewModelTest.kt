package com.kurtz.homerecord.housemanual.rooms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffKind
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.EquipmentRepository
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import com.kurtz.homerecord.core.repo.ShutoffRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
class RoomViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var panels: PanelRepository
    private lateinit var shutoffs: ShutoffRepository
    private lateinit var equipment: EquipmentRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        val media = MediaFileStore(Files.createTempDirectory("media").toFile())
        homes = HomeRepository(db, Source.HOUSE_MANUAL)
        panels = PanelRepository(db, media, Source.HOUSE_MANUAL)
        shutoffs = ShutoffRepository(db, media, Source.HOUSE_MANUAL)
        equipment = EquipmentRepository(db, media, Source.HOUSE_MANUAL)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(roomId: String) = RoomViewModel(roomId, homes, panels, shutoffs, equipment)

    @Test fun `AC-20 and AC-21 room lists direct and endpoint breakers, equipment and shutoffs`() = runTest {
        val home = homes.createHome("H")
        val kitchen = homes.addRoom(home.id, "Kitchen")
        val garage = homes.addRoom(home.id, "Garage")
        val panel = panels.createPanel(home.id, "Main", slotCount = 20, layout = PanelLayout.NA_NUMBERED)
        val direct = panels.saveBreaker(breaker(panel.id, 1, "Direct", Confidence.CONFIRMED))
        val viaEndpoint = panels.saveBreaker(breaker(panel.id, 2, "Via endpoint", Confidence.LIKELY))
        val other = panels.saveBreaker(breaker(panel.id, 3, "Garage only", Confidence.UNKNOWN))
        panels.linkRoom(direct.id, kitchen.id)
        panels.linkRoom(other.id, garage.id)
        panels.saveEndpoint(Endpoint("", viaEndpoint.id, kitchen.id, EndpointKind.OUTLET, "Counter", 0, 0, Source.HOUSE_MANUAL))
        equipment.save(Equipment("", home.id, kitchen.id, EquipmentCategory.DISHWASHER, "Bosch", null, null, null, null, null, null, null, 0, 0, Source.HOUSE_MANUAL))
        equipment.save(Equipment("", home.id, garage.id, EquipmentCategory.FURNACE, "Carrier", null, null, null, null, null, null, null, 0, 0, Source.HOUSE_MANUAL))
        shutoffs.save(Shutoff("", home.id, ShutoffKind.FIXTURE_VALVE, kitchen.id, null, "", 0, 0, Source.HOUSE_MANUAL))
        shutoffs.save(Shutoff("", home.id, ShutoffKind.WATER_MAIN, null, null, "", 0, 0, Source.HOUSE_MANUAL))

        val vm = vm(kitchen.id)
        val job = launchCollect(vm)
        val s = vm.state.first { it.room != null && it.breakers.size == 2 }
        assertEquals(listOf("Direct", "Via endpoint"), s.breakers.map { it.label })
        assertEquals(listOf(Confidence.CONFIRMED, Confidence.LIKELY), s.breakers.map { it.confidence })
        assertEquals(listOf("Bosch"), s.equipment.map { it.make })
        assertEquals(listOf(ShutoffKind.FIXTURE_VALVE), s.shutoffs.map { it.kind })
        job.cancel()
    }

    @Test fun `rename trims, blank rename is rejected`() = runTest {
        val home = homes.createHome("H")
        val room = homes.addRoom(home.id, "Den")
        val vm = vm(room.id)
        val job = launchCollect(vm)
        vm.state.first { it.room != null }
        vm.onEvent(RoomEvent.StartRename)
        assertEquals("Den", vm.state.first { it.renaming && it.renameInput == "Den" }.renameInput)
        vm.onEvent(RoomEvent.RenameChanged("   "))
        assertEquals(false, vm.state.first { it.renameInput == "   " }.canSaveRename)
        vm.onEvent(RoomEvent.SaveRename)
        assertEquals("Den", homes.getRoom(room.id)?.name)
        vm.onEvent(RoomEvent.RenameChanged("  Study "))
        vm.onEvent(RoomEvent.SaveRename)
        vm.state.first { it.room?.name == "Study" }
        job.cancel()
    }

    @Test fun `delete needs confirmation and removes the room`() = runTest {
        val home = homes.createHome("H")
        val room = homes.addRoom(home.id, "Den")
        val vm = vm(room.id)
        val job = launchCollect(vm)
        vm.state.first { it.room != null }
        vm.onEvent(RoomEvent.AskDelete)
        vm.onEvent(RoomEvent.CancelDelete)
        assertEquals("Den", homes.getRoom(room.id)?.name)
        vm.onEvent(RoomEvent.AskDelete)
        vm.onEvent(RoomEvent.ConfirmDelete)
        vm.deleted.first()
        assertNull(homes.getRoom(room.id))
        job.cancel()
    }

    @Test fun `rooms list adds trimmed unique names`() = runTest {
        val home = homes.createHome("H")
        homes.addRoom(home.id, "Den")
        val vm = RoomsViewModel(homes)
        val job = launchCollect(vm.state)
        vm.onEvent(RoomsEvent.NewNameChanged("  "))
        assertEquals(false, vm.state.value.canAdd)
        vm.onEvent(RoomsEvent.NewNameChanged("den"))
        vm.onEvent(RoomsEvent.Add)
        vm.onEvent(RoomsEvent.NewNameChanged(" Attic "))
        vm.onEvent(RoomsEvent.Add)
        val s = vm.state.first { it.rooms.size == 2 }
        assertEquals(listOf("Attic", "Den"), s.rooms.map { it.name })
        assertTrue(s.newName.isEmpty())
        job.cancel()
    }

    private fun breaker(panelId: String, slot: Int, label: String, c: Confidence) =
        Breaker(id = "", panelId = panelId, slots = listOf(slot), label = label, confidence = c, createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL)

    private fun TestScope.launchCollect(vm: RoomViewModel) = launchCollect(vm.state)

    private fun TestScope.launchCollect(flow: StateFlow<*>) = backgroundScope.launch { flow.collect { } }
}
