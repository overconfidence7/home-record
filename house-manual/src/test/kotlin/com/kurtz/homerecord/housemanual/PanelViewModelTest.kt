package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.SlotPlacement
import com.kurtz.homerecord.housemanual.panel.PanelEvent
import androidx.lifecycle.SavedStateHandle
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
    private val main = UnconfinedTestDispatcher()
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: PanelRepository
    private val vms = mutableListOf<PanelViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(main)
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

    private val media = PanelFakeMedia()

    private fun vm(itemId: String = "pi", saved: SavedStateHandle = SavedStateHandle(), highlightSlot: Int? = null) =
        PanelViewModel(repo, itemId, db.itemDao().observeOne(itemId).map { it?.name }, repo.observeChoices("h1"), saved, media, highlightSlot).also {
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

    private fun seedFeeds() = runBlocking {
        db.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", sortOrder = 0, name = "Main floor", createdAt = 1, updatedAt = 1))
        db.roomDao().upsert(RoomEntity(id = "kitchen", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
        db.itemDao().upsert(ItemEntity(id = "dw", homeId = "h1", category = "APPLIANCES", subtype = "dishwasher", name = "Dishwasher", createdAt = 2, updatedAt = 2))
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun tappingAnOpenSlotAddsALabeledBreakerThatFeedsARoomAndAnItem() {
        seedFeeds()
        val vm = vm()
        vm.await { !it.loading }
        vm.onEvent(PanelEvent.OpenSlot(5))
        val open = vm.await { it.editor?.rooms?.isNotEmpty() == true }
        // The panel item itself is not something a breaker feeds.
        assertThat(open.editor?.items?.map { it.name }).containsExactly("Dishwasher")
        assertThat(open.editor?.rooms?.map { it.name }).containsExactly("Kitchen")
        assertThat(open.editor?.isNew).isTrue()
        val draft = checkNotNull(open.editor).draft
        vm.onEvent(PanelEvent.ChangeBreaker(draft.copy(label = "Kitchen counter", amps = "20", type = BreakerType.GFCI, roomIds = setOf("kitchen"), itemIds = setOf("dw"))))
        vm.await { it.editor?.dirty == true }
        vm.onEvent(PanelEvent.SaveBreaker)
        val saved = vm.await { it.editor == null && it.breakers.size == 1 }
        val b = saved.breakers.single()
        assertThat(b.slots).containsExactly(5)
        assertThat(b.label).isEqualTo("Kitchen counter")
        assertThat(b.type).isEqualTo(BreakerType.GFCI)
        val links = runBlocking { db.breakerLinkDao().listFor(b.id) }
        assertThat(links.mapNotNull { it.roomId }).containsExactly("kitchen")
        assertThat(links.mapNotNull { it.itemId }).containsExactly("dw")
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun twoPolesIntoATakenSlotSaysWhichSlotAndDoesNotSave() {
        val panelId = runBlocking { repo.ensurePanel("pi") }
        runBlocking { repo.saveBreaker(panelId, BreakerDraft(startSlot = 3, label = "Bath")) }
        val vm = vm()
        vm.await { it.breakers.size == 1 }
        vm.onEvent(PanelEvent.OpenSlot(1))
        val draft = checkNotNull(vm.await { it.editor != null }.editor).draft
        vm.onEvent(PanelEvent.ChangeBreaker(draft.copy(poles = 2, label = "Range")))
        val e = checkNotNull(vm.await { it.editor?.slotProblem != null }.editor)
        assertThat(e.slotProblem).isEqualTo(SlotPlacement.Taken(listOf(3)))
        assertThat(e.canSave).isFalse()
        vm.onEvent(PanelEvent.SaveBreaker)
        assertThat(vm.state.value.editor).isNotNull()
        assertThat(runBlocking { db.breakerDao().listFor(panelId) }).hasSize(1)
        // Bad amps keep the form open with the error shown.
        vm.onEvent(PanelEvent.ChangeBreaker(draft.copy(poles = 1, amps = "999")))
        vm.onEvent(PanelEvent.SaveBreaker)
        vm.await { it.editor?.ampsError == true }
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun touchAndHoldOnASinglePoleBreakerAddsATandemInTheSameSlot() {
        val panelId = runBlocking { repo.ensurePanel("pi") }
        runBlocking {
            repo.saveBreaker(panelId, BreakerDraft(startSlot = 7, label = "Porch lights"))
            repo.saveBreaker(panelId, BreakerDraft(startSlot = 1, poles = 2, label = "Range"))
        }
        val vm = vm()
        vm.await { it.breakers.size == 2 }
        // Not beside a two-pole breaker.
        vm.onEvent(PanelEvent.AddTandem(1))
        assertThat(vm.state.value.editor).isNull()
        vm.onEvent(PanelEvent.AddTandem(7))
        val draft = checkNotNull(vm.await { it.editor != null }.editor).draft
        assertThat(draft.tandem).isTrue()
        // A tandem stays single-pole.
        vm.onEvent(PanelEvent.ChangeBreaker(draft.copy(poles = 2, label = "Garage door opener")))
        assertThat(vm.await { it.editor?.draft?.label == "Garage door opener" }.editor?.draft?.poles).isEqualTo(1)
        vm.onEvent(PanelEvent.SaveBreaker)
        val s = vm.await { it.editor == null && it.breakers.size == 3 }
        assertThat(checkNotNull(s.grid).cells.single { it.slot == 7 }.isTandem).isTrue()
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun editingABreakerSurvivesProcessDeathAndDeleteAsksThenFreesTheSlot() {
        val panelId = runBlocking { repo.ensurePanel("pi") }
        val id = (runBlocking { repo.saveBreaker(panelId, BreakerDraft(startSlot = 9, amps = "20", label = "Bathroom")) } as com.kurtz.homerecord.housemodel.panel.BreakerSave.Saved).id
        val saved = SavedStateHandle()
        val first = vm(saved = saved)
        first.await { it.breakers.size == 1 }
        first.onEvent(PanelEvent.OpenBreaker(id))
        val draft = checkNotNull(first.await { it.editor != null }.editor).draft
        assertThat(draft.label).isEqualTo("Bathroom")
        first.onEvent(PanelEvent.ChangeBreaker(draft.copy(label = "Hall bath")))

        val again = vm(saved = saved)
        val restored = checkNotNull(again.await { it.editor != null }.editor)
        assertThat(restored.draft.label).isEqualTo("Hall bath")
        assertThat(restored.dirty).isTrue()
        again.onEvent(PanelEvent.AskDeleteBreaker)
        again.await { it.editor?.confirmDelete == true }
        again.onEvent(PanelEvent.CancelDeleteBreaker)
        again.await { it.editor?.confirmDelete == false }
        again.onEvent(PanelEvent.AskDeleteBreaker)
        again.onEvent(PanelEvent.ConfirmDeleteBreaker)
        val s = again.await { it.editor == null && it.breakers.isEmpty() }
        assertThat(checkNotNull(s.grid).cells).hasSize(40)
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun panelDetailsSaveSlotCountMainAmpsAndBrandButNotBelowTheBreakersInUse() {
        val panelId = runBlocking { repo.ensurePanel("pi") }
        runBlocking { repo.saveBreaker(panelId, BreakerDraft(startSlot = 23, label = "Garage")) }
        val vm = vm()
        vm.await { it.breakers.size == 1 }
        vm.onEvent(PanelEvent.EditFacts)
        val facts = checkNotNull(vm.await { it.facts != null }.facts)
        assertThat(facts.draft.slotCount).isEqualTo("40")
        assertThat(facts.layoutLocked).isTrue()
        assertThat(facts.highestUsed).isEqualTo(23)
        vm.onEvent(PanelEvent.ChangeFacts(facts.draft.copy(slotCount = "20")))
        vm.onEvent(PanelEvent.SaveFacts)
        val refused = checkNotNull(vm.await { it.facts?.showErrors == true }.facts)
        assertThat(refused.errors).containsExactly(com.kurtz.homerecord.housemodel.panel.PanelField.SLOT_COUNT)
        vm.onEvent(PanelEvent.ChangeFacts(facts.draft.copy(slotCount = "30", mainAmps = "200", brand = "Square D QO")))
        vm.onEvent(PanelEvent.SaveFacts)
        val s = vm.await { it.facts == null && it.panel?.slotCount == 30 }
        assertThat(s.panel?.mainAmps).isEqualTo(200)
        assertThat(s.panel?.brand).isEqualTo("Square D QO")
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun aPanelPhotoComesFromTheCameraOrThePickerAndCanBeRemoved() {
        val saved = SavedStateHandle()
        val vm = vm(saved = saved)
        vm.await { !it.loading }
        vm.onEvent(PanelEvent.OpenPhotoSheet)
        vm.await { it.photoSheet }
        vm.onEvent(PanelEvent.TakePhoto)
        val target = checkNotNull(vm.await { it.photoLaunch != null }.photoLaunch?.target)
        vm.onEvent(PanelEvent.PhotoLaunchHandled)
        // The app was killed while the camera was open: a fresh view model still takes the photo.
        media.files += target.path
        val revived = vm(saved = saved)
        revived.await { !it.loading }
        revived.onEvent(PanelEvent.CameraResult(true))
        assertThat(revived.await { it.panel?.photoPath != null }.panel?.photoPath).isEqualTo(target.path)

        revived.onEvent(PanelEvent.GalleryResult("content://broken"))
        assertThat(revived.await { it.photoFailed }.panel?.photoPath).isEqualTo(target.path)
        revived.onEvent(PanelEvent.GalleryResult("content://picked"))
        assertThat(revived.await { it.panel?.photoPath?.contains("imported") == true }).isNotNull()
        revived.onEvent(PanelEvent.RemovePhoto)
        revived.await { it.panel?.photoPath == null }
    }

    @Test
    @AcceptanceCriterion("AC-H7")
    fun openedFromABreakerChipTheSlotIsMarkedForTwoSecondsAndNotAgainAfterRotation() {
        val saved = SavedStateHandle()
        val opened = vm(saved = saved, highlightSlot = 12)
        assertThat(opened.await { !it.loading }.highlightSlot).isEqualTo(12)
        main.scheduler.advanceTimeBy(1_999)
        main.scheduler.runCurrent()
        assertThat(opened.state.value.highlightSlot).isEqualTo(12)
        main.scheduler.advanceTimeBy(2)
        main.scheduler.runCurrent()
        assertThat(opened.await { it.highlightSlot == null }.highlightSlot).isNull()
        // The same back stack entry after rotation or process death keeps the saved state: no second highlight.
        assertThat(vm(saved = saved, highlightSlot = 12).await { !it.loading }.highlightSlot).isNull()
    }

    // AC-C43: Share legend makes the PDF from the panel, its breakers and what each feeds, then hands the URI to the route
    @Test
    @AcceptanceCriterion("AC-C43")
    fun shareLegendExportsThePanelWithFeedsAndClearsAfterHandled() {
        val got = mutableListOf<Pair<String, Map<String, List<String>>>>()
        val sizes = mutableListOf<Int>()
        var answer: String? = "content://legend.pdf"
        runBlocking {
            db.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", name = "Main", sortOrder = 0, createdAt = 1, updatedAt = 1))
            db.roomDao().upsert(RoomEntity(id = "kitchen", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
        }
        val vm = PanelViewModel(
            repo, "pi", db.itemDao().observeOne("pi").map { it?.name }, repo.observeChoices("h1"), SavedStateHandle(), media, null,
            legendExporter = { name, _, breakers, feeds -> got += name to feeds; sizes += breakers.size; answer },
        ).also { vms += it; it.state.launchIn(it.viewModelScope) }
        val panel = runBlocking { repo.ensurePanel("pi") }
        val id = runBlocking { (repo.saveBreaker(panel, BreakerDraft(startSlot = 4, label = "Fridge", roomIds = setOf("kitchen"))) as com.kurtz.homerecord.housemodel.panel.BreakerSave.Saved).id }
        vm.await { it.breakers.size == 1 }
        // The rooms a breaker can feed have arrived once the breaker form lists them.
        vm.onEvent(PanelEvent.OpenSlot(8))
        vm.await { it.editor?.rooms?.isNotEmpty() == true }
        vm.onEvent(PanelEvent.CloseEditor)
        // The breaker's links reach the panel a moment after the breaker; share until they are in.
        for (attempt in 1..50) {
            vm.onEvent(PanelEvent.ShareLegend)
            assertThat(vm.await { it.legendUri != null }.legendUri).isEqualTo("content://legend.pdf")
            if (got.last().second[id] != null) break
            vm.onEvent(PanelEvent.LegendHandled)
            vm.await { it.legendUri == null }
            Thread.sleep(20)
        }
        assertThat(got.last().first).isEqualTo("Main panel")
        assertThat(sizes.last()).isEqualTo(1)
        assertThat(got.last().second[id]).containsExactly("Kitchen")
        vm.onEvent(PanelEvent.LegendHandled)
        assertThat(vm.await { it.legendUri == null }.legendFailed).isFalse()
        answer = null
        vm.onEvent(PanelEvent.ShareLegend)
        assertThat(vm.await { it.legendFailed }.legendUri).isNull()
    }

    @Test
    fun openedFromMoreThePanelMarksNoSlot() {
        assertThat(vm().await { !it.loading }.highlightSlot).isNull()
    }
}

private class PanelFakeMedia : com.kurtz.homerecord.core.media.MediaFiles {
    val files = mutableSetOf<String>()
    private var n = 0
    override fun newCaptureTarget() = com.kurtz.homerecord.core.media.CaptureTarget("content://capture/${++n}", "/media/panel-$n.jpg")
    override suspend fun import(uri: String): String? = if (uri.contains("broken")) null else "/media/imported-${++n}.jpg".also { files += it }
    override fun exists(path: String) = path in files
    override fun discard(path: String) { files -= path }

}
