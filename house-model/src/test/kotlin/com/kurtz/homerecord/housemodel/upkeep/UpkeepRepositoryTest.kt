package com.kurtz.homerecord.housemodel.upkeep

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.model.ConsumableKind
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
class UpkeepRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: UpkeepRepository
    private val changes = mutableListOf<String>()
    private var clock = 1_000L

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = UpkeepRepository(db, now = { clock++ }, onChange = { e, id -> changes += "$e/$id" })
        db.itemDao().upsert(ItemEntity(id = "i1", homeId = "h1", category = "HEATING_COOLING", subtype = "furnace", name = "Furnace", createdAt = 1, updatedAt = 1))
    }

    @After fun tearDown() = db.close()

    @AcceptanceCriterion("AC-C35")
    @Test fun consumableNeedsSpecQuantityAndHttpUrl() {
        assertThat(ConsumableDraft().errors()).containsExactly(ConsumableField.SPEC)
        val bad = ConsumableDraft(spec = "16x25x1 MERV 8", quantity = "0", reorderUrl = "amazon.com/filter")
        assertThat(bad.errors()).containsExactly(ConsumableField.QUANTITY, ConsumableField.URL)
        assertThat(bad.copy(quantity = "2", reorderUrl = "https://example.com/f").errors()).isEmpty()
    }

    @AcceptanceCriterion("AC-C35")
    @Test fun consumableSavesEditsAndDeletes() = runTest {
        val id = checkNotNull(repo.saveConsumable("i1", ConsumableDraft(kind = ConsumableKind.FILTER, spec = " 16x25x1 MERV 8 ", quantity = "2")))
        val row = repo.observeConsumables("i1").first().single()
        assertThat(row.spec).isEqualTo("16x25x1 MERV 8")
        assertThat(row.quantity).isEqualTo(2)
        repo.saveConsumable("i1", ConsumableDraft(id = id, kind = ConsumableKind.FILTER, spec = "20x25x1", quantity = "2"))
        assertThat(repo.observeConsumables("i1").first().single().spec).isEqualTo("20x25x1")
        repo.deleteConsumable(id)
        assertThat(repo.observeConsumables("i1").first()).isEmpty()
        assertThat(changes).contains("consumable/$id")
        assertThat(repo.saveConsumable("i1", ConsumableDraft())).isNull()
    }

    @AcceptanceCriterion("AC-C36")
    @Test fun serviceHistoryIsNewestFirst() = runTest {
        repo.saveService("i1", ServiceDraft(date = "2025-03-01", description = "Tune-up"))
        repo.saveService("i1", ServiceDraft(date = "2026-09-12", description = "Replaced igniter", cost = "$180.50"))
        repo.saveService("i1", ServiceDraft(date = "2026-01-05", description = "Filter swap"))
        val rows = repo.observeService("i1").first()
        assertThat(rows.map { it.date }).containsExactly("2026-09-12", "2026-01-05", "2025-03-01").inOrder()
        assertThat(rows.first().costCents).isEqualTo(18_050L)
    }

    @AcceptanceCriterion("AC-C36")
    @Test fun serviceNeedsDateAndDescription() {
        assertThat(ServiceDraft().errors()).containsExactly(ServiceField.DATE, ServiceField.DESCRIPTION)
        assertThat(ServiceDraft(date = "2026-13-40", description = "x", cost = "abc").errors()).containsExactly(ServiceField.DATE, ServiceField.COST)
    }

    @AcceptanceCriterion("AC-C39")
    @Test fun contactsLinkToServiceAndSurviveUnlink() = runTest {
        val cid = checkNotNull(repo.saveContact("h1", ContactDraft(name = "Zed Plumbing", phone = " 555-0100 ", email = "zed@example.com")))
        repo.saveContact("h1", ContactDraft(name = "alpha HVAC", trade = "HVAC"))
        repo.saveService("i1", ServiceDraft(date = "2026-09-12", description = "Igniter", contactId = cid))
        val rows = repo.observeContacts("h1").first()
        assertThat(rows.map { it.contact.name }).containsExactly("alpha HVAC", "Zed Plumbing").inOrder()
        val zed = rows.last()
        assertThat(zed.contact.phone).isEqualTo("555-0100")
        assertThat(zed.serviceCount).isEqualTo(1)
        assertThat(zed.itemIds).containsExactly("i1")
        assertThat(zed.itemNames).containsExactly("Furnace")

        repo.deleteContact(cid)
        assertThat(repo.observeContacts("h1").first().map { it.contact.name }).containsExactly("alpha HVAC")
        assertThat(repo.observeService("i1").first().single().contactId).isNull()
    }

    @AcceptanceCriterion("AC-C39")
    @Test fun contactNeedsNameAndValidEmail() {
        assertThat(ContactDraft().errors()).containsExactly(ContactField.NAME)
        assertThat(ContactDraft(name = "A", email = "nope").errors()).containsExactly(ContactField.EMAIL)
        assertThat(ContactDraft(name = "A", email = "a@b.co").errors()).isEmpty()
    }
}
