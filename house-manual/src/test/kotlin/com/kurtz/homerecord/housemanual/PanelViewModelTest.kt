package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.panel.PanelUiState
import com.kurtz.homerecord.housemanual.panel.PanelViewModel
import com.kurtz.homerecord.housemodel.panel.BreakerDraft
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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

// AC-C40: HM-Panel opens the panel of a Breaker panel item (made with 40 two-column slots the first time) and follows it.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PanelViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: PanelRepository
    private val vms = mutableListOf<PanelViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = PanelRepository(db)
        runBlocking {
            db.itemDao().upsert(ItemEntity(id = "pi", homeId = "h1", category = "ELECTRICAL", subtype = "breaker_panel", name = "Main panel", createdAt = 1, updatedAt = 1))
        }
    }

    @After fun tearDown() {
        runBlocking { vms.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(itemId: String = "pi") = PanelViewModel(repo, itemId, db.itemDao().observeOne(itemId).map { it?.name }).also {
        vms += it
        it.state.launchIn(it.viewModelScope)
    }

    private fun PanelViewModel.await(what: (PanelUiState) -> Boolean) = runBlocking { withTimeout(5_000) { state.first(what) } }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun openingAPanelItemMakesItsFortySlotPanelOnceAndNamesTheScreenAfterTheItem() {
        val s = vm().await { !it.loading }
        assertThat(s.name).isEqualTo("Main panel")
        assertThat(s.panel?.slotCount).isEqualTo(40)
        assertThat(s.grid?.columns).isEqualTo(2)
        assertThat(s.grid?.cells).hasSize(40)
        assertThat(s.breakers).isEmpty()
        vm().await { !it.loading }
        assertThat(runBlocking { db.panelDao().listFor("pi") }).hasSize(1)
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun aTwoPoleBreakerShowsAsOneTallCellAndATandemAsTwoHalves() {
        val panelId = runBlocking { repo.ensurePanel("pi") }
        runBlocking {
            repo.saveBreaker(panelId, BreakerDraft(startSlot = 1, poles = 2, amps = "40", label = "Range"))
            repo.saveBreaker(panelId, BreakerDraft(startSlot = 2, poles = 1, amps = "15", label = "Porch lights"))
            repo.saveBreaker(panelId, BreakerDraft(startSlot = 2, poles = 1, amps = "15", label = "Garage door", tandem = true))
        }
        val s = vm().await { it.breakers.size == 3 }
        val cells = checkNotNull(s.grid).cells
        assertThat(cells.single { it.slot == 1 }.rowSpan).isEqualTo(2)
        assertThat(cells.none { it.slot == 3 }).isTrue()
        assertThat(cells.single { it.slot == 2 }.isTandem).isTrue()
        assertThat(cells).hasSize(39)
        assertThat(s.unplaced).isEmpty()
    }

    @Test
    fun aDeletedItemShowsTheMissingStateAndMakesNoPanel() {
        val s = vm("gone").await { !it.loading }
        assertThat(s.missing).isTrue()
        assertThat(runBlocking { db.panelDao().listFor("gone") }).isEmpty()
    }
}
