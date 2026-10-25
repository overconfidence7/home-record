package com.kurtz.homerecord.fieldlog.clock

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.JobSegment
import com.kurtz.homerecord.core.model.Shift
import com.kurtz.homerecord.core.model.ShiftFlag
import com.kurtz.homerecord.core.model.TimesheetRules
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// AC-F12 (flags on the timesheet), AC-F13 (daily and weekly totals): "My timesheet" week view.
class WeekSheetBuilderTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private fun at(date: String, hour: Int) = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun shift(start: Long, end: Long?, far: Boolean = false, auto: Boolean = false) =
        Shift("m1", start, end, emptyList(), listOf(JobSegment("j1", start, end ?: start)), auto, far, false)

    // Wed 2026-10-21 is in the week of Mon 2026-10-19.
    private val now = at("2026-10-21", 18)

    @Test fun weekStartsMondayWithSevenDays() {
        val sheet = WeekSheetBuilder.build(emptyList(), TimesheetRules(), false, zone, now)
        assertThat(sheet.days.map { it.date }).containsExactlyElementsIn((19..25).map { LocalDate.of(2026, 10, it) }).inOrder()
        assertThat(sheet.totalMillis).isEqualTo(0)
    }

    @Test fun totalsAndFlagsLandOnTheDayTheShiftStarted() {
        val shifts = listOf(
            shift(at("2026-10-19", 8), at("2026-10-19", 16)),
            shift(at("2026-10-20", 8), at("2026-10-20", 17), far = true),
            shift(at("2026-10-12", 8), at("2026-10-12", 16)), // last week, ignored
        )
        val sheet = WeekSheetBuilder.build(shifts, TimesheetRules(), false, zone, now)
        assertThat(sheet.days[0].workedMillis).isEqualTo(8 * 3_600_000L)
        assertThat(sheet.days[1].workedMillis).isEqualTo(9 * 3_600_000L)
        assertThat(sheet.days[1].flags).containsExactly(ShiftFlag.FarFromJob)
        assertThat(sheet.days[0].flags).isEmpty()
        assertThat(sheet.totalMillis).isEqualTo(17 * 3_600_000L)
    }

    @Test fun dailyOvertimeSplitsRegularAndOvertime() {
        val shifts = listOf(shift(at("2026-10-20", 7), at("2026-10-20", 17)))
        val sheet = WeekSheetBuilder.build(shifts, TimesheetRules(dailyOtOver8 = true), false, zone, now)
        assertThat(sheet.regularMillis).isEqualTo(8 * 3_600_000L)
        assertThat(sheet.overtimeMillis).isEqualTo(2 * 3_600_000L)
    }

    @Test fun hmLabels() {
        assertThat(WeekSheetBuilder.hm(0)).isEqualTo("0h")
        assertThat(WeekSheetBuilder.hm(8 * 3_600_000L + 15 * 60_000L)).isEqualTo("8h 15m")
        assertThat(WeekSheetBuilder.hm(3 * 3_600_000L)).isEqualTo("3h")
    }
}
