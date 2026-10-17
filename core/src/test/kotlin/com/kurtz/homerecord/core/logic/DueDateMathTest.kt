package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DueDateMathTest {
    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun intervalMustBeOneToTwentyFour() {
        assertFalse(DueDateMath.isValidInterval(0))
        assertTrue(DueDateMath.isValidInterval(1))
        assertTrue(DueDateMath.isValidInterval(24))
        assertFalse(DueDateMath.isValidInterval(25))
    }

    @Test
    fun firstDueWithoutInstallDateIsTodayPlusInterval() {
        assertEquals(LocalDate.of(2027, 1, 17), DueDateMath.firstDue(null, today, 3))
    }

    @Test
    fun firstDueFromRecentInstallDate() {
        assertEquals(LocalDate.of(2026, 12, 1), DueDateMath.firstDue("2026-09-01", today, 3))
    }

    @Test
    fun oldInstallDateRollsForwardByWholeIntervals() {
        // 2020-01-01 + 6 month steps: first one on/after 2026-10-17 is 2027-01-01
        assertEquals(LocalDate.of(2027, 1, 1), DueDateMath.firstDue("2020-01-01", today, 6))
    }

    @Test
    fun badInstallDateFallsBackToToday() {
        assertEquals(LocalDate.of(2026, 11, 17), DueDateMath.firstDue("nope", today, 1))
    }

    @Test
    fun monthEndClampsToShorterMonth() {
        assertEquals(LocalDate.of(2026, 2, 28), DueDateMath.firstDue(null, LocalDate.of(2025, 12, 31), 2))
    }

    @Test
    fun doneAdvancesCalendarAndSeasonalTasks() {
        assertEquals(LocalDate.of(2027, 4, 17), DueDateMath.afterDoneCalendar(today, 6))
        assertEquals(LocalDate.of(2027, 3, 1), DueDateMath.afterDoneSeasonal(today, Season.SPRING))
        // done on the first day of the season moves a full year ahead
        assertEquals(LocalDate.of(2027, 3, 1), DueDateMath.afterDoneSeasonal(LocalDate.of(2026, 3, 1), Season.SPRING))
    }

    @Test
    fun snoozeAddsOneWeekPastTodayOrDueWhicheverIsLater() {
        assertEquals(LocalDate.of(2026, 10, 24), DueDateMath.snooze(today, LocalDate.of(2026, 9, 1)))
        assertEquals(LocalDate.of(2026, 11, 8), DueDateMath.snooze(today, LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun nextSeasonStartIsOnOrAfterFrom() {
        assertEquals(LocalDate.of(2026, 12, 1), DueDateMath.nextSeasonStart(today, Season.WINTER))
        assertEquals(LocalDate.of(2026, 9, 1), DueDateMath.nextSeasonStart(LocalDate.of(2026, 9, 1), Season.FALL))
        assertEquals(LocalDate.of(2027, 6, 1), DueDateMath.nextSeasonStart(today, Season.SUMMER))
    }
}
