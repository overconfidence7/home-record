package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.qa.RegressionSeed
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.home.AttentionKind
import com.kurtz.homerecord.housemodel.items.ItemFilter
import com.kurtz.homerecord.housemodel.items.ItemGrouping
import com.kurtz.homerecord.housemodel.items.ItemListBuilder
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.items.WarrantyFilter
import com.kurtz.homerecord.housemodel.items.labelRes
import com.kurtz.homerecord.housemodel.upkeep.UpkeepRepository
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RegressionSeedTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: HomeRepository
    private val strings: (Int) -> String = ApplicationProvider.getApplicationContext<Context>().resources::getString

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = HomeRepository(db, strings)
    }

    @After fun tearDown() = db.close()

    // AC-C1
    @AcceptanceCriterion("AC-C1")
    @Test fun regressionSeedBuildsThreeFloorsWithTwelveRooms() = runTest {
        val homeId = RegressionSeed.seed(db, repo, strings)
        val floors = repo.observeFloors(homeId).first()
        assertThat(floors).hasSize(3)
        assertThat(floors.sumOf { it.roomCount }).isEqualTo(12)
        val plans = floors.map { repo.loadPlan(it.id) }
        assertThat(plans.sumOf { it.placed.size }).isEqualTo(12)
        assertThat(plans.flatMap { it.placed }.sumOf { it.openings.size }).isGreaterThan(5)
        assertThat(plans.flatMap { it.placed }.sumOf { it.stairs.size }).isEqualTo(1)
    }

    // cr:seed-items: the seed reaches Needs attention, filters, the item page and populated lists
    @Test fun regressionSeedFillsItemsWarrantiesRemindersContactsAndDocuments() = runTest {
        val today = LocalDate.of(2026, 10, 29)
        val dir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "seed-docs").apply { deleteRecursively() }
        val homeId = RegressionSeed.seed(db, repo, strings, today = today, documentsDir = dir, pdf = "%PDF-1.4".toByteArray())

        val items = ItemRepository(db, strings, today = { today })
        val rows = items.observeRows(homeId, mapOf(PseudoRooms.EXTERIOR to "Exterior", PseudoRooms.WHOLE_HOUSE to "Whole house")).first()
        assertThat(rows.size).isAtLeast(20)
        assertThat(rows.map { it.roomName }.toSet()).containsAtLeast("Kitchen", "Utility room", "Laundry", "Exterior", "Whole house")
        assertThat(rows.count { it.warranty is WarrantyStatus.Expiring }).isAtLeast(1)
        assertThat(rows.count { it.warranty is WarrantyStatus.Expired }).isAtLeast(1)
        assertThat(rows.count { it.warranty is WarrantyStatus.Active }).isAtLeast(1)

        val summary = items.observeHomeSummary(homeId, emptyMap()).first()
        assertThat(summary.attention.map { it.kind }.toSet())
            .containsExactly(AttentionKind.REMINDER_OVERDUE, AttentionKind.WARRANTY_EXPIRING, AttentionKind.MISSING_MAKE_MODEL)
        assertThat(summary.shutoffCount).isEqualTo(2)

        val furnace = rows.first { it.name == "Furnace" }.id
        assertThat(db.consumableDao().listFor(furnace)).isNotEmpty()
        assertThat(db.serviceRecordDao().listFor(furnace).single().contactId).isNotNull()

        val contacts = UpkeepRepository(db).observeContacts(homeId).first()
        assertThat(contacts.map { it.contact.name }).containsExactly("Dale Harlan", "Maria Soto").inOrder()
        assertThat(contacts.first().itemNames).containsExactly("Furnace")

        val docs = db.documentDao().listFor(homeId)
        assertThat(docs.map { it.mime }.toSet()).containsExactly("application/pdf")
        assertThat(docs.map { it.itemId }).contains(furnace)
        docs.forEach { assertThat(File(it.path).length()).isGreaterThan(0L) }
    }

    // AC-H6: what qa/flows/house-manual/H06-items.yaml asserts on the seeded Items list (cr:qa-seed-wiped)
    @AcceptanceCriterion("AC-H6")
    @Test fun seededItemsGiveTheH06FlowItsGroupsFilterAndSearch() = runTest {
        val today = LocalDate.of(2026, 10, 29)
        val homeId = RegressionSeed.seed(db, repo, strings, today = today)
        val rows = ItemRepository(db, strings, today = { today }).observeRows(homeId, mapOf(PseudoRooms.EXTERIOR to "Exterior")).first()

        val res = ApplicationProvider.getApplicationContext<Context>().resources
        val name: (ItemCategory) -> String = { res.getString(it.labelRes) }
        val groups = ItemListBuilder.build(rows, ItemGrouping.CATEGORY, categoryName = name)
        assertThat(groups.map { it.category?.let(name) }).contains("Appliances")
        val kitchen = ItemListBuilder.build(rows, ItemGrouping.ROOM, categoryName = name).single { it.title == "Kitchen" }
        assertThat(kitchen.count).isEqualTo(5)
        assertThat(kitchen.items.map { it.name }).contains("Refrigerator")
        val expired = ItemListBuilder.build(rows, ItemGrouping.CATEGORY, ItemFilter(warranty = WarrantyFilter.EXPIRED), name)
        assertThat(expired.flatMap { it.items }.map { it.name }).containsExactly("AC condenser", "Gas range")
        val byModel = ItemListBuilder.build(rows, ItemGrouping.CATEGORY, ItemFilter(query = "JGB735"), name)
        assertThat(byModel.flatMap { it.items }.map { it.name }).containsExactly("Gas range")
    }

    @Test fun seedingTwiceReplacesTheHomeAndResetEmptiesIt() = runTest {
        RegressionSeed.seed(db, repo, strings)
        val homeId = RegressionSeed.seed(db, repo, strings)
        assertThat(db.homeDao().observeAll().first()).hasSize(1)
        assertThat(db.itemDao().observeFor(homeId).first().map { it.homeId }.toSet()).containsExactly(homeId)
        RegressionSeed.reset(db)
        assertThat(repo.observeHome().first()).isNull()
    }

    // AC-U11: the jank stage scrolls a 200-row Items list (S7)
    @Test fun longListSeedGivesItemsTwoHundredRowsWithDistinctNames() = runTest {
        val today = LocalDate.of(2026, 10, 29)
        val homeId = RegressionSeed.seedLongList(db, repo, strings, today = today)
        val rows = ItemRepository(db, strings, today = { today }).observeRows(homeId, emptyMap()).first()
        assertThat(rows).hasSize(RegressionSeed.LONG_LIST_ITEMS)
        assertThat(rows.map { it.name }.toSet()).hasSize(RegressionSeed.LONG_LIST_ITEMS)
        assertThat(rows.map { it.name }).containsAtLeast("Furnace", "Wall paint, Kitchen", "Smoke alarm, Bedroom 2")
        assertThat(db.homeDao().observeAll().first()).hasSize(1)
    }
}
