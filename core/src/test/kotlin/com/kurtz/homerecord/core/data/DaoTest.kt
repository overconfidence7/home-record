package com.kurtz.homerecord.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerRoom
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.Photo
import com.kurtz.homerecord.core.model.PhotoStage
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffKind
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.model.Wall
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DaoTest {
    private lateinit var db: HomeRecordDatabase

    private val home = Home("h1", "Maple House", "1 Main", 1987, "5A", 1, 1, Source.HOUSE_MANUAL)
    private val room = Room("r1", "h1", "Garage", "0", 1, 1, Source.HOUSE_MANUAL)
    private val panel = Panel("p1", "h1", "Main", "r1", null, 40, PanelLayout.NA_NUMBERED, 1, 1, Source.HOUSE_MANUAL)
    private val breaker = Breaker(
        "b1", "p1", listOf(1, 3), 2, 40, BreakerType.GFCI, "Dryer", Confidence.LIKELY, null,
        1, 1, Source.HOUSE_MANUAL,
    )

    @Before
    fun setUp() = runTest {
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        db.homeDao().upsert(home.toEntity())
        db.roomDao().upsert(room.toEntity())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun home_insert_update_delete() = runTest {
        assertEquals(home, db.homeDao().get("h1")?.toDomain())
        db.homeDao().upsert(home.copy(name = "Oak House", updatedAt = 5).toEntity())
        assertEquals("Oak House", db.homeDao().observeFirst().first()?.name)
        assertEquals(1, db.homeDao().getAll().size)
        db.homeDao().delete(db.homeDao().get("h1")!!)
        assertNull(db.homeDao().get("h1"))
    }

    @Test
    fun room_insert_update_delete() = runTest {
        db.roomDao().upsert(room.copy(name = "Shop").toEntity())
        assertEquals("Shop", db.roomDao().observeForHome("h1").first().single().name)
        db.roomDao().delete(db.roomDao().get("r1")!!)
        assertNull(db.roomDao().get("r1"))
    }

    @Test
    fun wall_insert_update_delete() = runTest {
        val wall = Wall("w1", "r1", "N", 1, 1, Source.FIELD_LOG)
        db.wallDao().upsert(wall.toEntity())
        assertEquals(wall, db.wallDao().get("w1")?.toDomain())
        db.wallDao().upsert(wall.copy(label = "Closet wall").toEntity())
        assertEquals("Closet wall", db.wallDao().observeForRoom("r1").first().single().label)
        db.wallDao().delete(db.wallDao().get("w1")!!)
        assertNull(db.wallDao().get("w1"))
    }

    @Test
    fun panel_insert_update_delete() = runTest {
        db.panelDao().upsert(panel.toEntity())
        assertEquals(panel, db.panelDao().get("p1")?.toDomain())
        db.panelDao().upsert(panel.copy(slotCount = 30, layout = PanelLayout.DIN).toEntity())
        val saved = db.panelDao().observeForHome("h1").first().single()
        assertEquals(30, saved.slotCount)
        assertEquals(PanelLayout.DIN, saved.layout)
        db.panelDao().delete(saved)
        assertNull(db.panelDao().get("p1"))
    }

    @Test
    fun breaker_insert_update_delete_keeps_slot_list() = runTest {
        db.panelDao().upsert(panel.toEntity())
        db.breakerDao().upsert(breaker.toEntity())
        assertEquals(breaker, db.breakerDao().get("b1")?.toDomain())
        db.breakerDao().upsert(breaker.copy(label = "Range", slots = listOf(5, 7)).toEntity())
        val saved = db.breakerDao().observeForPanel("p1").first().single()
        assertEquals(listOf(5, 7), saved.slots)
        db.breakerDao().delete(saved)
        assertNull(db.breakerDao().get("b1"))
    }

    @Test
    fun tandem_breakers_can_share_a_slot() = runTest {
        db.panelDao().upsert(panel.toEntity())
        db.breakerDao().upsert(breaker.copy(id = "t0", slots = listOf(9), poles = 1, tandemIndex = 0).toEntity())
        db.breakerDao().upsert(breaker.copy(id = "t1", slots = listOf(9), poles = 1, tandemIndex = 1).toEntity())
        assertEquals(listOf(0, 1), db.breakerDao().getAll().map { it.tandemIndex })
    }

    @Test
    fun breakerRoom_insert_delete_and_unique_pair() = runTest {
        db.panelDao().upsert(panel.toEntity())
        db.breakerDao().upsert(breaker.toEntity())
        val link = BreakerRoom("br1", "b1", "r1", 1, 1, Source.HOUSE_MANUAL)
        db.breakerRoomDao().upsert(link.toEntity())
        assertEquals(link, db.breakerRoomDao().observeForBreaker("b1").first().single().toDomain())
        assertEquals(1, db.breakerRoomDao().observeForRoom("r1").first().size)
        db.breakerRoomDao().deleteForBreaker("b1")
        assertEquals(0, db.breakerRoomDao().getAll().size)
    }

    @Test
    fun endpoint_insert_update_delete() = runTest {
        val ep = Endpoint("e1", null, "r1", EndpointKind.OUTLET, "Freezer", 1, 1, Source.HOUSE_MANUAL)
        db.endpointDao().upsert(ep.toEntity())
        assertEquals(ep, db.endpointDao().get("e1")?.toDomain())
        db.endpointDao().upsert(ep.copy(label = "Chest freezer").toEntity())
        assertEquals("Chest freezer", db.endpointDao().observeForRoom("r1").first().single().label)
        db.endpointDao().delete(db.endpointDao().get("e1")!!)
        assertNull(db.endpointDao().get("e1"))
    }

    @Test
    fun deleting_breaker_detaches_endpoints_and_removes_room_links() = runTest {
        db.panelDao().upsert(panel.toEntity())
        db.breakerDao().upsert(breaker.toEntity())
        db.endpointDao().upsert(
            Endpoint("e1", "b1", "r1", EndpointKind.OUTLET, "Freezer", 1, 1, Source.HOUSE_MANUAL).toEntity(),
        )
        db.breakerRoomDao().upsert(BreakerRoom("br1", "b1", "r1", 1, 1, Source.HOUSE_MANUAL).toEntity())

        db.breakerDao().delete(db.breakerDao().get("b1")!!)

        val ep = db.endpointDao().get("e1")
        assertNotNull(ep)
        assertNull(ep?.breakerId)
        assertEquals(0, db.breakerRoomDao().getAll().size)
    }

    @Test
    fun shutoff_insert_update_delete() = runTest {
        val s = Shutoff("s1", "h1", ShutoffKind.WATER_MAIN, "r1", null, "Turn clockwise", 1, 1, Source.HOUSE_MANUAL)
        db.shutoffDao().upsert(s.toEntity())
        assertEquals(s, db.shutoffDao().get("s1")?.toDomain())
        db.shutoffDao().upsert(s.copy(instructions = "Quarter turn").toEntity())
        assertEquals("Quarter turn", db.shutoffDao().observeForHome("h1").first().single().instructions)
        db.shutoffDao().delete(db.shutoffDao().get("s1")!!)
        assertNull(db.shutoffDao().get("s1"))
    }

    @Test
    fun equipment_insert_update_delete() = runTest {
        val eq = Equipment(
            "q1", "h1", "r1", EquipmentCategory.WATER_HEATER, "Rheem", "XE50", "SN1",
            "2019-03-01", "2019-04-10", "2029-04-10", null, "Anode due", 1, 1, Source.HOUSE_MANUAL,
        )
        db.equipmentDao().upsert(eq.toEntity())
        assertEquals(eq, db.equipmentDao().get("q1")?.toDomain())
        db.equipmentDao().upsert(eq.copy(serial = "SN2").toEntity())
        assertEquals("SN2", db.equipmentDao().observeForHome("h1").first().single().serial)
        db.equipmentDao().delete(db.equipmentDao().get("q1")!!)
        assertNull(db.equipmentDao().get("q1"))
    }

    @Test
    fun photo_insert_update_delete_and_room_delete_nulls_reference() = runTest {
        val photo = Photo("ph1", "h1", "r1", null, PhotoStage.BEHIND_WALL, 100, "Pipe", "media/a.jpg", 1, 1, Source.FIELD_LOG)
        db.photoDao().upsert(photo.toEntity())
        assertEquals(photo, db.photoDao().get("ph1")?.toDomain())
        db.photoDao().upsert(photo.copy(caption = "Pex").toEntity())
        assertEquals("Pex", db.photoDao().observeForHome("h1").first().single().caption)
        db.roomDao().delete(db.roomDao().get("r1")!!)
        assertNull(db.photoDao().get("ph1")?.roomId)
        db.photoDao().delete(db.photoDao().get("ph1")!!)
        assertNull(db.photoDao().get("ph1"))
    }

    @Test
    fun deleting_home_cascades_to_its_records() = runTest {
        db.panelDao().upsert(panel.toEntity())
        db.homeDao().delete(db.homeDao().get("h1")!!)
        assertEquals(0, db.roomDao().getAll().size)
        assertEquals(0, db.panelDao().getAll().size)
    }

    @Test
    fun slot_converter_round_trips() {
        val c = Converters()
        assertEquals(listOf(1, 3), c.stringToSlots(c.slotsToString(listOf(1, 3))))
        assertEquals(emptyList<Int>(), c.stringToSlots(""))
    }
}
