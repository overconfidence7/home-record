package com.kurtz.homerecord.housemodel.panel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.SlotPlacement
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PanelRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: PanelRepository
    private val changes = mutableListOf<String>()
    private var clock = 1_000L

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = PanelRepository(db, now = { clock++ }, onChange = { e, id -> changes += "$e/$id" })
        db.itemDao().upsert(ItemEntity(id = "panel-item", homeId = "h1", category = "ELECTRICAL", subtype = "breaker_panel", name = "Main panel", createdAt = 1, updatedAt = 1))
    }

    @After fun tearDown() = db.close()

    @AcceptanceCriterion("AC-C40")
    @Test fun panelIsCreatedOnceWithFortySlotsAndTwoColumns() = runTest {
        assertThat(isPanelSubtype("breaker_panel")).isTrue()
        assertThat(isPanelSubtype("sub_panel")).isTrue()
        assertThat(isPanelSubtype("furnace")).isFalse()
        val id = repo.ensurePanel("panel-item")
        assertThat(repo.ensurePanel("panel-item")).isEqualTo(id)
        val state = checkNotNull(repo.observe("panel-item").first())
        assertThat(state.panel.slotCount).isEqualTo(40)
        assertThat(state.panel.layout).isEqualTo(PanelLayout.NORTH_AMERICAN)
        assertThat(state.grid.cells).hasSize(40)
        assertThat(changes).containsExactly("panel/$id")
    }

    @AcceptanceCriterion("AC-C40")
    @Test fun twoPoleBreakerFillsTwoSlotsAndTakenSlotsAreRefused() = runTest {
        val panel = repo.ensurePanel("panel-item")
        val saved = repo.saveBreaker(panel, BreakerDraft(startSlot = 1, poles = 2, amps = "40", type = BreakerType.STANDARD, label = " Range "))
        val rangeId = (saved as BreakerSave.Saved).id
        val state = checkNotNull(repo.observe("panel-item").first())
        val range = checkNotNull(state.breaker(rangeId))
        assertThat(range.slots).containsExactly(1, 3).inOrder()
        assertThat(range.label).isEqualTo("Range")
        assertThat(state.grid.cells.first { it.slot == 1 }.rowSpan).isEqualTo(2)
        assertThat(state.grid.cells.none { it.slot == 3 }).isTrue()

        val clash = repo.saveBreaker(panel, BreakerDraft(startSlot = 3, label = "Bath"))
        assertThat(clash).isEqualTo(BreakerSave.Invalid(setOf(BreakerField.SLOT), SlotPlacement.Taken(listOf(3))))
        val badAmps = repo.saveBreaker(panel, BreakerDraft(startSlot = 5, amps = "twenty"))
        assertThat((badAmps as BreakerSave.Invalid).fields).containsExactly(BreakerField.AMPS)
    }

    @AcceptanceCriterion("AC-C40")
    @Test fun tandemPutsTwoBreakersInOneSlot() = runTest {
        val panel = repo.ensurePanel("panel-item")
        val a = (repo.saveBreaker(panel, BreakerDraft(startSlot = 14, amps = "15", label = "Hall lights")) as BreakerSave.Saved).id
        assertThat(repo.saveBreaker(panel, BreakerDraft(startSlot = 14, label = "Attic"))).isInstanceOf(BreakerSave.Invalid::class.java)
        val b = (repo.saveBreaker(panel, BreakerDraft(startSlot = 14, label = "Attic", tandem = true)) as BreakerSave.Saved).id
        val cell = checkNotNull(repo.observe("panel-item").first()).grid.cells.first { it.slot == 14 }
        assertThat(cell.breakerIds).containsExactly(a, b).inOrder()
        assertThat(checkNotNull(repo.draftFor(b)).tandem).isTrue()
    }

    @AcceptanceCriterion("AC-C40")
    @Test fun roomsAndItemsFedAreLinksThatFollowTheEditor() = runTest {
        val panel = repo.ensurePanel("panel-item")
        val id = (repo.saveBreaker(panel, BreakerDraft(startSlot = 7, label = "Kitchen counters", type = BreakerType.GFCI, roomIds = setOf("kitchen"), itemIds = setOf("fridge", "dishwasher"))) as BreakerSave.Saved).id
        var links = checkNotNull(repo.observe("panel-item").first()).links.getValue(id)
        assertThat(links.mapNotNull { it.roomId }).containsExactly("kitchen")
        assertThat(links.mapNotNull { it.itemId }).containsExactly("fridge", "dishwasher")
        assertThat(links.map { it.confidence }.toSet()).containsExactly("LIKELY")

        val draft = checkNotNull(repo.draftFor(id))
        assertThat(draft.type).isEqualTo(BreakerType.GFCI)
        repo.saveBreaker(panel, draft.copy(itemIds = setOf("fridge"), roomIds = setOf("kitchen", "dining")))
        links = checkNotNull(repo.observe("panel-item").first()).links.getValue(id)
        assertThat(links.mapNotNull { it.itemId }).containsExactly("fridge")
        assertThat(links.mapNotNull { it.roomId }).containsExactly("kitchen", "dining")

        repo.deleteBreaker(id)
        val after = checkNotNull(repo.observe("panel-item").first())
        assertThat(after.breakers).isEmpty()
        assertThat(after.links).isEmpty()
        assertThat(changes.count { it.startsWith("breaker_link/") }).isAtLeast(5)
    }

    @AcceptanceCriterion("AC-C40")
    @Test fun panelFactsSaveAndCannotShrinkBelowABreakerOrSwitchLayoutUnderOne() = runTest {
        val panel = repo.ensurePanel("panel-item")
        assertThat(repo.savePanel(panel, PanelDraft(slotCount = "30", mainAmps = "200", brand = " Square D "))).isTrue()
        var p = checkNotNull(repo.observe("panel-item").first()).panel
        assertThat(p.slotCount).isEqualTo(30)
        assertThat(p.mainAmps).isEqualTo(200)
        assertThat(p.brand).isEqualTo("Square D")
        assertThat(PanelDraft(mainAmps = "lots").errors()).containsExactly(PanelField.MAIN_AMPS)
        assertThat(PanelDraft(slotCount = "0").errors()).containsExactly(PanelField.SLOT_COUNT)

        repo.saveBreaker(panel, BreakerDraft(startSlot = 27, poles = 2, label = "Dryer"))
        assertThat(repo.savePanel(panel, PanelDraft(slotCount = "24"))).isFalse()
        assertThat(repo.savePanel(panel, PanelDraft(slotCount = "30", layout = PanelLayout.DIN))).isFalse()
        repo.setPhoto(panel, "media/panel.jpg")
        p = checkNotNull(repo.observe("panel-item").first()).panel
        assertThat(p.photoPath).isEqualTo("media/panel.jpg")
        assertThat(p.slotCount).isEqualTo(30)
    }

    // AC-C41: an item or room page lists the breakers feeding it, with the panel's name and the link's confidence.
    @AcceptanceCriterion("AC-C41")
    @Test fun itemsAndRoomsListTheBreakersFeedingThemWithThePanelNameAndConfidence() = runTest {
        db.itemDao().upsert(ItemEntity(id = "sub-item", homeId = "h1", category = "ELECTRICAL", subtype = "sub_panel", name = "Garage sub-panel", createdAt = 2, updatedAt = 2))
        val main = repo.ensurePanel("panel-item")
        val sub = repo.ensurePanel("sub-item")
        val fridge = (repo.saveBreaker(main, BreakerDraft(startSlot = 12, amps = "20", label = "Refrigerator", itemIds = setOf("fridge"), roomIds = setOf("kitchen"))) as BreakerSave.Saved).id
        repo.saveBreaker(main, BreakerDraft(startSlot = 2, label = "Kitchen counter", roomIds = setOf("kitchen")))
        repo.saveBreaker(sub, BreakerDraft(startSlot = 1, label = "Freezer outlet", itemIds = setOf("fridge")))

        val forFridge = repo.observeFeedingItem("fridge").first()
        assertThat(forFridge.map { it.panelName to it.breaker.number }).containsExactly("Garage sub-panel" to 1, "Main panel" to 12).inOrder()
        assertThat(forFridge.map { it.confidence }.toSet()).containsExactly(com.kurtz.homerecord.core.model.LinkConfidence.LIKELY)
        assertThat(forFridge.last().breaker.amps).isEqualTo(20)

        val forKitchen = repo.observeFeedingRoom("kitchen").first()
        assertThat(forKitchen.map { it.breaker.label }).containsExactly("Kitchen counter", "Refrigerator").inOrder()

        // A deleted breaker, or a deleted panel item, no longer feeds anything.
        repo.deleteBreaker(fridge)
        assertThat(repo.observeFeedingRoom("kitchen").first().map { it.breaker.label }).containsExactly("Kitchen counter")
        db.itemDao().softDelete("sub-item", 99)
        assertThat(repo.observeFeedingItem("fridge").first()).isEmpty()
    }

    @Test fun aBreakerFeedsRoomsAndItemsButNotThePanels() = runTest {
        db.itemDao().upsert(ItemEntity(id = "dw", homeId = "h1", category = "APPLIANCES", subtype = "dishwasher", name = "dishwasher", createdAt = 3, updatedAt = 3))
        db.itemDao().upsert(ItemEntity(id = "ac", homeId = "h1", category = "HVAC", subtype = "ac", name = "Air conditioner", createdAt = 4, updatedAt = 4))
        val choices = repo.observeChoices("h1").first()
        assertThat(choices.items.map { it.name }).containsExactly("Air conditioner", "dishwasher").inOrder()
        assertThat(choices.rooms).isEmpty()
    }

    // AC-H3: Home search indexes every live breaker of the home with its panel item, so a hit opens that panel.
    @AcceptanceCriterion("AC-H3")
    @Test fun homeSearchSeesEveryLiveBreakerWithItsPanelItem() = runTest {
        db.itemDao().upsert(ItemEntity(id = "other-home-panel", homeId = "h2", category = "ELECTRICAL", subtype = "breaker_panel", name = "Neighbor", createdAt = 2, updatedAt = 2))
        val main = repo.ensurePanel("panel-item")
        val other = repo.ensurePanel("other-home-panel")
        val dryer = (repo.saveBreaker(main, BreakerDraft(startSlot = 27, poles = 2, amps = "30", label = "Dryer")) as BreakerSave.Saved).id
        val gone = (repo.saveBreaker(main, BreakerDraft(startSlot = 3, label = "Old porch light")) as BreakerSave.Saved).id
        repo.saveBreaker(other, BreakerDraft(startSlot = 1, label = "Not ours"))
        repo.deleteBreaker(gone)

        val rows = db.breakerDao().observeForHome("h1").first()
        assertThat(rows.map { Triple(it.label, it.panelItemId, it.number) }).containsExactly(Triple("Dryer", "panel-item", 27))
        assertThat(rows.single().breakerId).isEqualTo(dryer)
        assertThat(rows.single().panelName).isEqualTo("Main panel")
        assertThat(rows.single().amps).isEqualTo(30)
    }
}
