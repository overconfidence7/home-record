package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.ui.ItemEditEvent
import com.kurtz.homerecord.housemodel.ui.ItemEditViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemEditViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var items: ItemRepository
    private lateinit var homeId: String

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        homes = HomeRepository(db)
        items = ItemRepository(db)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private suspend fun home() = homes.createHome(NewHome("Maple St", null, HouseType.TWO_STORY, Foundation.BASEMENT, true)).also { homeId = it }

    @AcceptanceCriterion("AC-C30")
    @Test fun saveNeedsTypeRoomAndNameThenStoresTheItem() = runTest {
        home()
        val vm = ItemEditViewModel(items, homes, homeId, null, SavedStateHandle())
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.Save)
        assertThat(vm.state.value.showErrors).isTrue()
        assertThat(vm.state.value.savedItemId).isNull()

        vm.onEvent(ItemEditEvent.PickSubtype("furnace"))
        // Picking a type names a blank item after it.
        assertThat(vm.state.value.draft.name).isEqualTo("Furnace")
        vm.onEvent(ItemEditEvent.PickRoom(PseudoRooms.WHOLE_HOUSE))
        vm.onEvent(ItemEditEvent.Save)
        val id = vm.state.first { it.savedItemId != null }.savedItemId
        assertThat(id).isNotNull()
        assertThat(items.get(id!!)?.subtype).isEqualTo("furnace")
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun roomPickerListsEveryRoomOfTheHome() = runTest {
        home()
        val vm = ItemEditViewModel(items, homes, homeId, null, SavedStateHandle())
        vm.state.first { !it.loading }
        assertThat(vm.state.value.rooms.map { it.name }).contains("Garage")
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun draftSurvivesProcessDeath() = runTest {
        home()
        val handle = SavedStateHandle()
        val vm = ItemEditViewModel(items, homes, homeId, null, handle)
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.PickSubtype("dishwasher"))
        vm.onEvent(ItemEditEvent.Change(vm.state.value.draft.copy(make = "Bosch")))
        val revived = ItemEditViewModel(items, homes, homeId, null, SavedStateHandle(mapOf("draft" to handle.get<ArrayList<String>>("draft"))))
        revived.state.first { !it.loading }
        assertThat(revived.state.value.draft.make).isEqualTo("Bosch")
        assertThat(revived.state.value.draft.subtypeId).isEqualTo("dishwasher")
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun editingAnItemLoadsItsDraftAndMissingItemsAreReported() = runTest {
        home()
        val id = items.save(homeId, com.kurtz.homerecord.housemodel.items.ItemDraft(subtypeId = "range", roomId = PseudoRooms.EXTERIOR, name = "Range"))!!
        val vm = ItemEditViewModel(items, homes, homeId, id, SavedStateHandle())
        vm.state.first { !it.loading }
        assertThat(vm.state.value.draft.name).isEqualTo("Range")
        assertThat(vm.state.value.dirty).isFalse()
        val gone = ItemEditViewModel(items, homes, homeId, "nope", SavedStateHandle())
        gone.state.first { !it.loading }
        assertThat(gone.state.value.missing).isTrue()
    }
}
