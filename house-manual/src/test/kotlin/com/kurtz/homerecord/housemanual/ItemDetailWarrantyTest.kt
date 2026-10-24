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
import com.kurtz.homerecord.housemanual.items.LookupNote
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
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
    private var result: ApiResult<WarrantyTerms> = ApiResult.Ok(WarrantyTerms(partsMonths = 72, laborMonths = 12, confidence = "HIGH", sourceUrl = "https://rheem.com/w"))
    private val vms = mutableListOf<ItemDetailViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        items = ItemRepository(db, today = { LocalDate.of(2026, 10, 24) })
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

    private fun vm(id: String) = ItemDetailViewModel(items, lookup, id, items.observeDetail(id, "h1", emptyMap()), "copy").also {
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
}
