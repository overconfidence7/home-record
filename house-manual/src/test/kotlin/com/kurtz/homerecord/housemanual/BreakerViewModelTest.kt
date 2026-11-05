package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.SlotPlacement
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.panel.BreakerEvent
import com.kurtz.homerecord.housemanual.panel.BreakerUiState
import com.kurtz.homerecord.housemanual.panel.BreakerViewModel
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.panel.BreakerDraft
import com.kurtz.homerecord.housemodel.panel.BreakerSave
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

// AC-C40: HM-Breaker labels a slot's breaker (label, amps, poles, type, rooms and items it feeds), refuses a clash, deletes.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BreakerViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: PanelRepository
    private lateinit var panelId: String
    private val vms = mutableListOf<BreakerViewModel>()
    private val rows = listOf(
        ItemRow("pi", "Main panel", null, null, ItemCategory.ELECTRICAL, "breaker_panel", null, "", WarrantyStatus.Unknown),
        ItemRow("dw", "Dishwasher", "Bosch", null, ItemCategory.APPLIANCES, "dishwasher", "rk", "Kitchen", WarrantyStatus.Unknown),
    )

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = PanelRepository(db)
        runBlocking {
            db.itemDao().upsert(ItemEntity(id = "pi", homeId = "h1", category = "ELECTRICAL", subtype = "breaker_panel", name = "Main panel", createdAt = 1, updatedAt = 1))
            db.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", name = "Main", sortOrder = 0, createdAt = 1, updatedAt = 1))
            db.roomDao().upsert(RoomEntity(id = "rk", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
            panelId = repo.ensurePanel("pi")
        }
    }

    @After fun tearDown() {
        runBlocking { vms.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(slot: Int = 2, breakerId: String? = null, saved: SavedStateHandle = SavedStateHandle()) =
        BreakerViewModel(repo, "pi", slot, breakerId, "h1", flowOf(rows), saved).also { vms += it }

    private fun BreakerViewModel.await(what: (BreakerUiState) -> Boolean) = runBlocking { withTimeout(5_000) { state.first(what) } }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun aNewBreakerInAnOpenSlotSavesItsLabelAmpsTypeRoomAndItem() {
        val vm = vm(slot = 2)
        val s = vm.await { !it.loading && it.rooms.isNotEmpty() }
        assertThat(s.isNew).isTrue()
        assertThat(s.slots).containsExactly(2)
        assertThat(s.items.map { it.id }).containsExactly("dw") // the panel itself is not offered
        vm.onEvent(BreakerEvent.Label("Kitchen counter"))
        vm.onEvent(BreakerEvent.Amps("20"))
        vm.onEvent(BreakerEvent.Type(BreakerType.GFCI))
        vm.onEvent(BreakerEvent.ToggleRoom("rk"))
        vm.onEvent(BreakerEvent.ToggleItem("dw"))
        vm.onEvent(BreakerEvent.Save)
        vm.await { it.done }
        val saved = checkNotNull(runBlocking { repo.observe("pi").first() }).breakers.single()
        assertThat(saved.label).isEqualTo("Kitchen counter")
        assertThat(saved.amps).isEqualTo(20)
        assertThat(saved.type).isEqualTo(BreakerType.GFCI)
        assertThat(saved.slots).containsExactly(2)
        val draft = checkNotNull(runBlocking { repo.draftFor(saved.id) })
        assertThat(draft.roomIds).containsExactly("rk")
        assertThat(draft.itemIds).containsExactly("dw")
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun aTwoPoleOverATakenSlotIsRefusedWithTheSlotNamed() {
        runBlocking { repo.saveBreaker(panelId, BreakerDraft(startSlot = 4, label = "Refrigerator")) }
        val vm = vm(slot = 2)
        vm.await { !it.loading }
        vm.onEvent(BreakerEvent.Poles(2))
        assertThat(vm.state.value.slots).containsExactly(2, 4).inOrder()
        vm.onEvent(BreakerEvent.Save)
        val s = vm.await { it.placement != null }
        assertThat(s.placement).isEqualTo(SlotPlacement.Taken(listOf(4)))
        assertThat(s.done).isFalse()
        vm.onEvent(BreakerEvent.Poles(1))
        assertThat(vm.state.value.placement).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun anExistingBreakerOpensWithItsValuesAndDeleteAsksThenOpensTheSlot() {
        val id = (runBlocking { repo.saveBreaker(panelId, BreakerDraft(startSlot = 5, amps = "15", label = "Porch lights")) } as BreakerSave.Saved).id
        val vm = vm(slot = 5, breakerId = id)
        val s = vm.await { !it.loading }
        assertThat(s.isNew).isFalse()
        assertThat(s.draft.label).isEqualTo("Porch lights")
        assertThat(s.dirty).isFalse()
        vm.onEvent(BreakerEvent.Delete)
        assertThat(vm.state.value.confirmDelete).isTrue()
        vm.onEvent(BreakerEvent.CancelDelete)
        assertThat(vm.state.value.confirmDelete).isFalse()
        vm.onEvent(BreakerEvent.Delete)
        vm.onEvent(BreakerEvent.ConfirmDelete)
        vm.await { it.done }
        assertThat(checkNotNull(runBlocking { repo.observe("pi").first() }).breakers).isEmpty()
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun aBadAmpsValueIsFlaggedAndTheTypedDraftSurvivesProcessDeath() {
        val saved = SavedStateHandle()
        val vm = vm(slot = 3, saved = saved)
        vm.await { !it.loading }
        vm.onEvent(BreakerEvent.Label("Dryer"))
        vm.onEvent(BreakerEvent.Amps("900"))
        vm.onEvent(BreakerEvent.Save)
        assertThat(vm.await { it.ampsError }.done).isFalse()
        val again = vm(slot = 3, saved = saved).await { !it.loading }
        assertThat(again.draft.label).isEqualTo("Dryer")
        assertThat(again.draft.amps).isEqualTo("900")
        assertThat(again.dirty).isTrue()
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun aBreakerDeletedElsewhereShowsAsMissing() {
        assertThat(vm(slot = 1, breakerId = "gone").await { !it.loading }.missing).isTrue()
    }
}
