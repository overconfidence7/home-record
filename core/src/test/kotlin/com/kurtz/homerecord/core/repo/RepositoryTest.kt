package com.kurtz.homerecord.core.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.logic.PanelProfile
import com.kurtz.homerecord.core.model.PanelKind
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.RoomKind
import com.kurtz.homerecord.core.model.TandemPolicy
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffKind
import com.kurtz.homerecord.core.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var dir: File
    private lateinit var media: MediaFileStore
    private var tick = 100L
    private var seq = 0
    private val clock = Clock { tick++ }
    private val ids = IdGenerator { "id${++seq}" }

    private lateinit var homes: HomeRepository
    private lateinit var panels: PanelRepository
    private lateinit var shutoffs: ShutoffRepository
    private lateinit var equipment: EquipmentRepository

    @Before
    fun setUp() {
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        dir = Files.createTempDirectory("repo").toFile()
        media = MediaFileStore(dir)
        homes = HomeRepository(db, Source.HOUSE_MANUAL, clock, ids)
        panels = PanelRepository(db, media, Source.HOUSE_MANUAL, clock, ids)
        shutoffs = ShutoffRepository(db, media, Source.HOUSE_MANUAL, clock, ids)
        equipment = EquipmentRepository(db, media, Source.HOUSE_MANUAL, clock, ids)
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun storeFile(): String {
        val path = media.newPhotoPath()
        media.resolveForWrite(path)!!.writeText("jpg")
        return path
    }

    @Test
    fun home_and_rooms_flow() = runTest {
        assertNull(homes.observeHome().first())
        val home = homes.createHome("  Maple  ")
        assertEquals("Maple", homes.observeHome().first()?.name)
        assertEquals(Source.HOUSE_MANUAL, home.source)
        val room = homes.addRoom(home.id, "Kitchen")
        homes.renameRoom(room.id, "Galley")
        assertEquals(listOf("Galley"), homes.observeRooms(home.id).first().map { it.name })
        homes.deleteRoom(room.id)
        assertTrue(homes.observeRooms(home.id).first().isEmpty())
    }

    @Test
    fun panelPhoto_persistsAndReplacementDeletesOldFile() = runTest {
        val home = homes.createHome("H")
        val panel = panels.createPanel(home.id, "Main", 40, PanelLayout.NA_NUMBERED)
        val first = storeFile()
        panels.setPanelPhoto(panel.id, first)
        assertEquals(first, panels.getPanel(panel.id)?.photoPath)
        assertTrue(media.exists(first))
        val second = storeFile()
        panels.setPanelPhoto(panel.id, second)
        assertFalse(media.exists(first))
        assertTrue(media.exists(second))
        panels.deletePanel(panel.id)
        assertFalse(media.exists(second))
    }

    @Test
    fun deleteBreaker_detachesEndpointsAndLinks() = runTest {
        val home = homes.createHome("H")
        val room = homes.addRoom(home.id, "Den")
        val panel = panels.createPanel(home.id, "Main", 20, PanelLayout.DIN)
        val breaker = panels.saveBreaker(
            Breaker("", panel.id, listOf(1), label = "Den", createdAt = 0, updatedAt = 0, source = Source.IMPORT),
        )
        assertEquals(Source.HOUSE_MANUAL, breaker.source)
        panels.linkRoom(breaker.id, room.id)
        panels.linkRoom(breaker.id, room.id)
        assertEquals(1, panels.observeBreakerRooms(breaker.id).first().size)
        val ep = panels.saveEndpoint(
            Endpoint("", breaker.id, room.id, EndpointKind.OUTLET, "North wall", 0, 0, Source.IMPORT),
        )
        panels.deleteBreaker(breaker.id)
        assertNull(panels.getBreaker(breaker.id))
        assertTrue(panels.observeBreakerRooms(breaker.id).first().isEmpty())
        val remaining = panels.observeEndpointsForRoom(room.id).first()
        assertEquals(listOf(ep.id), remaining.map { it.id })
        assertNull(remaining.single().breakerId)
    }

    @Test
    fun makeTandem_splitsOneSlotIntoTwoBreakers() = runTest {
        val home = homes.createHome("H")
        val panel = panels.createPanel(home.id, "Main", 20, PanelLayout.NA_NUMBERED)
        val b = panels.saveBreaker(Breaker("", panel.id, listOf(4), amps = 15, label = "Hall", createdAt = 0, updatedAt = 0, source = Source.IMPORT))
        val second = panels.makeTandem(b.id)
        assertEquals(1, second?.tandemIndex)
        assertEquals(listOf(4), second?.slots)
        assertEquals(0, panels.getBreaker(b.id)?.tandemIndex)
        assertNull(panels.makeTandem(b.id))
    }

    @Test
    fun setSlotCount_refusesToDropOccupiedSlots() = runTest {
        val home = homes.createHome("H")
        val panel = panels.createPanel(home.id, "Main", 20, PanelLayout.NA_NUMBERED)
        panels.saveBreaker(Breaker("", panel.id, listOf(12, 14), poles = 2, label = "Dryer", createdAt = 0, updatedAt = 0, source = Source.IMPORT))
        assertEquals(com.kurtz.homerecord.core.logic.ShrinkCheck.Blocked(14), panels.setSlotCount(panel.id, 13))
        assertEquals(20, panels.getPanel(panel.id)?.slotCount)
        assertEquals(com.kurtz.homerecord.core.logic.ShrinkCheck.Ok, panels.setSlotCount(panel.id, 14))
        assertEquals(14, panels.getPanel(panel.id)?.slotCount)
    }

    @Test
    fun shutoffPhoto_storedAndCleanedUp() = runTest {
        val home = homes.createHome("H")
        val photo = storeFile()
        val saved = shutoffs.save(
            Shutoff("", home.id, ShutoffKind.WATER_MAIN, null, photo, "Turn clockwise", 0, 0, Source.IMPORT),
        )
        assertEquals(photo, shutoffs.observeShutoffs(home.id).first().single().photoPath)
        val replacement = storeFile()
        shutoffs.save(saved.copy(photoPath = replacement))
        assertFalse(media.exists(photo))
        shutoffs.delete(saved.id)
        assertFalse(media.exists(replacement))
        assertTrue(shutoffs.observeShutoffs(home.id).first().isEmpty())
    }

    @Test
    fun equipment_saveUpdateDelete_cleansLabelPhoto() = runTest {
        val home = homes.createHome("H")
        val label = storeFile()
        val saved = equipment.save(
            Equipment(
                id = "", homeId = home.id, category = EquipmentCategory.FURNACE, labelPhotoPath = label,
                createdAt = 0, updatedAt = 0, source = Source.IMPORT,
            ),
        )
        equipment.save(saved.copy(make = "Carrier"))
        assertEquals("Carrier", equipment.get(saved.id)?.make)
        assertTrue(media.exists(label))
        equipment.delete(saved.id)
        assertFalse(media.exists(label))
        assertNull(equipment.get(saved.id))
    }

    @Test fun quickAddRoomsGetKindsAndCustomRoomsAreOther() = runTest {
        val home = homes.createHomeWithRooms("H", listOf("Kitchen", "Primary Bath", "Mud Room"))
        val kinds = homes.getRooms(home.id).associate { it.name to it.kind }
        assertEquals(RoomKind.KITCHEN, kinds["Kitchen"])
        assertEquals(RoomKind.BATHROOM, kinds["Primary Bath"])
        assertEquals(RoomKind.OTHER, kinds["Mud Room"])
        val mud = homes.getRooms(home.id).first { it.name == "Mud Room" }
        homes.setRoomKind(mud.id, RoomKind.LAUNDRY)
        assertEquals(RoomKind.LAUNDRY, homes.getRoom(mud.id)?.kind)
    }

    @Test fun profileIsStoredAndSecondMainBreakerClearsTheFirst() = runTest {
        val home = homes.createHome("H")
        val profile = PanelProfile(PanelKind.MAIN, 200, "Eaton BR", "BR2040", 40, TandemPolicy.NOT_ALLOWED, true, 2004, null)
        val panel = panels.createPanel(home.id, "Main", 40, PanelLayout.NA_NUMBERED, profile = profile)
        assertEquals(200, panels.getPanel(panel.id)?.ratingAmps)
        assertEquals(TandemPolicy.NOT_ALLOWED, panels.getPanel(panel.id)?.tandemPolicy)

        fun b(slot: Int, main: Boolean) = Breaker("", panel.id, listOf(slot), label = "b$slot", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL, isMain = main)
        val first = panels.saveBreaker(b(1, true))
        val second = panels.saveBreaker(b(2, true))
        assertFalse(panels.getBreaker(first.id)!!.isMain)
        assertTrue(panels.getBreaker(second.id)!!.isMain)
    }

    @Test fun deletingTheFeedBreakerDetachesTheSubPanel() = runTest {
        val home = homes.createHome("H")
        val main = panels.createPanel(home.id, "Main", 40, PanelLayout.NA_NUMBERED)
        val feed = panels.saveBreaker(Breaker("", main.id, listOf(1), label = "Sub", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL))
        val sub = panels.createPanel(home.id, "Sub", 12, PanelLayout.NA_NUMBERED, profile = PanelProfile(PanelKind.SUB, null, null, null, null, TandemPolicy.UNKNOWN, true, null, feed.id))
        assertEquals(feed.id, panels.getPanel(sub.id)?.feedBreakerId)
        panels.deleteBreaker(feed.id)
        assertNull(panels.getPanel(sub.id)?.feedBreakerId)
    }
}
