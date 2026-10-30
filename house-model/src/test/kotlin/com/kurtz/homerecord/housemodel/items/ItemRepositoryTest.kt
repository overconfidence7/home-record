package com.kurtz.homerecord.housemodel.items

import com.kurtz.homerecord.core.units.Money
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: ItemRepository
    private val changes = mutableListOf<String>()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = ItemRepository(db, now = { 5_000L }, today = { LocalDate.of(2026, 10, 23) }, onChange = { e, id -> changes += "$e/$id" })
    }

    @After fun tearDown() = db.close()

    private fun furnace() = ItemDraft(subtypeId = "furnace", roomId = PseudoRooms.WHOLE_HOUSE, name = " Basement furnace ", model = "  ", price = "$1,299.5")

    @AcceptanceCriterion("AC-C30")
    @Test fun draftNeedsSubtypeRoomAndName() {
        assertThat(ItemDraft().errors()).containsExactly(ItemField.SUBTYPE, ItemField.ROOM, ItemField.NAME)
        assertThat(furnace().errors()).isEmpty()
        assertThat(furnace().copy(price = "abc", purchasedOn = "10/2/2026").errors()).containsExactly(ItemField.PRICE, ItemField.PURCHASED)
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun priceParsesToCents() {
        assertThat(Money.parseCents("$1,299.5")).isEqualTo(129_950L)
        assertThat(Money.parseCents("42")).isEqualTo(4_200L)
        assertThat(Money.parseCents("-1")).isNull()
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun saveTrimsAndRoundTripsThroughDraft() = runTest {
        val id = checkNotNull(repo.save("h1", furnace()))
        val row = checkNotNull(repo.get(id))
        assertThat(row.name).isEqualTo("Basement furnace")
        assertThat(row.model).isNull()
        assertThat(row.category).isEqualTo("HEATING_COOLING")
        assertThat(row.priceCents).isEqualTo(129_950L)
        assertThat(checkNotNull(repo.draftFor(id)).price).isEqualTo("1299.50")
    }

    @AcceptanceCriterion("AC-C35")
    @Test fun newItemSeedsConsumablesAndRemindersFromSubtype() = runTest {
        val id = checkNotNull(repo.save("h1", furnace()))
        assertThat(db.consumableDao().listFor(id).map { it.spec }).containsExactly("Furnace filter")
        val tasks = db.maintenanceTaskDao().listFor("h1")
        val task = tasks.single { it.title == "HVAC service" }
        assertThat(tasks.map { it.title }).contains("Replace furnace filter")
        assertThat(task.dueOn).isEqualTo("2027-10-23")
        assertThat(changes).contains("maintenance_task/${task.id}")
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun editingKeepsDefaultsAndCreatedAt() = runTest {
        val id = checkNotNull(repo.save("h1", furnace()))
        repo.save("h1", checkNotNull(repo.draftFor(id)).copy(name = "Furnace"))
        assertThat(db.consumableDao().listFor(id)).hasSize(1)
        assertThat(checkNotNull(repo.get(id)).name).isEqualTo("Furnace")
    }

    @Test fun invalidDraftSavesNothing() = runTest {
        assertThat(repo.save("h1", ItemDraft())).isNull()
        assertThat(changes).isEmpty()
    }

    @AcceptanceCriterion("AC-H7")
    @Test fun detailCombinesItemRoomWarrantyAndPhotos() = runTest {
        val id = checkNotNull(repo.save("h1", furnace().copy(manufacturedOn = "2016-04-18")))
        db.warrantyDao().upsert(
            com.kurtz.homerecord.core.data.WarrantyEntity(id = "w1", itemId = id, endsOn = "2026-12-01", createdAt = 1, updatedAt = 1),
        )
        val d = checkNotNull(repo.observeDetail(id, "h1", mapOf(PseudoRooms.WHOLE_HOUSE to "Whole house")).first())
        assertThat(d.roomName).isEqualTo("Whole house")
        assertThat(d.category).isEqualTo(com.kurtz.homerecord.core.model.ItemCategory.HEATING_COOLING)
        assertThat(d.subtypeLabel).isEqualTo("Furnace")
        assertThat(d.status).isInstanceOf(com.kurtz.homerecord.core.model.WarrantyStatus.Expiring::class.java)
        repo.delete(id)
        assertThat(repo.observeDetail(id, "h1", emptyMap()).first()).isNull()
    }

    @AcceptanceCriterion("AC-H7")
    @Test fun duplicateCopiesDetailsWithoutPhotos() = runTest {
        val id = checkNotNull(repo.save("h1", furnace().copy(photos = listOf(DraftPhoto(null, "/m/a.jpg", PhotoKind.ITEM)))))
        val copy = checkNotNull(repo.duplicate(id, "(copy)"))
        assertThat(copy).isNotEqualTo(id)
        assertThat(checkNotNull(repo.get(copy)).name).isEqualTo("Basement furnace (copy)")
        assertThat(checkNotNull(repo.get(copy)).priceCents).isEqualTo(129_950L)
        assertThat(db.photoDao().listForItem(copy)).isEmpty()
        assertThat(db.photoDao().listForItem(id)).hasSize(1)
    }

    @AcceptanceCriterion("AC-C33")
    @Test fun registeredToggleUpdatesWarrantyAndQueuesSync() = runTest {
        val id = checkNotNull(repo.save("h1", furnace()))
        repo.setRegistered(id, true) // no warranty yet: nothing to do
        assertThat(changes).doesNotContain("warranty/w1")
        db.warrantyDao().upsert(com.kurtz.homerecord.core.data.WarrantyEntity(id = "w1", itemId = id, createdAt = 1, updatedAt = 1))
        repo.setRegistered(id, true)
        assertThat(checkNotNull(db.warrantyDao().get("w1")).registered).isTrue()
        assertThat(changes).contains("warranty/w1")
    }

    // cr:item-delete-cascade: the delete dialog says "Its photos, reminders and warranty go with it".
    @AcceptanceCriterion("AC-H2")
    @Test fun deleteTakesWarrantyRemindersConsumablesAndPhotosWithIt() = runTest {
        val id = checkNotNull(repo.save("h1", furnace().copy(photos = listOf(DraftPhoto(null, "/m/a.jpg", PhotoKind.ITEM)))))
        val other = checkNotNull(repo.save("h1", furnace().copy(name = "Water heater")))
        db.itemDao().upsert(checkNotNull(repo.get(other)).copy(shutoffItemId = id))
        db.warrantyDao().upsert(com.kurtz.homerecord.core.data.WarrantyEntity(id = "w1", itemId = id, endsOn = "2026-11-01", createdAt = 1, updatedAt = 1))
        db.serviceRecordDao().upsert(com.kurtz.homerecord.core.data.ServiceRecordEntity(id = "s1", itemId = id, date = "2026-01-04", description = "Tune-up", createdAt = 1, updatedAt = 1))
        db.documentDao().upsert(com.kurtz.homerecord.core.data.DocumentEntity(id = "d1", homeId = "h1", itemId = id, name = "Manual", path = "/m/d.pdf", mime = "application/pdf", createdAt = 1, updatedAt = 1))
        val photoId = db.photoDao().listForItem(id).single().id
        val consumableId = db.consumableDao().listFor(id).single().id
        db.maintenanceTaskDao().upsert(com.kurtz.homerecord.core.data.MaintenanceTaskEntity(id = "t-late", homeId = "h1", itemId = id, title = "Replace furnace filter", dueOn = "2026-09-01", createdAt = 1, updatedAt = 1))
        val taskIds = db.maintenanceTaskDao().listForItem(id).map { it.id }
        assertThat(repo.observeHomeSummary("h1", emptyMap()).first().attention.map { it.itemId }).contains(id)
        changes.clear()

        repo.delete(id)

        assertThat(db.warrantyDao().listFor(id)).isEmpty()
        assertThat(db.consumableDao().listFor(id)).isEmpty()
        assertThat(db.serviceRecordDao().listFor(id)).isEmpty()
        assertThat(db.maintenanceTaskDao().listForItem(id)).isEmpty()
        assertThat(db.photoDao().listForItem(id)).isEmpty()
        assertThat(checkNotNull(db.documentDao().get("d1")).itemId).isNull()
        assertThat(checkNotNull(repo.get(other)).shutoffItemId).isNull()
        assertThat(changes).containsAtLeast("item/$id", "warranty/w1", "service_record/s1", "photo/$photoId", "consumable/$consumableId", "document/d1", "item/$other")
        taskIds.forEach { assertThat(changes).contains("maintenance_task/$it") }
        // Needs attention no longer lists the deleted furnace's warranty or reminders.
        val tasksLeft = db.maintenanceTaskDao().listFor("h1").map { it.itemId }.toSet()
        assertThat(tasksLeft).containsExactly(other)
        assertThat(repo.observeHomeSummary("h1", emptyMap()).first().attention.map { it.itemId }).doesNotContain(id)
    }
}
