package com.kurtz.homerecord.fieldlog.house

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.core.warranty.WarrantyLookupApi
import com.kurtz.homerecord.core.warranty.WarrantyTerms
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
import com.kurtz.homerecord.housemodel.ui.roomItemsFor
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
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

/** FL-Item (T062): an item installed on the job carries make, model and serial, sits in its room and gets the warranty lookup. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobItemViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var items: ItemRepository
    private lateinit var lookup: WarrantyLookupRepository
    private val changes = mutableListOf<String>()
    private var result: ApiResult<WarrantyTerms> = ApiResult.Ok(WarrantyTerms(partsMonths = 72, laborMonths = 12, confidence = "HIGH", sourceUrl = "https://rheem.com/w"))
    private val canEdit = MutableStateFlow(true)
    private val vms = mutableListOf<JobItemViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        val record: suspend (String, String) -> Unit = { entity, _ -> synchronized(changes) { changes += entity } }
        items = ItemRepository(db, ApplicationProvider.getApplicationContext<Context>().resources::getString, today = { LocalDate.of(2026, 11, 5) }, onChange = record, source = com.kurtz.homerecord.core.data.Sources.FIELD_LOG)
        lookup = WarrantyLookupRepository(db, object : WarrantyLookupApi {
            override suspend fun lookup(make: String, model: String, category: String?, serial: String?) = result
        }, onChange = record)
        runBlocking {
            db.homeDao().upsert(HomeEntity(id = "h1", name = "Hale kitchen", createdAt = 1, updatedAt = 1))
            db.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", name = "Main floor", sortOrder = 0, createdAt = 1, updatedAt = 1))
            db.roomDao().upsert(RoomEntity(id = "r1", floorId = "f1", name = "Utility room", createdAt = 1, updatedAt = 1))
        }
    }

    @After fun tearDown() {
        runBlocking { vms.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun heater(model: String = "XE50M06ST45U1"): String =
        items.save("h1", ItemDraft(subtypeId = "water_heater", roomId = "r1", name = "Water heater", make = "Rheem", model = model, serial = "Q051826153", inServiceOn = "2026-10-21")).orEmpty()

    private fun vm(id: String, saved: SavedStateHandle = SavedStateHandle()) =
        JobItemViewModel(items, lookup, id, items.observeDetail(id, "h1", emptyMap()), canEdit, saved).also {
            vms += it
            it.state.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Main))
        }

    /** Room and the lookup hop to real threads, so wait for the state instead of reading it straight away. */
    private suspend fun JobItemViewModel.await(until: (JobItemUiState) -> Boolean) =
        kotlinx.coroutines.withContext(Dispatchers.Default) { kotlinx.coroutines.withTimeout(10_000) { state.first(until) } }

    @AcceptanceCriterion("AC-F50")
    @Test fun anInstalledItemCarriesMakeModelSerialShowsInItsRoomAndQueuesForSync() = runTest {
        val id = heater()
        val page = vm(id).await { it.detail != null }
        assertThat(page.detail?.item?.make).isEqualTo("Rheem")
        assertThat(page.detail?.item?.model).isEqualTo("XE50M06ST45U1")
        assertThat(page.detail?.item?.serial).isEqualTo("Q051826153")
        assertThat(page.detail?.roomName).isEqualTo("Utility room")
        assertThat(page.detail?.item?.source).isEqualTo(com.kurtz.homerecord.core.data.Sources.FIELD_LOG)
        val room = roomItemsFor(items.observeRows("h1", emptyMap()).first(), "r1")
        assertThat(room.map { it.name to it.detail }).containsExactly("Water heater" to "Rheem XE50M06ST45U1")
        // The item and the reminders it seeds go to the outbox; the backend takes them in the company tenant (sync.test.ts T062).
        assertThat(synchronized(changes) { changes.toList() }).containsAtLeast("item", "maintenance_task")
    }

    @AcceptanceCriterion("AC-F50", "AC-C34")
    @Test fun lookUpShowsTermsAndSavingStoresTheWarrantyForTheHandoff() = runTest {
        val vm = vm(heater())
        vm.onEvent(JobItemEvent.LookUp(again = false))
        assertThat(vm.await { it.review != null }.review?.partsMonths).isEqualTo(72)
        vm.onEvent(JobItemEvent.ConfirmTerms(partsMonths = 72, laborMonths = 12))
        val w = vm.await { it.review == null && it.detail?.warranty != null }.detail?.warranty
        assertThat(w?.partsMonths).isEqualTo(72)
        assertThat(w?.endsOn).isEqualTo("2032-10-21")
        assertThat(synchronized(changes) { changes.toList() }).contains("warranty")
    }

    @AcceptanceCriterion("AC-F50")
    @Test fun noModelExplainsWhyAndOfflineQueuesTheLookup() = runTest {
        val bare = vm(heater(model = ""))
        bare.onEvent(JobItemEvent.LookUp(again = false))
        assertThat(bare.await { it.lookupNote != null }.lookupNote).isEqualTo(JobItemLookupNote.NeedsMakeModel)
        result = ApiResult.Err(ApiFailure.Offline)
        val offline = vm(heater())
        offline.onEvent(JobItemEvent.LookUp(again = false))
        assertThat(offline.await { it.lookupNote != null }.lookupNote).isEqualTo(JobItemLookupNote.Queued)
        assertThat(offline.await { it.detail?.warranty != null }.detail?.warranty?.confidence).isEqualTo(WarrantyLookupRepository.PENDING)
    }

    @Test fun deleteAsksFirstThenClosesAndTheConfirmSurvivesProcessDeath() = runTest {
        val id = heater()
        val saved = SavedStateHandle()
        val vm = vm(id, saved)
        vm.onEvent(JobItemEvent.AskDelete)
        assertThat(vm.await { it.detail != null }.confirmDelete).isTrue()
        assertThat(vm(id, saved).await { it.detail != null }.confirmDelete).isTrue()
        vm.onEvent(JobItemEvent.ConfirmDelete)
        assertThat(vm.await { it.closed && it.detail == null }.closed).isTrue()
        assertThat(roomItemsFor(items.observeRows("h1", emptyMap()).first(), "r1")).isEmpty()
    }

    @Test fun crewCannotOpenTheDeleteConfirm() = runTest {
        canEdit.value = false
        val vm = vm(heater())
        vm.onEvent(JobItemEvent.AskDelete)
        val s = vm.await { it.detail != null }
        assertThat(s.canEdit).isFalse()
        assertThat(s.confirmDelete).isFalse()
    }

    @Test fun crewCannotDeleteLookUpOrRegisterEvenIfAnEventArrives() = runTest {
        val id = heater()
        canEdit.value = false
        val vm = vm(id)
        vm.await { it.detail != null }
        vm.onEvent(JobItemEvent.SetRegistered(true))
        vm.onEvent(JobItemEvent.LookUp(again = false))
        vm.onEvent(JobItemEvent.ConfirmDelete)
        val s = vm.await { it.detail != null && !it.lookupBusy }
        assertThat(s.closed).isFalse()
        assertThat(s.review).isNull()
        assertThat(s.detail?.warranty).isNull()
        assertThat(items.observeRows("h1", emptyMap()).first()).isNotEmpty()
    }
}
