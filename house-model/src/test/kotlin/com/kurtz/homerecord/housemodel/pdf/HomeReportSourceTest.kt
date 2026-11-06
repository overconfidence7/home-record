package com.kurtz.homerecord.housemodel.pdf

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.WarrantyEntity
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeReportSourceTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var source: HomeReportSource

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        homes = HomeRepository(db, context.resources::getString)
        val items = ItemRepository(db, context.resources::getString, today = { LocalDate.parse("2026-11-06") })
        val legend = PanelLegendExporter(context).words
        source = HomeReportSource(homes, items, PanelRepository(db), ReportSourceWords("Exterior", "Whole house", "No room", { it.name }), legend)
    }

    @After fun tearDown() = db.close()

    private data class RoomRef(val id: String, val name: String)

    // AC-H13: every floor and every room's items with warranty status, read from Room.
    @Test fun loadsEveryFloorEveryRoomAndEachItemsWarranty() = runTest {
        val homeId = homes.createHome(NewHome("Maple Street house", "14 Maple St", HouseType.TWO_STORY, Foundation.BASEMENT, attachedGarage = false))
        val floors = homes.observeFloors(homeId).first()
        homes.savePlan(floors.first().id, listOf(PlanRoom("kitchen", "Kitchen", points = listOf(Pt(0, 0), Pt(168, 0), Pt(168, 150), Pt(0, 150)))))
        val room = RoomRef(checkNotNull(homes.loadPlan(floors.first().id).placed.firstOrNull()).id, "Kitchen")
        db.itemDao().upsert(ItemEntity(id = "wh", homeId = homeId, roomId = room.id, category = "WATER", subtype = "water_heater", name = "Water heater", make = "Rheem", model = "XE50", serial = "Q1", inServiceOn = "2021-05-03", createdAt = 1, updatedAt = 1))
        db.warrantyDao().upsert(WarrantyEntity(id = "w1", itemId = "wh", endsOn = "2031-05-03", createdAt = 1, updatedAt = 1))
        db.itemDao().upsert(ItemEntity(id = "sd", homeId = homeId, roomId = PseudoRooms.WHOLE_HOUSE, category = "SAFETY", subtype = "smoke_detector", name = "Smoke detectors", createdAt = 1, updatedAt = 1))
        db.itemDao().upsert(ItemEntity(id = "wm", homeId = homeId, roomId = room.id, category = "SHUTOFFS", subtype = "water_main", name = "Water main shutoff", notes = "Quarter turn.", createdAt = 1, updatedAt = 1))

        val data = checkNotNull(source.load(homeId))

        assertThat(data.homeName).isEqualTo("Maple Street house")
        assertThat(data.houseType).isEqualTo("TWO_STORY")
        assertThat(data.floors.map { it.name }).isEqualTo(floors.map { it.name })
        val placedRooms = floors.sumOf { homes.loadPlan(it.id).let { p -> p.placed.size + p.unplaced.size } }
        assertThat(data.rooms.count { it.floorName.isNotEmpty() }).isEqualTo(placedRooms)
        val heater = data.rooms.first { it.name == room.name }.items.first { it.name == "Water heater" }
        assertThat(heater.serial).isEqualTo("Q1")
        assertThat(heater.warranty).isEqualTo(WarrantyStatus.Active("2031-05-03"))
        assertThat(data.rooms.first { it.name == "Whole house" }.items.map { it.name }).containsExactly("Smoke detectors")
        assertThat(data.rooms.none { it.name == "Exterior" }).isTrue()
        assertThat(data.shutoffs.map { it.name to it.instructions }).containsExactly("Water main shutoff" to "Quarter turn.")
    }

    @Test fun anotherHomesIdLoadsNothing() = runTest {
        homes.createHome(NewHome("Maple Street house", null, HouseType.RANCHER, Foundation.SLAB, attachedGarage = false))
        assertThat(source.load("someone-else")).isNull()
    }
}
