package com.kurtz.homerecord.housemodel.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: HomeRepository
    private val created = mutableListOf<RoomViewModel>()

    private fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking { block() }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HomeRepository(db)
    }

    @After fun tearDown() {
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(roomId: String, rows: MutableStateFlow<List<ItemRow>>? = null) = RoomViewModel(repo, roomId, rows).also { created += it }

    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)
    private val living = PlanRoom.rectangle("l", "Living room", "LIVING_ROOM", 168, 0, 180, 144)

    private suspend fun mainFloor(): String {
        val homeId = repo.createHome(NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, false))
        val floor = repo.observeFloors(homeId).first { it.isNotEmpty() }.single { it.name == "Main" }
        repo.savePlan(floor.id, listOf(kitchen, living))
        return floor.id
    }

    private fun row(id: String, name: String, roomId: String?, warranty: WarrantyStatus = WarrantyStatus.Unknown) = ItemRow(
        id = id, name = name, make = null, model = null, categoryLabel = "Appliances", categoryOrder = 0,
        subtypeId = "dishwasher", roomId = roomId, roomName = "", warranty = warranty,
    )

    @AcceptanceCriterion("AC-C14")
    @Test fun loadsTheRoomWithFloorAndHome() = test {
        mainFloor()
        val state = withTimeout(10_000) { vm("k").state.first { !it.loading } }
        assertThat(state.room?.name).isEqualTo("Kitchen")
        assertThat(state.floorName).isEqualTo("Main")
        assertThat(state.homeName).isEqualTo("Maple St")
        assertThat(state.canAddItem).isFalse()
        assertThat(state.items).isEmpty()
    }

    // AC-C14: the page follows the plan, so a rename shows and a delete turns it into Room not found
    @Test fun followsRenameAndDelete() = test {
        val floorId = mainFloor()
        val model = vm("k")
        withTimeout(10_000) { model.state.first { it.room?.name == "Kitchen" } }
        repo.savePlan(floorId, listOf(kitchen.copy(name = "Eat-in kitchen"), living))
        withTimeout(10_000) { model.state.first { it.room?.name == "Eat-in kitchen" } }
        repo.savePlan(floorId, listOf(living))
        val gone = withTimeout(10_000) { model.state.first { it.room == null } }
        assertThat(gone.loading).isFalse()
    }

    @Test fun unknownRoomIsNotFound() = test {
        mainFloor()
        val state = withTimeout(10_000) { vm("nope").state.first { !it.loading } }
        assertThat(state.room).isNull()
    }

    @AcceptanceCriterion("AC-H10")
    @Test fun listsOnlyThisRoomsItemsAndUpdates() = test {
        mainFloor()
        val active = WarrantyStatus.Active("2028-03-14")
        val rows = MutableStateFlow(listOf(row("1", "Range", "k"), row("2", "Dishwasher", "k", active), row("3", "Sofa", "l")))
        val model = vm("k", rows)
        val state = withTimeout(10_000) { model.state.first { it.items.size == 2 } }
        assertThat(state.canAddItem).isTrue()
        assertThat(state.items.map { it.name }).containsExactly("Dishwasher", "Range").inOrder()
        assertThat(state.items[0].warranty).isEqualTo(active)
        rows.value = rows.value + row("4", "Toaster", "k")
        assertThat(withTimeout(10_000) { model.state.first { it.items.size == 3 } }.items.map { it.name }).contains("Toaster")
    }
}
