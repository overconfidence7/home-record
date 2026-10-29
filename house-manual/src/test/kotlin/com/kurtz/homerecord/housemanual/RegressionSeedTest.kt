package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.qa.RegressionSeed
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.home.AttentionKind
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
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

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = HomeRepository(db)
    }

    @After fun tearDown() = db.close()

    // AC-C1
    @AcceptanceCriterion("AC-C1")
    @Test fun regressionSeedBuildsThreeFloorsWithTwelveRooms() = runTest {
        val homeId = RegressionSeed.seed(db, repo)
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
        val homeId = RegressionSeed.seed(db, repo, today = today, documentsDir = dir, pdf = "%PDF-1.4".toByteArray())

        val items = ItemRepository(db, today = { today })
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

    @Test fun seedingTwiceReplacesTheHomeAndResetEmptiesIt() = runTest {
        RegressionSeed.seed(db, repo)
        val homeId = RegressionSeed.seed(db, repo)
        assertThat(db.homeDao().observeAll().first()).hasSize(1)
        assertThat(db.itemDao().observeFor(homeId).first().map { it.homeId }.toSet()).containsExactly(homeId)
        RegressionSeed.reset(db)
        assertThat(repo.observeHome().first()).isNull()
    }
}
