package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Season
import java.time.LocalDate
import java.time.Month

/** Due-date rules for maintenance tasks (F15). Dates are ISO `yyyy-MM-dd` strings. */
object DueDateMath {
    const val MIN_INTERVAL_MONTHS = 1
    const val MAX_INTERVAL_MONTHS = 24
    const val SNOOZE_DAYS = 7L

    fun isValidInterval(months: Int): Boolean = months in MIN_INTERVAL_MONTHS..MAX_INTERVAL_MONTHS

    /**
     * AC-81: first due date of a new interval reminder is (installedOn, else today) + [months].
     * An install date long ago would land in the past, so the result rolls forward by whole
     * intervals until it is today or later.
     */
    fun firstDue(installedOn: String?, today: LocalDate, months: Int): LocalDate {
        val base = installedOn?.let { EquipmentParse.date(it) } ?: today
        var due = base.plusMonths(months.toLong())
        var steps = 1
        while (due.isBefore(today)) {
            steps++
            due = base.plusMonths(months.toLong() * steps)
        }
        return due
    }

    /** AC-82: Done on a calendar task advances nextDue to today + interval. */
    fun afterDoneCalendar(today: LocalDate, months: Int): LocalDate = today.plusMonths(months.toLong())

    /** AC-82: Done on a seasonal task advances to the next occurrence of its season, strictly after today. */
    fun afterDoneSeasonal(today: LocalDate, season: Season): LocalDate = nextSeasonStart(today.plusDays(1), season)

    /** AC-82: Snooze pushes nextDue one week past today (or past its current value if that is later). */
    fun snooze(today: LocalDate, currentDue: LocalDate): LocalDate =
        maxOf(today, currentDue).plusDays(SNOOZE_DAYS)

    /** First day of [season] on or after [from] (spring Mar 1, summer Jun 1, fall Sep 1, winter Dec 1). */
    fun nextSeasonStart(from: LocalDate, season: Season): LocalDate {
        val month = when (season) {
            Season.SPRING -> Month.MARCH
            Season.SUMMER -> Month.JUNE
            Season.FALL -> Month.SEPTEMBER
            Season.WINTER -> Month.DECEMBER
        }
        val thisYear = LocalDate.of(from.year, month, 1)
        return if (thisYear.isBefore(from)) thisYear.plusYears(1) else thisYear
    }
}

internal object EquipmentParse {
    fun date(value: String): LocalDate? = EquipmentRule.parseDate(value.trim())
}
