package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.housemanual.house.HouseDialog
import com.kurtz.homerecord.housemanual.house.HouseEvent
import com.kurtz.homerecord.housemanual.house.HouseViewModel
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import com.kurtz.homerecord.housemodel.blueprint.LayerSet
import com.kurtz.homerecord.housemodel.blueprint.LayerStore
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

private class MemoryLayerStore : LayerStore {
    val map = mutableMapOf<String, LayerSet>()
    override fun read(homeId: String) = map[homeId] ?: LayerSet()
    override fun write(homeId: String, layers: LayerSet) { map[homeId] = layers }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HouseViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: HomeRepository
    private val store = MemoryLayerStore()

    private fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking { block() }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HomeRepository(db)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun setUpHome(): Pair<String, String> {
        val homeId = repo.createHome(NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, false))
        val floorId = repo.observeFloors(homeId).first { it.isNotEmpty() }.first().id
        repo.savePlan(floorId, listOf(PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 144, 120), PlanRoom.rectangle("l", "Living", "LIVING_ROOM", 144, 0, 180, 120)))
        return homeId to floorId
    }

    private suspend fun HouseViewModel.untilRooms(count: Int) = withTimeout(10_000) { state.first { it.rooms.size == count } }

    @Test fun showsTheFloorsRooms() = test {
        val (homeId, _) = setUpHome()
        val vm = HouseViewModel(repo, homeId, store, SavedStateHandle(), "Maple St", "Colonial")
        val state = vm.untilRooms(2)
        assertThat(state.rooms.map { it.name }).containsExactly("Kitchen", "Living")
    }

    @Test fun layerTogglesPersistPerHome() = test {
        val (homeId, _) = setUpHome()
        val vm = HouseViewModel(repo, homeId, store, SavedStateHandle(), "Maple St", "Colonial")
        vm.onEvent(HouseEvent.ToggleLayer(BlueprintLayer.PLUMBING))
        assertThat(store.read(homeId)).isEqualTo(LayerSet(setOf(BlueprintLayer.PLUMBING)))
        val again = HouseViewModel(repo, homeId, store, SavedStateHandle(), "Maple St", "Colonial")
        assertThat(again.state.value.layers).isEqualTo(LayerSet(setOf(BlueprintLayer.PLUMBING)))
    }

    @Test fun renameFromTheLongPressMenuSavesTheRoom() = test {
        val (homeId, floorId) = setUpHome()
        val vm = HouseViewModel(repo, homeId, store, SavedStateHandle(), "Maple St", "Colonial")
        val room = vm.untilRooms(2).rooms.first { it.id == "k" }
        vm.onEvent(HouseEvent.LongPressRoom(room))
        assertThat(vm.state.value.dialog).isEqualTo(HouseDialog.Menu(room))
        vm.onEvent(HouseEvent.StartRename(room))
        vm.onEvent(HouseEvent.Rename("k", "  Eat-in kitchen "))
        withTimeout(10_000) { vm.state.first { s -> s.rooms.any { it.name == "Eat-in kitchen" } } }
        assertThat(vm.state.value.dialog).isNull()
        assertThat(repo.loadPlan(floorId).placed.map { it.name }).contains("Eat-in kitchen")
    }

    @Test fun deleteRemovesOnlyThatRoom() = test {
        val (homeId, floorId) = setUpHome()
        val vm = HouseViewModel(repo, homeId, store, SavedStateHandle(), "Maple St", "Colonial")
        val room = vm.untilRooms(2).rooms.first { it.id == "l" }
        vm.onEvent(HouseEvent.StartDelete(room))
        vm.onEvent(HouseEvent.ConfirmDelete("l"))
        vm.untilRooms(1)
        assertThat(repo.loadPlan(floorId).placed.map { it.id }).containsExactly("k")
    }
}
