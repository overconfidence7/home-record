package com.kurtz.homerecord.housemodel.reminders

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.MaintenanceTaskEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// T072c (AC-C50): the daily 08:00 check, its one summary and when the permission is asked.
class DailyReminderCheckTest {
    private val zone = ZoneId.of("America/New_York")

    private fun at(text: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), zone)

    private fun task(id: String, title: String, dueOn: String, snoozedUntil: String? = null) =
        MaintenanceTaskEntity(id = id, homeId = "h1", title = title, dueOn = dueOn, snoozedUntil = snoozedUntil, createdAt = 1, updatedAt = 1)

    @Test @AcceptanceCriterion("AC-C50")
    fun theNextRunIsTheComing0800Local() {
        assertThat(DailyReminderCheck.delayUntilNext(at("2026-11-06T06:30"))).isEqualTo(Duration.ofMinutes(90))
        assertThat(DailyReminderCheck.delayUntilNext(at("2026-11-06T08:00"))).isEqualTo(Duration.ofHours(24))
        assertThat(DailyReminderCheck.delayUntilNext(at("2026-11-06T21:15"))).isEqualTo(Duration.ofMinutes(10 * 60 + 45))
        // Clocks go back on 2026-11-01 (a 25-hour night): still 08:00 local
        assertThat(DailyReminderCheck.delayUntilNext(at("2026-10-31T08:00"))).isEqualTo(Duration.ofHours(25))
        // Clocks go forward on 2027-03-14 (a 23-hour night)
        assertThat(DailyReminderCheck.delayUntilNext(at("2027-03-13T08:00"))).isEqualTo(Duration.ofHours(23))
    }

    @Test @AcceptanceCriterion("AC-C50")
    fun oneSummaryNamesTheOldestThreeAndCountsTheRest() {
        assertThat(DailyReminderCheck.digest(emptyList())).isNull()
        val digest = DailyReminderCheck.digest(
            listOf(
                task("a", "Flush the water heater", "2026-10-20"),
                task("b", "Replace the furnace filter", "2026-09-01"),
                task("c", "Test smoke alarms", "2026-08-01", snoozedUntil = "2026-11-01"),
                task("d", "Clean the dryer vent", "2026-10-01"),
                task("e", "Check the sump pump", "2026-11-06"),
            ),
        )
        assertThat(digest).isEqualTo(
            ReminderDigest(5, listOf("Replace the furnace filter", "Clean the dryer vent", "Flush the water heater")),
        )
        assertThat(digest?.more).isEqualTo(2)
    }

    @Test @AcceptanceCriterion("AC-C50")
    fun thePermissionIsAskedOnlyOnceAReminderExists() {
        assertThat(DailyReminderCheck.shouldAskPermission(33, granted = false, askedBefore = false, reminderCount = 0)).isFalse()
        assertThat(DailyReminderCheck.shouldAskPermission(33, granted = false, askedBefore = false, reminderCount = 1)).isTrue()
        assertThat(DailyReminderCheck.shouldAskPermission(33, granted = false, askedBefore = true, reminderCount = 4)).isFalse()
        assertThat(DailyReminderCheck.shouldAskPermission(35, granted = true, askedBefore = false, reminderCount = 4)).isFalse()
        // Android 12 and older grant notifications without asking
        assertThat(DailyReminderCheck.shouldAskPermission(32, granted = false, askedBefore = false, reminderCount = 4)).isFalse()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderCheckTest {
    private class FakeNotices : ReminderNotices {
        var shown: ReminderDigest? = null
        var cleared = 0
        override fun show(digest: ReminderDigest) { shown = digest }
        override fun clear() { cleared++ }
    }

    @Test @AcceptanceCriterion("AC-C50")
    fun dueRemindersOfEveryHomeMakeOneNotificationAndNoneClearsIt() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = ReminderRepository(db, context::getString, now = { 1_000L }, today = { LocalDate.parse("2026-11-06") })
            db.homeDao().upsert(HomeEntity(id = "h1", name = "Maple St", createdAt = 1, updatedAt = 1))
            db.homeDao().upsert(HomeEntity(id = "h2", name = "Lake cabin", createdAt = 1, updatedAt = 1))
            val notices = FakeNotices()
            val check = ReminderCheck({ listOf("h1", "h2") }, repo, notices)

            assertThat(check.run()).isNull()
            assertThat(notices.cleared).isEqualTo(1)

            repo.add("h1", ReminderDraft("Replace the furnace filter", LocalDate.parse("2026-11-01")))
            repo.add("h2", ReminderDraft("Drain the outdoor faucets", LocalDate.parse("2026-11-06")))
            repo.add("h2", ReminderDraft("Clean the gutters", LocalDate.parse("2026-12-01"))) // not due yet
            assertThat(check.run()).isEqualTo(ReminderDigest(2, listOf("Replace the furnace filter", "Drain the outdoor faucets")))
            assertThat(notices.shown?.count).isEqualTo(2)
        } finally {
            db.close()
        }
    }
}
