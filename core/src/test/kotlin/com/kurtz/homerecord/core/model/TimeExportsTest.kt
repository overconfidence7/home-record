package com.kurtz.homerecord.core.model

import com.kurtz.homerecord.core.company.OvertimeRule
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val H = 3_600_000L
private val UTC = ZoneOffset.UTC
private val MONDAY = LocalDate.of(2026, 10, 19)

private fun at(day: Int, hour: Int, min: Int = 0): Long =
    LocalDate.of(2026, 10, day).atStartOfDay(UTC).toInstant().toEpochMilli() + hour * H + min * 60_000L

private fun shift(day: Int, from: Int, to: Int, job: String? = "j1", who: String = "a", breakMin: Int = 0): Shift {
    val s = at(day, from)
    val e = at(day, to)
    val breaks = if (breakMin > 0) listOf(at(day, 12)..at(day, 12, breakMin)) else emptyList()
    return Shift(who, s, e, breaks, listOf(JobSegment(job, s, e)), false, false, false)
}

/** Mon-Thu 8 h, Fri 10 h = 42 h. */
private fun week42(who: String = "a") = (19..22).map { shift(it, 8, 16, who = who) } + shift(23, 7, 17, who = who)

private val names = mapOf("a" to "Ana Ruiz", "b" to "Ben Cole")
private val jobs = mapOf("j1" to "Maple St kitchen", "j2" to "Oak Ave deck")

class HoursReportTest {
    private val weekly = TimesheetRules.from(OvertimeRule.Weekly40)
    private val daily = TimesheetRules.from(OvertimeRule.Daily8)
    private val calif = TimesheetRules.from(OvertimeRule.Daily8Weekly40, californiaDoubleTime = true)
    private val now = at(30, 0)

    // AC-F46
    @Test fun weeklyRuleSplitsOvertimeAfterForty() {
        val r = HoursReport.build(week42(), listOf(MONDAY), names, jobs, weekly, UTC, now)
        val ana = r.byPerson.single()
        assertEquals(42 * H, ana.workedMillis)
        assertEquals(40 * H, ana.regularMillis)
        assertEquals(2 * H, ana.overtimeMillis)
    }

    // AC-F46
    @Test fun dailyRuleCountsTheTenHourDay() {
        val r = HoursReport.build(week42(), listOf(MONDAY), names, jobs, daily, UTC, now)
        assertEquals(2 * H, r.overtimeMillis)
        assertEquals(40 * H, r.regularMillis)
    }

    // AC-F46
    @Test fun californiaDoubleTimePastTwelve() {
        val long = week42().dropLast(1) + shift(23, 6, 20)
        val r = HoursReport.build(long, listOf(MONDAY), names, jobs, calif, UTC, now)
        assertEquals(2 * H, r.doubleMillis)
        assertEquals(4 * H, r.overtimeMillis)
        assertEquals(32 * H + 8 * H, r.regularMillis)
    }

    // AC-F46
    @Test fun totalsMatchTheTimesheetForTheSameWeek() {
        val shifts = week42() + week42("b").map { it.copy(segments = listOf(JobSegment("j2", it.startAt, it.endAt!!))) }
        val r = HoursReport.build(shifts, listOf(MONDAY), names, jobs, weekly, UTC, now)
        val sheet = TimesheetMath.week(shifts.filter { it.memberId == "a" }, weekly, UTC, now)
        assertEquals(sheet.workedMillis, r.byPerson.first { it.id == "a" }.workedMillis)
        assertEquals(sheet.overtimeMillis, r.byPerson.first { it.id == "a" }.overtimeMillis)
        assertEquals(84 * H, r.totalMillis)
        assertEquals(listOf("Ana Ruiz", "Ben Cole"), r.byPerson.map { it.name })
    }

    // AC-F46
    @Test fun overtimeIsPerWeekAcrossTwoWeeks() {
        val next = (26..28).map { shift(it, 8, 18) } // 30 h next week
        val r = HoursReport.build(week42() + next, listOf(MONDAY, MONDAY.plusDays(7)), names, jobs, weekly, UTC, at(31, 0))
        assertEquals(72 * H, r.totalMillis)
        assertEquals(2 * H, r.overtimeMillis)
    }

    // AC-F46
    @Test fun jobHoursSplitBySegmentAndLoseBreaks() {
        val s = at(20, 8)
        val sw = at(20, 12)
        val e = at(20, 16)
        val switched = Shift("a", s, e, listOf(at(20, 10)..at(20, 10, 30)), listOf(JobSegment("j1", s, sw), JobSegment("j2", sw, e)), false, false, false)
        val r = HoursReport.build(listOf(switched), listOf(MONDAY), names, jobs, weekly, UTC, now)
        assertEquals(listOf("Oak Ave deck", "Maple St kitchen"), r.byJob.map { it.name })
        assertEquals(4 * H, r.byJob[0].workedMillis)
        assertEquals(3 * H + 30 * 60_000, r.byJob[1].workedMillis)
    }

    // AC-F46
    @Test fun emptyRangeIsEmpty() {
        val r = HoursReport.build(emptyList(), listOf(MONDAY), names, jobs, weekly, UTC, now)
        assertTrue(r.byPerson.isEmpty() && r.byJob.isEmpty())
        assertEquals(0L, r.totalMillis)
    }
}

class TimeExportsTest {
    private val weekly = TimesheetRules.from(OvertimeRule.Weekly40)
    private val now = at(30, 0)

    // AC-F15
    @Test fun shiftCsvHasOneRowPerShiftWithTheSpecColumns() {
        val rows = week42().map { ShiftExportRow(it, "Ana Ruiz", emptySet(), "40.1,-75.2", "40.1,-75.2") }
        val lines = TimeExports.shiftCsv(rows, jobs, weekly, UTC, now).trimEnd().split("\r\n")
        assertEquals("Person,Job,In,Out,Break minutes,Hours,OT hours,Flags,GPS in,GPS out", lines[0])
        assertEquals(6, lines.size)
        assertEquals("Ana Ruiz,Maple St kitchen,2026-10-19 08:00,2026-10-19 16:00,0,8.00,0.00,,\"40.1,-75.2\",\"40.1,-75.2\"", lines[1])
    }

    // AC-F15
    @Test fun overtimeLandsOnTheLatestShiftAndFlagsAreListed() {
        val rows = week42().mapIndexed { i, s -> ShiftExportRow(s, "Ana Ruiz", if (i == 4) setOf(ShiftFlag.Edited, ShiftFlag.FarFromJob) else emptySet()) }
        val last = TimeExports.shiftCsv(rows, jobs, weekly, UTC, now).trimEnd().split("\r\n").last()
        assertTrue(last, last.contains(",10.00,2.00,Edited FarFromJob,"))
    }

    // AC-F15
    @Test fun breakMinutesComeOffHours() {
        val rows = listOf(ShiftExportRow(shift(20, 8, 16, breakMin = 30), "Ana Ruiz", emptySet()))
        val line = TimeExports.shiftCsv(rows, jobs, weekly, UTC, now).trimEnd().split("\r\n")[1]
        assertTrue(line, line.contains(",30,7.50,"))
    }

    // AC-F15
    @Test fun openShiftHasNoOutTime() {
        val open = Shift("a", at(20, 8), null, emptyList(), listOf(JobSegment("j1", at(20, 8), at(20, 8))), false, false, false)
        val line = TimeExports.shiftCsv(listOf(ShiftExportRow(open, "Ana Ruiz", emptySet())), jobs, weekly, UTC, at(20, 10)).trimEnd().split("\r\n")[1]
        assertTrue(line, line.contains("2026-10-20 08:00,,0,2.00"))
    }

    // AC-F15
    @Test fun quickBooksCsvIsPerPersonJobAndDay() {
        val two = listOf(shift(20, 8, 12), shift(20, 13, 17, job = "j2"), shift(21, 8, 16))
        val lines = TimeExports.quickBooksCsv(two, names, mapOf("j1" to "Cole:Maple St kitchen", "j2" to "Cole:Oak Ave deck"), UTC, now).trimEnd().split("\r\n")
        assertEquals("Customer:Job,Service Item,Hours,Date,Employee", lines[0])
        assertEquals(listOf(
            "Cole:Maple St kitchen,Labor,4.00,2026-10-20,Ana Ruiz",
            "Cole:Oak Ave deck,Labor,4.00,2026-10-20,Ana Ruiz",
            "Cole:Maple St kitchen,Labor,8.00,2026-10-21,Ana Ruiz",
        ), lines.drop(1))
    }

    // AC-F15
    @Test fun quickBooksSplitsAtMidnight() {
        val night = Shift("a", at(20, 22), at(21, 2), emptyList(), listOf(JobSegment("j1", at(20, 22), at(21, 2))), false, false, false)
        val lines = TimeExports.quickBooksCsv(listOf(night), names, mapOf("j1" to "Cole:Maple"), UTC, now).trimEnd().split("\r\n")
        assertEquals(listOf("Cole:Maple,Labor,2.00,2026-10-20,Ana Ruiz", "Cole:Maple,Labor,2.00,2026-10-21,Ana Ruiz"), lines.drop(1))
    }

    // AC-F15
    @Test fun cellsAreQuotedAndFormulasDefused() {
        assertEquals("\"Smith, \"\"Bo\"\"\"", TimeExports.csv("Smith, \"Bo\""))
        assertEquals("'=SUM(A1)", TimeExports.csv("=SUM(A1)"))
        assertEquals("-5", TimeExports.csv("-5"))
    }
}
