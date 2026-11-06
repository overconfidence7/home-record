package com.kurtz.homerecord.housemodel.reminders

import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.ReminderDefault
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

/** The five climate zones a home can pick; each seeds its own seasonal tasks (AC-C49, AC-H12). */
enum class ClimateZone(val id: String) {
    COLD("cold"), MIXED("mixed"), HOT_HUMID("hot_humid"), HOT_DRY("hot_dry"), MARINE("marine");

    companion object {
        fun fromId(id: String?): ClimateZone? = entries.firstOrNull { it.id == id }
    }
}

/** A yearly task for a climate zone, due on the 1st of [month]; [key] names its text in house-model's resources. */
data class SeasonalTask(val key: String, val month: Month)

/** How long Snooze puts a reminder off. */
enum class SnoozeLength { WEEK, MONTH }

/** Where a reminder sits on HM-Reminders. */
enum class ReminderBucket { DUE, UPCOMING, DONE }

/** The dates of one reminder, as stored on `maintenance_task`. [intervalMonths] null means it happens once. */
data class ReminderDates(
    val dueOn: LocalDate,
    val intervalMonths: Int?,
    val lastDoneOn: LocalDate? = null,
    val snoozedUntil: LocalDate? = null,
)

/**
 * Maintenance math (AC-C49): default intervals per item type, seasonal tasks per climate zone, warranty-expiry reminders
 * 60 and 7 days before the end date, and what Done and Snooze do to the dates. Pure; dates in, dates out.
 */
object MaintenanceRule {
    /** Days before a warranty ends that a reminder is due. */
    val WARRANTY_LEAD_DAYS = listOf(60, 7)

    /** A done reminder stays under Done this many days before it is shown as Upcoming again (recurring) or hidden (one-off). */
    const val DONE_SHOWN_DAYS = 14L

    /** A one-off reminder done longer ago than this is no longer listed. */
    const val DONE_KEPT_DAYS = 90L

    /** The reminders a new item of [subtypeId] starts with (furnace filter 3 months, water heater flush 12 months, …). */
    fun defaultsFor(subtypeId: String?): List<ReminderDefault> =
        subtypeId?.let { ItemCategory.subtype(it) }?.reminders.orEmpty()

    /** The first due date of a default reminder seeded [today]. */
    fun firstDue(default: ReminderDefault, today: LocalDate): LocalDate = today.plusMonths(default.intervalMonths.toLong())

    /** Seasonal tasks a home in [zone] gets, in calendar order. */
    fun seasonalTasks(zone: ClimateZone): List<SeasonalTask> = SEASONAL.getValue(zone).sortedBy { it.month }

    /** The next 1st of [month] on or after [today]: the due date of a seasonal task seeded today. */
    fun nextSeasonalDue(month: Month, today: LocalDate): LocalDate {
        val thisYear = LocalDate.of(today.year, month, 1)
        return if (thisYear.isBefore(today)) thisYear.plusYears(1) else thisYear
    }

    /** Warranty-expiry reminder dates for a warranty ending [endsOn]: 60 and 7 days before, leaving out dates already past. */
    fun warrantyReminderDates(endsOn: LocalDate, today: LocalDate): List<LocalDate> =
        WARRANTY_LEAD_DAYS.map { endsOn.minusDays(it.toLong()) }.filter { !it.isBefore(today) }

    /**
     * Done [today]: a recurring reminder rolls forward one interval from today (never from the old due date, so a late
     * filter change does not leave the next one overdue); a one-off keeps its date and is finished. Snooze is cleared.
     */
    fun done(dates: ReminderDates, today: LocalDate): ReminderDates = dates.copy(
        dueOn = dates.intervalMonths?.let { today.plusMonths(it.toLong()) } ?: dates.dueOn,
        lastDoneOn = today,
        snoozedUntil = null,
    )

    /** Snooze from [today] by a week or a month. */
    fun snooze(dates: ReminderDates, today: LocalDate, length: SnoozeLength): ReminderDates = dates.copy(
        snoozedUntil = when (length) {
            SnoozeLength.WEEK -> today.plusWeeks(1)
            SnoozeLength.MONTH -> today.plusMonths(1)
        },
    )

    /** The date the reminder next asks for attention: its due date, or the end of a snooze that runs later. */
    fun effectiveDue(dates: ReminderDates): LocalDate {
        val snoozed = dates.snoozedUntil ?: return dates.dueOn
        return if (snoozed.isAfter(dates.dueOn)) snoozed else dates.dueOn
    }

    /** True when a one-off reminder has been done: it never comes back. */
    fun finished(dates: ReminderDates): Boolean = dates.intervalMonths == null && dates.lastDoneOn != null

    /**
     * The section on HM-Reminders, or null when it is no longer listed (a one-off done more than 90 days ago).
     * Done recently wins over everything; a finished one-off stays Done; otherwise due on or before today is Due now.
     */
    fun bucket(dates: ReminderDates, today: LocalDate): ReminderBucket? {
        val last = dates.lastDoneOn
        val daysSinceDone = last?.let { ChronoUnit.DAYS.between(it, today) }
        if (finished(dates)) return if ((daysSinceDone ?: 0) <= DONE_KEPT_DAYS) ReminderBucket.DONE else null
        if (daysSinceDone != null && daysSinceDone in 0..DONE_SHOWN_DAYS && dates.dueOn.isAfter(today)) return ReminderBucket.DONE
        return if (effectiveDue(dates).isAfter(today)) ReminderBucket.UPCOMING else ReminderBucket.DUE
    }

    /** True when the reminder belongs in today's notification: due on or before today, not snoozed past it, not finished. */
    fun dueToday(dates: ReminderDates, today: LocalDate): Boolean = bucket(dates, today) == ReminderBucket.DUE

    private val SEASONAL: Map<ClimateZone, List<SeasonalTask>> = mapOf(
        ClimateZone.COLD to listOf(
            SeasonalTask("seasonal_test_ac", Month.MAY),
            SeasonalTask("seasonal_check_heating", Month.SEPTEMBER),
            SeasonalTask("seasonal_drain_outdoor_faucets", Month.OCTOBER),
            SeasonalTask("seasonal_check_weatherstripping", Month.OCTOBER),
            SeasonalTask("seasonal_check_ice_dams", Month.JANUARY),
        ),
        ClimateZone.MIXED to listOf(
            SeasonalTask("seasonal_test_ac", Month.APRIL),
            SeasonalTask("seasonal_check_heating", Month.SEPTEMBER),
            SeasonalTask("seasonal_check_weatherstripping", Month.OCTOBER),
            SeasonalTask("seasonal_drain_outdoor_faucets", Month.NOVEMBER),
        ),
        ClimateZone.HOT_HUMID to listOf(
            SeasonalTask("seasonal_test_ac", Month.MARCH),
            SeasonalTask("seasonal_storm_prep", Month.JUNE),
            SeasonalTask("seasonal_check_moisture", Month.JUNE),
            SeasonalTask("seasonal_clear_ac_drain", Month.JULY),
        ),
        ClimateZone.HOT_DRY to listOf(
            SeasonalTask("seasonal_test_ac", Month.MARCH),
            SeasonalTask("seasonal_service_evap_cooler", Month.APRIL),
            SeasonalTask("seasonal_clear_brush", Month.MAY),
            SeasonalTask("seasonal_check_roof_sun", Month.SEPTEMBER),
        ),
        ClimateZone.MARINE to listOf(
            SeasonalTask("seasonal_clear_moss", Month.OCTOBER),
            SeasonalTask("seasonal_check_weatherstripping", Month.OCTOBER),
            SeasonalTask("seasonal_check_moisture", Month.NOVEMBER),
            SeasonalTask("seasonal_test_ac", Month.JUNE),
        ),
    )
}
