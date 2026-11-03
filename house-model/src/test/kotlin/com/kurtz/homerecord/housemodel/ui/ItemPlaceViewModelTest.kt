package com.kurtz.homerecord.housemodel.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.housemodel.items.ItemPlace
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C30 position: Place on plan opens on the item's floor, a tap saves the spot, Remove takes it off the plan.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemPlaceViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var items: ItemRepository
    private val created = mutableListOf<ItemPlaceViewModel>()
    private val collectors = mutableListOf<Job>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        homes = HomeRepository(db, context.resources::getString)
        items = ItemRepository(db, context.resources::getString)
    }

    @After fun tearDown() {
        runBlocking {
            collectors.forEach { it.cancelAndJoin() }
            created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        }
        db.close()
        Dispatchers.resetMain()
    }

    private fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(10_000) { block() } }

    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)
    private val living = PlanRoom.rectangle("l", "Living room", "LIVING_ROOM", 168, 0, 180, 144)
    private val utility = PlanRoom.rectangle("u", "Utility room", "UTILITY", 0, 0, 144, 144)

    private data class Home(val id: String, val main: String, val basement: String)

    private suspend fun home(): Home {
        val homeId = homes.createHome(NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, false))
        val floors = homes.observeFloors(homeId).first { it.isNotEmpty() }
        val main = floors.single { it.name == "Main" }.id
        val basement = floors.single { it.name == "Basement" }.id
        homes.savePlan(main, listOf(kitchen, living))
        homes.savePlan(basement, listOf(utility))
        return Home(homeId, main, basement)
    }

    private fun vm(h: Home, itemId: String, saved: SavedStateHandle = SavedStateHandle()): ItemPlaceViewModel {
        val vm = ItemPlaceViewModel(homes, items, h.id, itemId, saved).also { created += it }
        // Collected in the ViewModel's own scope, so the test block can finish; tearDown cancels it.
        collectors += vm.viewModelScope.launch { vm.state.collect {} }
        return vm
    }

    private suspend fun ItemPlaceViewModel.settled(check: (ItemPlaceUiState) -> Boolean) = state.first { !it.loading && check(it) }

    @AcceptanceCriterion("AC-C30")
    @Test fun opensOnTheFloorOfTheItemsRoomAndATapSavesTheSpot() = test {
        val h = home()
        val id = checkNotNull(items.save(h.id, ItemDraft(subtypeId = "water_heater", roomId = "u", name = "Water heater")))
        val vm = vm(h, id)
        val open = vm.settled { it.rooms.isNotEmpty() }
        assertThat(open.floorId).isEqualTo(h.basement)
        assertThat(open.itemName).isEqualTo("Water heater")
        assertThat(open.spot).isNull()
        assertThat(open.marks).isEmpty()

        vm.onEvent(ItemPlaceEvent.Place(40, 60))
        val placed = vm.settled { it.spot != null }
        assertThat(placed.spot).isEqualTo(ItemPlace("u", 40, 60))
        assertThat(placed.spotRoomName).isEqualTo("Utility room")
        assertThat(placed.marks.single().points.single().x).isEqualTo(40f)
        assertThat(items.get(id)?.planX).isEqualTo(40)
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun aTapInAnotherRoomMovesTheItemThereAndRemoveKeepsTheRoom() = test {
        val h = home()
        val id = checkNotNull(items.save(h.id, ItemDraft(subtypeId = "refrigerator", roomId = "k", name = "Refrigerator")))
        val vm = vm(h, id)
        vm.settled { it.floorId == h.main && it.rooms.isNotEmpty() }
        vm.onEvent(ItemPlaceEvent.Place(200, 40))
        assertThat(vm.settled { it.spot != null }.spotRoomName).isEqualTo("Living room")
        assertThat(items.get(id)?.roomId).isEqualTo("l")

        vm.onEvent(ItemPlaceEvent.Remove)
        val removed = vm.settled { it.spot == null }
        assertThat(removed.marks).isEmpty()
        assertThat(items.get(id)?.roomId).isEqualTo("l")
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun anotherFloorHidesTheMarkerAndThePickSurvivesProcessDeath() = test {
        val h = home()
        val id = checkNotNull(items.save(h.id, ItemDraft(subtypeId = "refrigerator", roomId = "k", name = "Refrigerator")))
        items.setPlanSpot(id, ItemPlace("k", 30, 30))
        val saved = SavedStateHandle()
        val vm = vm(h, id, saved)
        assertThat(vm.settled { it.rooms.isNotEmpty() }.marks).hasSize(1)
        vm.onEvent(ItemPlaceEvent.PickFloor(h.basement))
        val other = vm.settled { it.floorId == h.basement && it.rooms.isNotEmpty() }
        assertThat(other.spot).isEqualTo(ItemPlace("k", 30, 30))
        assertThat(other.spotHere).isNull()
        assertThat(other.marks).isEmpty()

        val again = vm(h, id, SavedStateHandle(mapOf("item_place_floor" to saved.get<String>("item_place_floor"))))
        assertThat(again.settled { it.rooms.isNotEmpty() }.floorId).isEqualTo(h.basement)
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun aDeletedItemShowsMissing() = test {
        val h = home()
        val id = checkNotNull(items.save(h.id, ItemDraft(subtypeId = "refrigerator", roomId = "k", name = "Refrigerator")))
        val vm = vm(h, id)
        vm.settled { it.rooms.isNotEmpty() }
        items.delete(id)
        assertThat(vm.settled { it.missing }.spot).isNull()
    }
}
