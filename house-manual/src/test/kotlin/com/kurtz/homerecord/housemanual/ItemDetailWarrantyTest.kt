package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.core.warranty.WarrantyLookupApi
import com.kurtz.homerecord.core.warranty.WarrantyTerms
import com.kurtz.homerecord.housemanual.items.ItemDetailEvent
import com.kurtz.homerecord.housemanual.items.ItemDetailViewModel
import com.kurtz.homerecord.housemanual.items.RecallUi
import com.kurtz.homerecord.housemanual.items.LookupNote
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
import com.kurtz.homerecord.housemodel.upkeep.ConsumableDraft
import com.kurtz.homerecord.housemodel.upkeep.ServiceDraft
import com.kurtz.homerecord.housemodel.upkeep.UpkeepRepository
import java.time.LocalDate
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ItemDetailWarrantyTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var items: ItemRepository
    private lateinit var lookup: WarrantyLookupRepository
    private lateinit var docs: com.kurtz.homerecord.housemodel.documents.DocumentRepository
    private var result: ApiResult<WarrantyTerms> = ApiResult.Ok(WarrantyTerms(partsMonths = 72, laborMonths = 12, confidence = "HIGH", sourceUrl = "https://rheem.com/w"))
    private val vms = mutableListOf<ItemDetailViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        items = ItemRepository(db, today = { LocalDate.of(2026, 10, 24) })
        docs = com.kurtz.homerecord.housemodel.documents.DocumentRepository(
            db,
            object : com.kurtz.homerecord.core.media.DocumentFiles {
                override suspend fun import(uri: String) =
                    if (uri == "u/manual.pdf") com.kurtz.homerecord.core.media.StoredDocument("documents/a.pdf", "Water heater manual.pdf", "application/pdf", 2048)
                    else if (uri == "u/notes.txt") com.kurtz.homerecord.core.media.StoredDocument("documents/c.txt", "notes.txt", "text/plain", 10) else null
                override fun discard(path: String) = Unit
            },
        )
        lookup = WarrantyLookupRepository(db, object : WarrantyLookupApi {
            override suspend fun lookup(make: String, model: String, category: String?, serial: String?) = result
        })
    }

    @After fun tearDown() {
        runBlocking { vms.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun heater(model: String = "XG50T06"): String =
        items.save("h1", ItemDraft(subtypeId = "water_heater", roomId = PseudoRooms.WHOLE_HOUSE, name = "Water heater", make = "Rheem", model = model, inServiceOn = "2024-01-15")).orEmpty()

    private var recallResult: ApiResult<List<com.kurtz.homerecord.core.lookup.RecallInfo>> = ApiResult.Ok(emptyList())
    private val recallApi = object : com.kurtz.homerecord.core.lookup.RecallApi {
        override suspend fun check(make: String, model: String?) = recallResult
    }

    private fun vm(id: String) = ItemDetailViewModel(items, lookup, UpkeepRepository(db), docs, id, "h1", items.observeDetail(id, "h1", emptyMap()), "copy", recallApi = recallApi).also {
        vms += it
        it.state.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Main))
    }

    /** Room and the lookup hop to real threads, so wait for the state instead of reading it straight away. */
    private suspend fun ItemDetailViewModel.await(until: (com.kurtz.homerecord.housemanual.items.ItemDetailUiState) -> Boolean) =
        kotlinx.coroutines.withContext(Dispatchers.Default) { kotlinx.coroutines.withTimeout(10_000) { state.first(until) } }

    @AcceptanceCriterion("AC-C34")
    @Test fun lookUpShowsTermsThenEditedConfirmStoresThemAsManual() = runTest {
        val id = heater()
        val vm = vm(id)
        vm.onEvent(ItemDetailEvent.LookUp(again = false))
        assertThat(vm.await { it.review != null }.review?.partsMonths).isEqualTo(72)
        vm.onEvent(ItemDetailEvent.ConfirmTerms(partsMonths = 60, laborMonths = 12))
        val w = vm.await { it.review == null && it.detail?.warranty != null }.detail?.warranty
        assertThat(w?.partsMonths).isEqualTo(60)
        assertThat(w?.confidence).isEqualTo("MANUAL")
        assertThat(w?.endsOn).isNotNull()
    }

    @AcceptanceCriterion("AC-C34")
    @Test fun discardStoresNothingAndMissingModelExplainsWhy() = runTest {
        val vm = vm(heater())
        vm.onEvent(ItemDetailEvent.LookUp(again = false))
        vm.await { it.review != null }
        vm.onEvent(ItemDetailEvent.DiscardTerms)
        assertThat(vm.await { it.review == null && !it.lookupBusy && it.detail != null }.detail?.warranty).isNull()
        val bare = vm(heater(model = ""))
        bare.onEvent(ItemDetailEvent.LookUp(again = false))
        assertThat(bare.await { it.lookupNote != null }.lookupNote).isEqualTo(LookupNote.NeedsMakeModel)
    }

    @AcceptanceCriterion("AC-C34")
    @Test fun offlineQueuesThenRunPendingLeavesTermsForReview() = runTest {
        val id = heater()
        val vm = vm(id)
        result = ApiResult.Err(ApiFailure.Offline)
        vm.onEvent(ItemDetailEvent.LookUp(again = false))
        assertThat(vm.await { it.lookupNote != null }.lookupNote).isEqualTo(LookupNote.Queued)
        assertThat(vm.await { it.detail?.warranty != null }.detail?.warranty?.confidence).isEqualTo(WarrantyLookupRepository.PENDING)
        result = ApiResult.Ok(WarrantyTerms(partsMonths = 72, confidence = "HIGH"))
        lookup.runPending()
        assertThat(vm.await { it.detail?.warranty?.confidence?.startsWith(WarrantyLookupRepository.REVIEW) == true }.detail).isNotNull()
        vm.onEvent(ItemDetailEvent.OpenReview)
        assertThat(vm.await { it.review != null }.review?.partsMonths).isEqualTo(72)
    }

    @AcceptanceCriterion("AC-C34")
    @Test fun autoLookupLeavesFoundTermsAsABadgeWithoutASheet() = runTest {
        val id = heater()
        assertThat(lookup.autoLookup(id)).isTrue()
        val vm = vm(id)
        assertThat(vm.await { it.detail?.warranty != null }.review).isNull()
        assertThat(vm.state.value.detail?.warranty?.confidence).isEqualTo("REVIEW_HIGH")
    }

    @AcceptanceCriterion("AC-C35")
    @Test fun upkeepFormsSaveConsumableAndServiceNewestFirst() = runTest {
        // AC-C35, AC-C36
        val id = heater()
        val vm = vm(id)
        vm.onEvent(ItemDetailEvent.OpenConsumable(null))
        vm.onEvent(ItemDetailEvent.SaveConsumable)
        assertThat(vm.await { it.showFormErrors }.consumableForm).isNotNull()
        vm.onEvent(ItemDetailEvent.ChangeConsumable(ConsumableDraft(spec = "16x25x1 MERV 8", quantity = "2")))
        vm.onEvent(ItemDetailEvent.SaveConsumable)
        assertThat(vm.await { it.consumableForm == null && it.consumables.any { c -> c.spec == "16x25x1 MERV 8" } }.consumables).isNotEmpty()

        vm.onEvent(ItemDetailEvent.OpenService(null))
        vm.onEvent(ItemDetailEvent.ChangeService(ServiceDraft(date = "2025-01-10", description = "Flushed tank")))
        vm.onEvent(ItemDetailEvent.SaveService)
        vm.await { it.serviceForm == null && it.service.size == 1 }
        vm.onEvent(ItemDetailEvent.OpenService(null))
        vm.onEvent(ItemDetailEvent.ChangeService(ServiceDraft(date = "2026-02-01", description = "New anode rod", cost = "89.50")))
        vm.onEvent(ItemDetailEvent.SaveService)
        val rows = vm.await { it.serviceForm == null && it.service.size == 2 }.service
        assertThat(rows.map { it.description }).containsExactly("New anode rod", "Flushed tank").inOrder()
        assertThat(rows.first().costCents).isEqualTo(8950L)
    }

    @AcceptanceCriterion("AC-C39")
    @Test fun serviceFormOffersContactsAndSavesTheChosenOne() = runTest {
        val id = heater()
        val cid = UpkeepRepository(db).saveContact("h1", com.kurtz.homerecord.housemodel.upkeep.ContactDraft(name = "Dale Harlan", trade = "HVAC")).orEmpty()
        val vm = vm(id)
        vm.onEvent(ItemDetailEvent.OpenService(null))
        assertThat(vm.await { it.contacts.isNotEmpty() }.contacts.map { it.name }).containsExactly("Dale Harlan")
        vm.onEvent(ItemDetailEvent.ChangeService(ServiceDraft(date = "2026-02-01", contactId = cid, description = "Tune-up")))
        vm.onEvent(ItemDetailEvent.SaveService)
        assertThat(vm.await { it.serviceForm == null && it.service.size == 1 }.service.first().contactId).isEqualTo(cid)
    }

    @AcceptanceCriterion("AC-C37")
    @Test fun documentsAddAttachAndRejectUnsupportedFiles() = runTest {
        val vm = vm(heater())
        vm.onEvent(ItemDetailEvent.DocumentPicked("u/manual.pdf"))
        assertThat(vm.await { it.documents.size == 1 }.documents.single().name).isEqualTo("Water heater manual.pdf")
        vm.onEvent(ItemDetailEvent.DocumentPicked("u/notes.txt"))
        assertThat(vm.await { it.documentNotice }.documents).hasSize(1)
        vm.onEvent(ItemDetailEvent.DismissDocumentNotice)
        val homeDoc = docs.add("h1", com.kurtz.homerecord.housemodel.documents.DocumentScope.Home, "u/manual.pdf").orEmpty()
        vm.onEvent(ItemDetailEvent.ShowAttach)
        assertThat(vm.await { it.showAttach && it.attachable.any { d -> d.id == homeDoc } }.documents).hasSize(1)
        vm.onEvent(ItemDetailEvent.AttachDocument(homeDoc))
        assertThat(vm.await { !it.showAttach && it.documents.size == 2 }.attachable).isEmpty()
    }

    @AcceptanceCriterion("AC-B12")
    @Test fun checkRecallsShowsMatchesNoneOfflineAndNeedsMake() = runTest {
        val vm = vm(heater())
        recallResult = ApiResult.Ok(listOf(com.kurtz.homerecord.core.lookup.RecallInfo("24-100", "Rheem water heaters", "2024-03-01", "Fire", null, null, true)))
        vm.onEvent(ItemDetailEvent.CheckRecalls)
        val done = vm.await { it.recalls is RecallUi.Done }.recalls as RecallUi.Done
        assertThat(done.recalls.single().modelMatch).isTrue()

        recallResult = ApiResult.Err(ApiFailure.Offline)
        vm.onEvent(ItemDetailEvent.CheckRecalls)
        assertThat(vm.await { it.recalls == RecallUi.Offline }.recalls).isEqualTo(RecallUi.Offline)

        val bare = vm(items.save("h1", ItemDraft(subtypeId = "water_heater", roomId = PseudoRooms.WHOLE_HOUSE, name = "Heater")).orEmpty())
        bare.onEvent(ItemDetailEvent.CheckRecalls)
        assertThat(bare.await { it.recalls == RecallUi.NeedsMake }.recalls).isEqualTo(RecallUi.NeedsMake)
    }
}
