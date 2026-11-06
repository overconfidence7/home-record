package com.kurtz.homerecord.housemodel.reminders

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
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
@Config(sdk = [35])
class ReminderRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: ReminderRepository
    private lateinit var context: Context
    private var day = LocalDate.parse("2026-11-06")
    private val changes = mutableListOf<String>()

    @Before fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = ReminderRepository(db, context::getString, now = { 1_000L }, today = { day }, onChange = { e, id -> changes += "$e/$id" })
        db.homeDao().upsert(HomeEntity(id = "h1", name = "Maple St", createdAt = 1, updatedAt = 1))
        db.itemDao().upsert(ItemEntity(id = "i1", homeId = "h1", category = "WATER", subtype = "water_heater", name = "Water heater", createdAt = 1, updatedAt = 1))
    }

    @After fun tearDown() = db.close()

    @Test @AcceptanceCriterion("AC-H12")
    fun pickingAClimateZoneSeedsItsSeasonalTasksAndAnotherZoneReplacesThem() = runTest {
        repo.setClimateZone("h1", ClimateZone.COLD)
        val cold = repo.observe("h1").first()
        assertThat(cold.map { it.title }).contains("Shut off and drain outdoor faucets")
        assertThat(cold).hasSize(MaintenanceRule.seasonalTasks(ClimateZone.COLD).size)
        assertThat(cold.all { it.seasonal && it.intervalMonths == 12 }).isTrue()
        assertThat(cold.first { it.title.startsWith("Shut off") }.dueOn).isEqualTo(LocalDate.parse("2027-10-01"))
        assertThat(db.homeDao().get("h1")?.climateZone).isEqualTo("cold")

        repo.setClimateZone("h1", ClimateZone.COLD) // same zone: nothing doubles
        assertThat(repo.observe("h1").first()).hasSize(cold.size)

        repo.add("h1", ReminderDraft("Reseal the deck", LocalDate.parse("2027-05-01")))
        repo.setClimateZone("h1", ClimateZone.HOT_HUMID)
        val titles = repo.observe("h1").first().map { it.title }
        assertThat(titles).contains("Get ready for storm season")
        assertThat(titles).doesNotContain("Shut off and drain outdoor faucets")
        assertThat(titles).contains("Reseal the deck")
    }

    @Test @AcceptanceCriterion("AC-H12")
    fun doneRollsTheNextDueDateForwardAndSnoozeMovesItToUpcoming() = runTest {
        val filter = repo.add("h1", ReminderDraft("Replace furnace filter", LocalDate.parse("2026-11-01"), intervalMonths = 3, itemId = "i1"))
        val smoke = repo.add("h1", ReminderDraft("Test smoke alarms", LocalDate.parse("2026-11-05"), intervalMonths = 6))
        checkNotNull(filter); checkNotNull(smoke)
        assertThat(repo.observe("h1").first().map { it.bucket }).containsExactly(ReminderBucket.DUE, ReminderBucket.DUE)
        assertThat(repo.dueNow("h1")).hasSize(2)

        repo.done(filter)
        repo.snooze(smoke, SnoozeLength.WEEK)
        val rows = repo.observe("h1").first().associateBy { it.id }
        assertThat(rows.getValue(filter).bucket).isEqualTo(ReminderBucket.DONE)
        assertThat(rows.getValue(filter).dueOn).isEqualTo(LocalDate.parse("2027-02-06"))
        assertThat(rows.getValue(filter).itemName).isEqualTo("Water heater")
        assertThat(rows.getValue(smoke).bucket).isEqualTo(ReminderBucket.UPCOMING)
        assertThat(repo.dueNow("h1")).isEmpty()

        day = LocalDate.parse("2026-11-13")
        assertThat(repo.dueNow("h1").map { it.id }).containsExactly(smoke)
        assertThat(changes.filter { it.startsWith("maintenance_task/") }).hasSize(4)
    }

    @Test @AcceptanceCriterion("AC-C49")
    fun handWrittenReminderNeedsATitleAndADate() = runTest {
        assertThat(repo.add("h1", ReminderDraft("", LocalDate.parse("2027-01-01")))).isNull()
        assertThat(repo.add("h1", ReminderDraft("Clean the chimney", null))).isNull()
        assertThat(repo.add("h1", ReminderDraft("Clean the chimney", LocalDate.parse("2027-01-01"), intervalMonths = 0))).isNull()
        assertThat(repo.add("h1", ReminderDraft("  Clean the chimney ", LocalDate.parse("2027-01-01"), intervalMonths = 12))).isNotNull()
        assertThat(repo.observe("h1").first().single().title).isEqualTo("Clean the chimney")
        assertThat(repo.count("h1")).isEqualTo(1)
    }

    @Test @AcceptanceCriterion("AC-C49")
    fun warrantyRemindersFollowTheWarrantyEndDate() = runTest {
        repo.syncWarrantyReminders("h1", "i1", "Water heater", LocalDate.parse("2027-03-01"))
        var rows = repo.observe("h1").first()
        assertThat(rows.map { it.dueOn }).containsExactly(LocalDate.parse("2026-12-31"), LocalDate.parse("2027-02-22")).inOrder()
        assertThat(rows.first().title).isEqualTo("Warranty ends soon: Water heater")
        assertThat(rows.all { it.intervalMonths == null && it.itemId == "i1" }).isTrue()

        // The end date moves 30 days out: only the 7-day reminder is still ahead, rewritten in place.
        repo.syncWarrantyReminders("h1", "i1", "Water heater", LocalDate.parse("2026-12-06"))
        rows = repo.observe("h1").first()
        assertThat(rows.map { it.id }).containsExactly(ReminderRepository.warrantyReminderId("i1", 7))
        assertThat(rows.single().dueOn).isEqualTo(LocalDate.parse("2026-11-29"))

        repo.syncWarrantyReminders("h1", "i1", "Water heater", null)
        assertThat(repo.observe("h1").first()).isEmpty()
    }

    @Test fun everySeasonalKeyHasItsOwnText() {
        val keys = ClimateZone.entries.flatMap { MaintenanceRule.seasonalTasks(it) }.map { it.key }.toSet()
        assertThat(seasonalTitles.keys).containsAtLeastElementsIn(keys)
        ClimateZone.entries.forEach { assertThat(context.getString(it.labelRes())).isNotEmpty() }
    }
}
