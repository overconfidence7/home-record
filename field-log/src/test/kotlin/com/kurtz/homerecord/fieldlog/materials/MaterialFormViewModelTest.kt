package com.kurtz.homerecord.fieldlog.materials

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.material.JobMaterials
import com.kurtz.homerecord.core.material.MaterialCategory
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
import java.time.LocalDate

// AC-F44: receipt capture → job, vendor, amount (OCR suggestion from the receipt total when found), category, note
@AcceptanceCriterion("AC-F44")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MaterialFormViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var materials: JobMaterials
    private val store = ViewModelStore()
    private var made = 0
    private val jobs = listOf(JobOption("j1", "Hale kitchen"))
    private val read = mutableListOf<String>()
    private var receipt: ScannedReceipt? = ScannedReceipt("/media/r1.jpg", 12_371)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        materials = JobMaterials(db)
        runBlocking { db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1)) }
    }

    @After fun tearDown() {
        store.clear()
        db.close()
        Dispatchers.resetMain()
    }

    private var fetched: String? = null
    private val fetches = mutableListOf<String>()

    private fun vm(id: String? = null, saved: SavedStateHandle = SavedStateHandle()): MaterialFormViewModel {
        val deps = MaterialFormDeps(
            id, materials, flowOf(jobs), receipts = { uri -> read += uri; receipt }, today = { LocalDate.of(2026, 11, 4) },
            fetchReceipt = { mid -> fetches += mid; fetched },
        )
        return ViewModelProvider.create(store, viewModelFactory { initializer { MaterialFormViewModel(deps, saved) } })["vm${made++}", MaterialFormViewModel::class]
    }

    private fun MaterialFormViewModel.current(until: (MaterialFormUiState) -> Boolean = { true }) = runBlocking {
        withTimeout(20_000) { state.first { !it.loading && until(it) } }
    }

    @Test fun newReceiptStartsTodayOnTheOnlyJobAndNeedsAnAmount() {
        val vm = vm()
        val s = vm.current()
        assertThat(s.draft.jobId).isEqualTo("j1")
        assertThat(s.draft.date).isEqualTo("2026-11-04")
        assertThat(s.canScan).isTrue()
        vm.onEvent(MaterialFormEvent.Save)
        assertThat(vm.current().showErrors).isTrue()
        assertThat(vm.current().done).isFalse()
    }

    @Test fun scanKeepsTheReceiptAndOffersItsTotal() {
        val vm = vm()
        vm.current()
        vm.onEvent(MaterialFormEvent.Scan)
        assertThat(vm.current().scanning).isTrue()
        vm.onEvent(MaterialFormEvent.Scanned("content://scanner/page1.jpg"))
        val s = vm.current { it.draft.receiptPath != null }
        assertThat(read).containsExactly("content://scanner/page1.jpg")
        assertThat(s.scanning).isFalse()
        assertThat(s.suggestedCents).isEqualTo(12_371L)
        assertThat(s.draft.amount).isEmpty()
        vm.onEvent(MaterialFormEvent.UseTotal)
        assertThat(vm.current().draft.amount).isEqualTo("123.71")
    }

    @Test fun aScanWithNoTotalSaysSoAndAFailedScanShowsTheError() {
        receipt = ScannedReceipt("/media/r2.jpg", null)
        val vm = vm()
        vm.current()
        vm.onEvent(MaterialFormEvent.Scan)
        vm.onEvent(MaterialFormEvent.Scanned("content://scanner/page1.jpg"))
        assertThat(vm.current { it.draft.receiptPath != null }.noTotalFound).isTrue()
        receipt = null
        vm.onEvent(MaterialFormEvent.Scan)
        vm.onEvent(MaterialFormEvent.Scanned("content://scanner/page2.jpg"))
        assertThat(vm.current { it.scanFailed }.draft.receiptPath).isEqualTo("/media/r2.jpg")
        vm.onEvent(MaterialFormEvent.Scan)
        vm.onEvent(MaterialFormEvent.ScanCancelled)
        assertThat(vm.current().scanning).isFalse()
    }

    @Test fun saveWritesEveryFieldAndEditReopensIt() = runBlocking<Unit> {
        val vm = vm()
        vm.current()
        vm.onEvent(MaterialFormEvent.Scan)
        vm.onEvent(MaterialFormEvent.Scanned("content://scanner/page1.jpg"))
        vm.current { it.draft.receiptPath != null }
        vm.onEvent(MaterialFormEvent.Vendor("City Electric Supply"))
        vm.onEvent(MaterialFormEvent.UseTotal)
        vm.onEvent(MaterialFormEvent.Category(MaterialCategory.TOOLS))
        vm.onEvent(MaterialFormEvent.Note("Wago 221s"))
        vm.onEvent(MaterialFormEvent.Save)
        vm.current { it.done }
        val saved = materials.observeForCompany("c1").first().single()
        assertThat(saved.vendor).isEqualTo("City Electric Supply")
        assertThat(saved.amountCents).isEqualTo(12_371L)
        assertThat(saved.category).isEqualTo(MaterialCategory.TOOLS)
        assertThat(saved.receiptPath).isEqualTo("/media/r1.jpg")
        assertThat(saved.note).isEqualTo("Wago 221s")

        val edit = vm(saved.id)
        val s = edit.current()
        assertThat(s.isNew).isFalse()
        assertThat(s.draft.amount).isEqualTo("123.71")
        assertThat(s.dirty).isFalse()
        edit.onEvent(MaterialFormEvent.AskDelete)
        edit.onEvent(MaterialFormEvent.Delete)
        edit.current { it.done }
        assertThat(materials.observeForCompany("c1").first()).isEmpty()
    }

    @Test fun typingSurvivesProcessDeath() {
        val saved = SavedStateHandle()
        val first = vm(saved = saved)
        first.current()
        first.onEvent(MaterialFormEvent.Amount("42.10"))
        first.onEvent(MaterialFormEvent.Vendor("Sunbelt Rentals"))
        val again = vm(saved = saved).current()
        assertThat(again.draft.amount).isEqualTo("42.10")
        assertThat(again.draft.vendor).isEqualTo("Sunbelt Rentals")
        assertThat(again.dirty).isTrue()
    }

    @Test fun aDeletedReceiptShowsTheErrorState() {
        assertThat(vm("gone").current().failed).isTrue()
    }

    // T060c: a receipt scanned on another phone (key synced, no image here) is downloaded and shown on open, without
    // making the form dirty; a receipt with no key is never fetched.
    @Test fun receiptFromAnotherPhoneIsDownloadedAndShown() {
        runBlocking {
            db.materialDao().upsert(com.kurtz.homerecord.core.data.MaterialEntity(id = "m1", jobId = "j1", vendor = "City Electric", amountCents = 12_371, date = "2026-11-03", r2Key = "c1/material/m1/r.jpg", createdAt = 1, updatedAt = 1))
            db.materialDao().upsert(com.kurtz.homerecord.core.data.MaterialEntity(id = "m2", jobId = "j1", amountCents = 500, date = "2026-11-03", createdAt = 1, updatedAt = 1))
        }
        fetched = "/cache/downloads/r.jpg"
        val vm = vm("m1")
        val s = vm.current { it.draft.receiptPath != null }
        assertThat(s.draft.receiptPath).isEqualTo("/cache/downloads/r.jpg")
        assertThat(s.dirty).isFalse()
        vm("m2").current()
        assertThat(fetches).containsExactly("m1")
    }
}
