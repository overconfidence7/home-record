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
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.model.ChangeOrderLineItem
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
// AC-F38: Sign on phone saves first; a Signed order shows who signed and when, read only, and duplicates to change
// AC-F39: Share PDF makes the order's PDF (saving a Draft's typing first) and opens the share sheet
@AcceptanceCriterion("AC-F37", "AC-F38", "AC-F39")
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

    private val jobPhotos = MutableStateFlow<List<CoPhoto>>(emptyList())

    /** Records what was made and shared; [fail] makes no PDF. */
    private class FakeSharer(private val orders: JobChangeOrders) : ChangeOrderSharer {
        var fail = false
        val made = mutableListOf<String>()
        val shared = mutableListOf<java.io.File>()
        /** The title saved at the moment the PDF was made. */
        val titles = mutableListOf<String>()
        override suspend fun make(changeOrderId: String): java.io.File? {
            made += changeOrderId
            titles += orders.get(changeOrderId)?.title.orEmpty()
            return if (fail) null else java.io.File("Change order $changeOrderId.pdf")
        }
        override fun share(pdf: java.io.File): Boolean {
            shared += pdf
            return true
        }
    }
    private val sharer by lazy { FakeSharer(orders) }
    private var clock = 1_000L

    private fun vm(id: String? = null, manage: Boolean = true, saved: SavedStateHandle = SavedStateHandle()): ChangeOrderFormViewModel {
        val deps = ChangeOrderFormDeps(
            "j1", id, orders, flowOf(manage), clientName = { "Dana Hale" }, canDictate = true, newLineId = { "n${lineIds++}" },
            photos = jobPhotos, sharer = sharer, now = { clock },
        )
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

    // T056b: job photos picked in the sheet, a photo taken from the form joins once, and the order saves them in order.
    @Test fun photosArePickedTakenAndSavedInOrder() {
        fun photo(id: String, at: Long) = CoPhoto(id, "/m/$id.jpg", at)
        runBlocking {
            for (id in listOf("p1", "p2", "p3", "p4")) {
                db.photoDao().upsert(PhotoEntity(id = id, homeId = "h1", jobId = "j1", path = "/m/$id.jpg", takenAt = 1, createdAt = 1, updatedAt = 1))
            }
        }
        jobPhotos.value = listOf(photo("p2", 600), photo("p1", 500))
        val saved = SavedStateHandle()
        val vm = vm(saved = saved)
        vm.current()
        vm.onEvent(ChangeOrderFormEvent.Title("Island outlet"))
        vm.onEvent(ChangeOrderFormEvent.OpenPhotos)
        assertThat(vm.current().pickingPhotos).isTrue()
        vm.onEvent(ChangeOrderFormEvent.TogglePhoto("p1"))
        vm.onEvent(ChangeOrderFormEvent.TogglePhoto("p2"))
        vm.onEvent(ChangeOrderFormEvent.TogglePhoto("p1"))
        vm.onEvent(ChangeOrderFormEvent.TogglePhoto("p1"))
        assertThat(vm.current().photos.map { it.id }).containsExactly("p2", "p1").inOrder()

        clock = 2_000
        vm.onEvent(ChangeOrderFormEvent.TakePhoto)
        assertThat(vm.current().pickingPhotos).isFalse()
        jobPhotos.value = listOf(photo("p3", 2_100)) + jobPhotos.value
        assertThat(vm.current().draft.photoIds).containsExactly("p2", "p1", "p3").inOrder()
        // Taken off again, it stays off when the list changes; a later shot from the same trip still joins.
        vm.onEvent(ChangeOrderFormEvent.RemovePhoto("p3"))
        jobPhotos.value = listOf(photo("p4", 2_200)) + jobPhotos.value
        assertThat(vm.current().draft.photoIds).containsExactly("p2", "p1", "p4").inOrder()
        // The picks survive process death with the rest of the typing.
        assertThat(vm(saved = saved).current().draft.photoIds).containsExactly("p2", "p1", "p4").inOrder()

        vm.onEvent(ChangeOrderFormEvent.Save)
        vm.current { it.done }
        assertThat(runBlocking { orders.observeForJob("j1").first().single().photoIds }).containsExactly("p2", "p1", "p4").inOrder()
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

    // AC-F38: Sign on phone saves what is typed (a new order is added first) and hands its id to the pad.
    @Test fun signOnPhoneSavesThenOpensThePad() {
        val m = vm()
        m.current()
        assertThat(m.state.value.canSign).isFalse()
        m.onEvent(ChangeOrderFormEvent.Title("Island outlet"))
        m.onEvent(ChangeOrderFormEvent.LineDescription("n0", "Outlet, box and wire"))
        m.onEvent(ChangeOrderFormEvent.LinePrice("n0", "185"))
        assertThat(m.state.value.canSign).isTrue()
        m.onEvent(ChangeOrderFormEvent.SignOnPhone)
        val id = checkNotNull(m.current { it.signId != null }.signId)
        val saved = runBlocking { checkNotNull(orders.get(id)) }
        assertThat(saved.title).isEqualTo("Island outlet")
        assertThat(saved.totalCents).isEqualTo(18_500)
    }

    // AC-F38: a Signed order shows the signer, the time and the image, and Duplicate opens a new Draft copy.
    @Test fun signedOrderShowsTheSignatureAndDuplicates() {
        val id = runBlocking {
            val id = checkNotNull(orders.add("j1", ChangeOrderDraft("Island outlet", lines = listOf(ChangeOrderLineItem("l1", "Outlet", 1_000, 18_500)))))
            check(orders.sign(id, "Dana Hale", "/m/sig.png"))
            id
        }
        val m = vm(id)
        val st = m.current()
        assertThat(st.locked).isTrue()
        assertThat(st.signature?.signedBy).isEqualTo("Dana Hale")
        assertThat(st.signature?.imagePath).isEqualTo("/m/sig.png")
        m.onEvent(ChangeOrderFormEvent.SignOnPhone)
        assertThat(m.state.value.signId).isNull()
        m.onEvent(ChangeOrderFormEvent.Duplicate)
        val copyId = checkNotNull(m.current { it.duplicateId != null }.duplicateId)
        val copy = runBlocking { checkNotNull(orders.get(copyId)) }
        assertThat(copy.title).isEqualTo("Island outlet")
        assertThat(copy.signedBy).isNull()
    }

    // AC-F39: a Signed order shares its PDF; a failed PDF says so and Share works again.
    @Test fun shareMakesThePdfAndOpensTheShareSheetAndCanBeRetried() {
        val id = runBlocking {
            val id = checkNotNull(orders.add("j1", ChangeOrderDraft("Island outlet", lines = listOf(ChangeOrderLineItem("l1", "Outlet", 1_000, 18_500)))))
            check(orders.sign(id, "Dana Hale", "/m/sig.png"))
            id
        }
        val m = vm(id)
        assertThat(m.current().canShare).isTrue()
        sharer.fail = true
        m.onEvent(ChangeOrderFormEvent.Share)
        assertThat(m.current { !it.sharing }.shareFailed).isTrue()
        assertThat(sharer.shared).isEmpty()
        sharer.fail = false
        m.onEvent(ChangeOrderFormEvent.Share)
        val st = m.current { !it.sharing && !it.shareFailed }
        assertThat(st.done).isFalse()
        assertThat(sharer.made).containsExactly(id, id)
        assertThat(sharer.shared.map { it.name }).containsExactly("Change order $id.pdf")
    }

    // AC-F39: a Draft with typed changes is saved before its PDF is made, so the PDF shows what the form shows; a new
    // order has nothing saved to share.
    @Test fun shareSavesADraftsTypingFirstAndANewOrderCannotShare() {
        assertThat(vm().current().canShare).isFalse()
        val id = checkNotNull(runBlocking { orders.add("j1", ChangeOrderDraft("Island outlet")) })
        val m = vm(id)
        m.current()
        m.onEvent(ChangeOrderFormEvent.Title("Island outlet and USB"))
        assertThat(m.state.value.dirty).isTrue()
        m.onEvent(ChangeOrderFormEvent.Share)
        val st = m.current { !it.sharing }
        assertThat(sharer.titles).containsExactly("Island outlet and USB")
        assertThat(st.dirty).isFalse()
        assertThat(st.shareFailed).isFalse()
        m.onEvent(ChangeOrderFormEvent.Title(""))
        assertThat(m.state.value.canShare).isFalse()
    }
}
