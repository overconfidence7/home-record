package com.kurtz.homerecord.housemodel.reminders

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import java.time.LocalDate
import java.time.Month
import org.junit.Test

// AC-C49
class MaintenanceRuleTest {
    private val today = LocalDate.parse("2026-11-06")
    private fun d(s: String) = LocalDate.parse(s)

    @Test @AcceptanceCriterion("AC-C49")
    fun defaultIntervalsPerItemType() {
        fun months(subtype: String) = MaintenanceRule.defaultsFor(subtype).associate { it.titleKey to it.intervalMonths }
        assertThat(months("furnace")).containsEntry("replace_furnace_filter", 3)
        assertThat(months("furnace")).containsEntry("hvac_service", 12)
        assertThat(months("water_heater")).containsEntry("flush_water_heater", 12)
        assertThat(months("water_softener")).containsEntry("add_softener_salt", 2)
        assertThat(months("smoke_detector")).containsEntry("replace_smoke_detector_battery", 12)
        assertThat(months("dryer")).containsEntry("clean_dryer_vent", 12)
        assertThat(months("gutters")).containsEntry("clean_gutters", 6)
        assertThat(months("sump_pump")).containsEntry("test_sump_pump", 3)
        assertThat(MaintenanceRule.defaultsFor("tile")).isEmpty()
        assertThat(MaintenanceRule.defaultsFor(null)).isEmpty()
        val filter = MaintenanceRule.defaultsFor("furnace").first { it.titleKey == "replace_furnace_filter" }
        assertThat(MaintenanceRule.firstDue(filter, today)).isEqualTo(d("2027-02-06"))
    }

    @Test @AcceptanceCriterion("AC-C49")
    fun everyClimateZoneHasSeasonalTasksDueOnTheNextFirstOfTheMonth() {
        ClimateZone.entries.forEach { zone -> assertThat(MaintenanceRule.seasonalTasks(zone)).isNotEmpty() }
        assertThat(MaintenanceRule.seasonalTasks(ClimateZone.COLD).map { it.key }).contains("seasonal_drain_outdoor_faucets")
        assertThat(MaintenanceRule.seasonalTasks(ClimateZone.HOT_HUMID).map { it.key }).doesNotContain("seasonal_drain_outdoor_faucets")
        assertThat(MaintenanceRule.seasonalTasks(ClimateZone.MIXED).map { it.month }).isInOrder()
        assertThat(MaintenanceRule.nextSeasonalDue(Month.DECEMBER, today)).isEqualTo(d("2026-12-01"))
        assertThat(MaintenanceRule.nextSeasonalDue(Month.OCTOBER, today)).isEqualTo(d("2027-10-01"))
        assertThat(MaintenanceRule.nextSeasonalDue(Month.NOVEMBER, d("2026-11-01"))).isEqualTo(d("2026-11-01"))
        assertThat(ClimateZone.fromId("hot_dry")).isEqualTo(ClimateZone.HOT_DRY)
        assertThat(ClimateZone.fromId("tundra")).isNull()
    }

    @Test @AcceptanceCriterion("AC-C49")
    fun warrantyRemindersSixtyAndSevenDaysBeforeTheEnd() {
        assertThat(MaintenanceRule.warrantyReminderDates(d("2027-03-01"), today)).containsExactly(d("2026-12-31"), d("2027-02-22")).inOrder()
        // 30 days out: the 60-day reminder is already past, only the 7-day one is left.
        assertThat(MaintenanceRule.warrantyReminderDates(d("2026-12-06"), today)).containsExactly(d("2026-11-29"))
        assertThat(MaintenanceRule.warrantyReminderDates(d("2026-11-10"), today)).isEmpty()
    }

    @Test @AcceptanceCriterion("AC-C49")
    fun doneRollsARecurringReminderForwardFromToday() {
        val late = ReminderDates(dueOn = d("2026-10-01"), intervalMonths = 3, snoozedUntil = d("2026-11-10"))
        val after = MaintenanceRule.done(late, today)
        assertThat(after.dueOn).isEqualTo(d("2027-02-06"))
        assertThat(after.lastDoneOn).isEqualTo(today)
        assertThat(after.snoozedUntil).isNull()
        assertThat(MaintenanceRule.bucket(after, today)).isEqualTo(ReminderBucket.DONE)
        assertThat(MaintenanceRule.bucket(after, today.plusDays(15))).isEqualTo(ReminderBucket.UPCOMING)
        assertThat(MaintenanceRule.bucket(after, d("2027-02-06"))).isEqualTo(ReminderBucket.DUE)
    }

    @Test @AcceptanceCriterion("AC-C49")
    fun doneFinishesAOneOffReminder() {
        val once = ReminderDates(dueOn = d("2026-11-01"), intervalMonths = null)
        val after = MaintenanceRule.done(once, today)
        assertThat(after.dueOn).isEqualTo(d("2026-11-01"))
        assertThat(MaintenanceRule.finished(after)).isTrue()
        assertThat(MaintenanceRule.bucket(after, today.plusDays(60))).isEqualTo(ReminderBucket.DONE)
        assertThat(MaintenanceRule.bucket(after, today.plusDays(91))).isNull()
        assertThat(MaintenanceRule.dueToday(after, today)).isFalse()
    }

    @Test @AcceptanceCriterion("AC-C49")
    fun snoozeAWeekOrAMonthHidesItFromDueUntilThen() {
        val due = ReminderDates(dueOn = d("2026-11-01"), intervalMonths = 12)
        assertThat(MaintenanceRule.bucket(due, today)).isEqualTo(ReminderBucket.DUE)
        val week = MaintenanceRule.snooze(due, today, SnoozeLength.WEEK)
        assertThat(week.snoozedUntil).isEqualTo(d("2026-11-13"))
        assertThat(week.dueOn).isEqualTo(d("2026-11-01"))
        assertThat(MaintenanceRule.bucket(week, today)).isEqualTo(ReminderBucket.UPCOMING)
        assertThat(MaintenanceRule.bucket(week, d("2026-11-13"))).isEqualTo(ReminderBucket.DUE)
        val month = MaintenanceRule.snooze(due, d("2027-01-31"), SnoozeLength.MONTH)
        assertThat(month.snoozedUntil).isEqualTo(d("2027-02-28"))
        assertThat(MaintenanceRule.effectiveDue(month)).isEqualTo(d("2027-02-28"))
        // A snooze that ends before the due date does not pull it earlier.
        assertThat(MaintenanceRule.effectiveDue(ReminderDates(d("2027-05-01"), 12, snoozedUntil = d("2027-01-01")))).isEqualTo(d("2027-05-01"))
    }

    @Test @AcceptanceCriterion("AC-C49")
    fun dueOnTodayIsDueNowAndTomorrowIsUpcoming() {
        assertThat(MaintenanceRule.bucket(ReminderDates(today, 6), today)).isEqualTo(ReminderBucket.DUE)
        assertThat(MaintenanceRule.bucket(ReminderDates(today.plusDays(1), 6), today)).isEqualTo(ReminderBucket.UPCOMING)
        assertThat(MaintenanceRule.dueToday(ReminderDates(today.minusDays(3), null), today)).isTrue()
    }
}
