package com.kurtz.homerecord.core.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// AC-F47: job summary report: hours vs budget, change order total, tasks open/done, days on site, delays
class JobSummaryReportTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val h = 3_600_000L
    private val mon = LocalDate.of(2026, 10, 26)
    private fun at(day: LocalDate, hour: Int) = day.atStartOfDay(zone).toInstant().toEpochMilli() + hour * h
    private val now = at(mon.plusDays(7), 12)

    private val punches = listOf(
        // Marco: Monday 7-15 on Hale with a half-hour break at noon (7.5 h)
        PunchPoint("marco", "hale", PunchKind.IN, at(mon, 7)),
        PunchPoint("marco", "hale", PunchKind.BREAK_START, at(mon, 12)),
        PunchPoint("marco", "hale", PunchKind.BREAK_END, at(mon, 12) + h / 2),
        PunchPoint("marco", null, PunchKind.OUT, at(mon, 15)),
        // Dana: Tuesday 8-12 on Hale, then switches to Ortiz until 16 (4 h on Hale)
        PunchPoint("dana", "hale", PunchKind.IN, at(mon.plusDays(1), 8)),
        PunchPoint("dana", "ortiz", PunchKind.SWITCH, at(mon.plusDays(1), 12)),
        PunchPoint("dana", null, PunchKind.OUT, at(mon.plusDays(1), 16)),
        // Marco on Thursday, a different job only
        PunchPoint("marco", "ortiz", PunchKind.IN, at(mon.plusDays(3), 7)),
        PunchPoint("marco", null, PunchKind.OUT, at(mon.plusDays(3), 9)),
    )

    private fun build(budget: Int?, cos: List<SummaryChangeOrder> = emptyList(), tasks: List<Boolean> = emptyList(), delays: List<DelayDay> = emptyList()) =
        JobSummaryReport.build("hale", budget, punches, cos, tasks, delays, zone, now)

    @Test fun hoursAgainstBudgetAndDaysOnSite() {
        val s = build(budget = 10)
        assertEquals(11 * h + h / 2, s.workedMillis)
        assertEquals(10 * h, s.budgetMillis)
        assertEquals(h + h / 2, s.overBudgetMillis)
        assertEquals(0L, s.remainingMillis)
        assertEquals(2, s.daysOnSite)
        assertEquals(mon, s.firstDay)
        assertEquals(mon.plusDays(1), s.lastDay)
    }

    @Test fun noBudgetMeansNoComparison() {
        val s = build(budget = null)
        assertNull(s.budgetMillis)
        assertNull(s.remainingMillis)
        assertEquals(0L, s.overBudgetMillis)
        assertNull(build(budget = 0).budgetMillis)
        assertEquals(28 * h + h / 2, build(budget = 40).remainingMillis)
    }

    @Test fun changeOrdersCountSignedTotalApartFromDrafts() {
        val s = build(
            budget = 40,
            cos = listOf(
                SummaryChangeOrder(ChangeOrderStatus.SIGNED, 48_000),
                SummaryChangeOrder(ChangeOrderStatus.SIGNED, 12_550),
                SummaryChangeOrder(ChangeOrderStatus.DRAFT, 9_900),
                SummaryChangeOrder(ChangeOrderStatus.DECLINED, 30_000),
            ),
        )
        assertEquals(2, s.signedCount)
        assertEquals(60_550L, s.signedCents)
        assertEquals(1, s.draftCount)
        assertEquals(9_900L, s.draftCents)
        assertEquals(1, s.declinedCount)
    }

    @Test fun tasksAndDelaysOfThisJobOnly() {
        val s = build(
            budget = 40,
            tasks = listOf(true, true, false, true, false),
            delays = listOf(
                DelayDay("hale", mon, listOf(LogDelay(DelayReason.WEATHER, "Rain"), LogDelay(DelayReason.MATERIALS))),
                DelayDay("hale", mon.plusDays(2), listOf(LogDelay(DelayReason.WEATHER))),
                DelayDay("hale", mon.plusDays(3), emptyList()),
                DelayDay("ortiz", mon, listOf(LogDelay(DelayReason.CLIENT))),
            ),
        )
        assertEquals(3, s.tasksDone)
        assertEquals(2, s.tasksOpen)
        assertEquals(2, s.delayDays)
        assertEquals(listOf(DelayReason.WEATHER to 2, DelayReason.MATERIALS to 1), s.delaysByReason)
    }
}
