package com.kurtz.homerecord.fieldlog.changes

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.changeorder.ChangeOrderDraft
import com.kurtz.homerecord.core.changeorder.JobChangeOrders
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.model.ChangeOrderLineItem
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

// AC-F37: create from a job: title, description (keyboard or voice), line items with quantity and price, total in cents, client name
@AcceptanceCriterion("AC-F37")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ChangeOrderFormViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var orders: JobChangeOrders
    private val store = ViewModelStore()
    private var made = 0
    private var lineIds = 0

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        orders = JobChangeOrders(db)
        runBlocking { db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1)) }
    }

    @After fun tearDown() {
        store.clear()
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(id: String? = null, manage: Boolean = true, saved: SavedStateHandle = SavedStateHandle()): ChangeOrderFormViewModel {
        val deps = ChangeOrderFormDeps("j1", id, orders, flowOf(manage), clientName = { "Dana Hale" }, canDictate = true, newLineId = { "n${lineIds++}" })
        return ViewModelProvider.create(store, viewModelFactory { initializer { ChangeOrderFormViewModel(deps, saved) } })["vm${made++}", ChangeOrderFormViewModel::class]
    }

    private fun ChangeOrderFormViewModel.current(until: (ChangeOrderFormUiState) -> Boolean = { true }) = runBlocking {
        withTimeout(20_000) { state.first { !it.loading && until(it) } }
    }

    @Test fun newOrderStartsWithTheJobClientAndOneLineThenSavesWithTheTotal() {
        val vm = vm()
        val s = vm.current()
        assertThat(s.draft.clientName).isEqualTo("Dana Hale")
        assertThat(s.draft.lines).hasSize(1)
        assertThat(s.dirty).isFalse()
        assertThat(s.canDictate).isTrue()
        val first = s.draft.lines.single().id
        vm.onEvent(ChangeOrderFormEvent.Title("Second outlet on the island"))
        vm.onEvent(ChangeOrderFormEvent.Description("Client asked for an outlet"))
        vm.onEvent(ChangeOrderFormEvent.Dictated(" on the end facing the dining room. "))
        vm.onEvent(ChangeOrderFormEvent.LineDescription(first, "Outlet, box and wire"))
        vm.onEvent(ChangeOrderFormEvent.LinePrice(first, "185"))
        vm.onEvent(ChangeOrderFormEvent.AddLine)
        val second = vm.current().draft.lines[1].id
        vm.onEvent(ChangeOrderFormEvent.LineDescription(second, "Labor"))
        vm.onEvent(ChangeOrderFormEvent.LineQty(second, "1.5"))
        vm.onEvent(ChangeOrderFormEvent.LinePrice(second, "$96.00"))
        val typed = vm.current()
        assertThat(typed.draft.description).isEqualTo("Client asked for an outlet on the end facing the dining room.")
        assertThat(typed.draft.totalCents).isEqualTo(18_500 + 14_400)
        vm.onEvent(ChangeOrderFormEvent.Save)
        assertThat(vm.current { it.done }.done).isTrue()
        val saved = runBlocking { orders.observeForJob("j1").first().single() }
        assertThat(saved.title).isEqualTo("Second outlet on the island")
        assertThat(saved.clientName).isEqualTo("Dana Hale")
        assertThat(saved.lines.map { it.description to it.amountCents }).containsExactly("Outlet, box and wire" to 18_500L, "Labor" to 14_400L).inOrder()
        assertThat(saved.totalCents).isEqualTo(32_900)
    }

    @Test fun aBadQuantityOrPriceBlocksSaveAndABlankLineIsLeftOut() {
        val vm = vm()
        val line = vm.current().draft.lines.single().id
        vm.onEvent(ChangeOrderFormEvent.Title("Pantry light"))
        vm.onEvent(ChangeOrderFormEvent.AddLine)
        vm.onEvent(ChangeOrderFormEvent.LineDescription(line, "Fixture"))
        vm.onEvent(ChangeOrderFormEvent.LineQty(line, "0"))
        assertThat(vm.current().canSave).isFalse()
        vm.onEvent(ChangeOrderFormEvent.LineQty(line, "1"))
        vm.onEvent(ChangeOrderFormEvent.LinePrice(line, "12.345"))
        assertThat(vm.current().canSave).isFalse()
        vm.onEvent(ChangeOrderFormEvent.LinePrice(line, "120"))
        assertThat(vm.current().canSave).isTrue()
        vm.onEvent(ChangeOrderFormEvent.Save)
        vm.current { it.done }
        assertThat(runBlocking { orders.observeForJob("j1").first().single().lines }).hasSize(1)
    }

    @Test fun editLoadsTheLinesRemovesOneAndDeleteAsksFirst() {
        val id = checkNotNull(runBlocking {
            orders.add("j1", ChangeOrderDraft("Pantry light", lines = listOf(ChangeOrderLineItem("a", "Fixture", 1_000, 18_500), ChangeOrderLineItem("b", "Labor", 2_500, 9_600))))
        })
        val vm = vm(id)
        val s = vm.current()
        assertThat(s.isNew).isFalse()
        assertThat(s.draft.lines.map { it.qty to it.price }).containsExactly("1" to "185.00", "2.5" to "96.00").inOrder()
        assertThat(s.draft.totalCents).isEqualTo(42_500)
        vm.onEvent(ChangeOrderFormEvent.RemoveLine("b"))
        assertThat(vm.current().draft.totalCents).isEqualTo(18_500)
        vm.onEvent(ChangeOrderFormEvent.Save)
        vm.current { it.done }
        assertThat(runBlocking { orders.get(id)?.totalCents }).isEqualTo(18_500)

        val again = vm(id)
        again.current()
        again.onEvent(ChangeOrderFormEvent.AskDelete)
        assertThat(again.current().confirmDelete).isTrue()
        again.onEvent(ChangeOrderFormEvent.Delete)
        again.current { it.done }
        assertThat(runBlocking { orders.get(id) }).isNull()
    }

    @Test fun typingSurvivesProcessDeath() {
        val saved = SavedStateHandle()
        val vm = vm(saved = saved)
        vm.current()
        vm.onEvent(ChangeOrderFormEvent.Title("Island outlet"))
        val restored = vm(saved = saved).current()
        assertThat(restored.draft.title).isEqualTo("Island outlet")
        assertThat(restored.dirty).isTrue()
    }

    @Test fun aSignedOrderOpensLockedAndCrewCannotOpenTheForm() {
        val id = checkNotNull(runBlocking { orders.add("j1", ChangeOrderDraft("Island outlet")) })
        runBlocking { db.changeOrderDao().upsert(checkNotNull(db.changeOrderDao().get(id)).copy(status = "SIGNED", signedAt = 5, signedBy = "Dana Hale")) }
        val locked = vm(id)
        assertThat(locked.current().locked).isTrue()
        locked.onEvent(ChangeOrderFormEvent.Title("Changed"))
        assertThat(locked.current().draft.title).isEqualTo("Island outlet")
        assertThat(locked.current().canSave).isFalse()

        assertThat(vm(manage = false).current().failed).isTrue()
    }
}
