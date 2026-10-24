package com.kurtz.homerecord.core.model

import com.kurtz.homerecord.core.company.OvertimeRule
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val H = 3_600_000L
private const val M = 60_000L
private val UTC = ZoneOffset.UTC

private fun at(day: Int, hour: Int, min: Int = 0): Long =
    LocalDate.of(2026, 10, day).atStartOfDay(UTC).toInstant().toEpochMilli() + hour * H + min * M

private fun p(kind: String, t: Long, job: String? = "j1", far: Boolean = false, edited: Boolean = false, member: String = "a") =
    ShiftPunch(member, job, kind, t, far, edited)

private fun day(day: Int, from: Int, to: Int, breakMin: Int = 0): List<ShiftPunch> = buildList {
    add(p(PunchKind.IN, at(day, from)))
    if (breakMin > 0) { add(p(PunchKind.BREAK_START, at(day, 12))); add(p(PunchKind.BREAK_END, at(day, 12, breakMin))) }
    add(p(PunchKind.OUT, at(day, to)))
}

class PunchDistanceTest {
    private val job = 40.0 to -75.0

    // AC-F9
    @Test fun nearFixIsNotFlagged() {
        val fix = GeoFix(40.0005, -75.0, 10f) // ~180 ft
        assertFalse(PunchDistance.isFarFromJob(fix, job.first, job.second, 500))
    }

    // AC-F9
    @Test fun farFixIsFlagged() {
        val fix = GeoFix(40.01, -75.0, 10f) // ~3650 ft
        assertTrue(PunchDistance.isFarFromJob(fix, job.first, job.second, 500))
    }

    // AC-F9
    @Test fun thresholdIsTheCompanySetting() {
        val fix = GeoFix(40.01, -75.0)
        assertFalse(PunchDistance.isFarFromJob(fix, job.first, job.second, 5_000))
    }

    // AC-F9
    @Test fun noFixOrNoJobLocationNeverFlags() {
        assertFalse(PunchDistance.isFarFromJob(null, 40.0, -75.0, 500))
        assertFalse(PunchDistance.isFarFromJob(GeoFix(41.0, -75.0), null, null, 500))
    }

    // AC-F9
    @Test fun reasonTextUsesPickerLabelOrTypedOther() {
        assertEquals("Supply house", PunchDistance.reasonText(FarReason.SupplyHouse, ""))
        assertEquals("Parts run", PunchDistance.reasonText(FarReason.Other, "  Parts run "))
        assertNull(PunchDistance.reasonText(FarReason.Other, "  "))
        assertNull(PunchDistance.reasonText(null, "x"))
    }
}

class ShiftBuilderTest {
    // AC-F10
    @Test fun inBreakOutMakesOneShiftWithOneBreak() {
        val s = ShiftBuilder.build(day(5, 8, 16, breakMin = 30), at(6, 0)).single()
        assertEquals(30 * M, s.breakMillis())
        assertEquals(7 * H + 30 * M, s.workedMillis(at(6, 0)))
    }

    // AC-F10
    @Test fun switchSplitsJobSegmentsInsideOneShift() {
        val punches = listOf(p(PunchKind.IN, at(5, 8)), p(PunchKind.SWITCH, at(5, 11), job = "j2"), p(PunchKind.OUT, at(5, 16), job = "j2"))
        val s = ShiftBuilder.build(punches, at(6, 0)).single()
        assertEquals(listOf("j1", "j2"), s.segments.map { it.jobId })
        assertEquals(3 * H, s.segments[0].endAt - s.segments[0].startAt)
    }

    // AC-F12
    @Test fun openShiftPast14HoursIsClosedAt14AndFlagged() {
        val s = ShiftBuilder.build(listOf(p(PunchKind.IN, at(5, 6))), at(6, 12)).single()
        assertTrue(s.autoClosed)
        assertEquals(at(5, 20), s.endAt)
    }

    // AC-F12
    @Test fun openShiftUnder14HoursStaysOpen() {
        val s = ShiftBuilder.build(listOf(p(PunchKind.IN, at(5, 6))), at(5, 10)).single()
        assertNull(s.endAt)
        assertFalse(s.autoClosed)
        assertEquals(4 * H, s.elapsedMillis(at(5, 10)))
    }

    // AC-F12
    @Test fun inWithoutOutThenNextInClosesThePreviousOne() {
        val punches = listOf(p(PunchKind.IN, at(5, 8)), p(PunchKind.IN, at(6, 8)), p(PunchKind.OUT, at(6, 12)))
        val shifts = ShiftBuilder.build(punches, at(7, 0))
        assertEquals(2, shifts.size)
        assertTrue(shifts[0].autoClosed)
        assertEquals(at(5, 22), shifts[0].endAt)
    }
}

class ShiftFlagsTest {
    private fun flags(punches: List<ShiftPunch>, breakRule: Boolean = false, now: Long = at(30, 0)): List<Set<ShiftFlag>> =
        ShiftFlags.flags(ShiftBuilder.build(punches, now), breakRule, now)

    // AC-F12
    @Test fun missingClockOutIsFlagged() {
        assertEquals(setOf(ShiftFlag.MissingClockOut), flags(listOf(p(PunchKind.IN, at(5, 6)))).single())
    }

    // AC-F12
    @Test fun farFromJobAndEditedAreFlagged() {
        val f = flags(listOf(p(PunchKind.IN, at(5, 8), far = true), p(PunchKind.OUT, at(5, 12), edited = true))).single()
        assertEquals(setOf(ShiftFlag.FarFromJob, ShiftFlag.Edited), f)
    }

    // AC-F12
    @Test fun noBreakOverSixHoursOnlyWhenTheRuleIsOn() {
        val punches = day(5, 7, 15)
        assertTrue(ShiftFlag.NoBreak in flags(punches, breakRule = true).single())
        assertTrue(flags(punches, breakRule = false).single().isEmpty())
    }

    // AC-F12
    @Test fun breakOrShortShiftIsNotNoBreak() {
        assertTrue(flags(day(5, 7, 15, breakMin = 30), breakRule = true).single().isEmpty())
        assertTrue(flags(day(6, 8, 14), breakRule = true).single().isEmpty())
    }

    // AC-F12
    @Test fun overlappingShiftsOfOnePersonAreFlaggedButNotTwoPeople() {
        val overlap = flags(listOf(
            p(PunchKind.IN, at(5, 8)), p(PunchKind.OUT, at(5, 12)),
            // second shift built from edited punches that start before the first ends
            p(PunchKind.IN, at(5, 10), member = "b"), p(PunchKind.OUT, at(5, 14), member = "b"),
        ))
        assertTrue(overlap.all { ShiftFlag.Overlap !in it })
        val shifts = listOf(
            Shift("a", at(5, 8), at(5, 12), emptyList(), emptyList(), false, false, false),
            Shift("a", at(5, 11), at(5, 14), emptyList(), emptyList(), false, false, false),
        )
        val f = ShiftFlags.flags(shifts, false, at(6, 0))
        assertTrue(f.all { ShiftFlag.Overlap in it })
    }
}

class TimesheetMathTest {
    private fun week(punches: List<ShiftPunch>, rules: TimesheetRules) =
        TimesheetMath.week(ShiftBuilder.build(punches, at(30, 0)), rules, UTC, at(30, 0))

    // AC-F13
    @Test fun dailyTotalsRemoveUnpaidBreak() {
        val d = TimesheetMath.dailyTotals(ShiftBuilder.build(day(5, 8, 16, breakMin = 30), at(6, 0)), UTC, at(6, 0)).single()
        assertEquals(7 * H + 30 * M, d.workedMillis)
    }

    // AC-F13
    @Test fun noOvertimeRuleMeansAllRegular() {
        val w = week((5..9).flatMap { day(it, 7, 17) } + day(10, 8, 12), TimesheetRules())
        assertEquals(54 * H, w.regularMillis)
        assertEquals(0, w.overtimeMillis)
    }

    // AC-F13
    @Test fun weeklyOvertimeOver40() {
        val w = week((5..9).flatMap { day(it, 7, 17) } + day(10, 8, 12), TimesheetRules.from(OvertimeRule.Weekly40))
        assertEquals(40 * H, w.regularMillis)
        assertEquals(14 * H, w.overtimeMillis)
    }

    // AC-F13
    @Test fun dailyOvertimeOver8() {
        val w = week(day(5, 6, 17), TimesheetRules.from(OvertimeRule.Daily8))
        assertEquals(8 * H, w.regularMillis)
        assertEquals(3 * H, w.overtimeMillis)
    }

    // AC-F13
    @Test fun californiaDoubleTimeOver12() {
        val w = week(day(5, 5, 19), TimesheetRules.from(OvertimeRule.Daily8, californiaDoubleTime = true))
        assertEquals(8 * H, w.regularMillis)
        assertEquals(4 * H, w.overtimeMillis)
        assertEquals(2 * H, w.doubleMillis)
    }

    // AC-F13
    @Test fun dailyAndWeeklyDoNotCountOvertimeTwice() {
        val w = week((5..9).flatMap { day(it, 7, 17) }, TimesheetRules.from(OvertimeRule.Daily8Weekly40))
        assertEquals(40 * H, w.regularMillis)
        assertEquals(10 * H, w.overtimeMillis)
    }

    // AC-F13
    @Test fun roundingIsOffByDefaultAndQuarterHourWhenOn() {
        val punches = listOf(p(PunchKind.IN, at(5, 8, 7)), p(PunchKind.OUT, at(5, 12, 0)))
        assertEquals(3 * H + 53 * M, week(punches, TimesheetRules()).workedMillis)
        assertEquals(4 * H, week(punches, TimesheetRules(roundTo15 = true)).workedMillis)
    }

    // AC-F13
    @Test fun shiftAcrossMidnightSplitsByDay() {
        val punches = listOf(p(PunchKind.IN, at(5, 22)), p(PunchKind.OUT, at(6, 3)))
        val days = week(punches, TimesheetRules()).days
        assertEquals(listOf(2 * H, 3 * H), days.map { it.workedMillis })
    }
}
