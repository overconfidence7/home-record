package com.kurtz.homerecord.fieldlog.clock

import com.kurtz.homerecord.core.model.Shift
import com.kurtz.homerecord.core.model.ShiftFlag
import com.kurtz.homerecord.core.model.ShiftFlags
import com.kurtz.homerecord.core.model.TimesheetMath
import com.kurtz.homerecord.core.model.TimesheetRules
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class SheetDay(val date: LocalDate, val workedMillis: Long, val flags: Set<ShiftFlag>)

data class WeekSheet(
    val days: List<SheetDay>,
    val regularMillis: Long,
    val overtimeMillis: Long,
    val doubleMillis: Long,
) {
    val totalMillis: Long get() = regularMillis + overtimeMillis + doubleMillis
}

/** "My timesheet": this week (Monday first) by day with totals and flags (FL-Clock, AC-F12, AC-F13). Pure. */
object WeekSheetBuilder {
    fun build(shifts: List<Shift>, rules: TimesheetRules, breakRule: Boolean, zone: ZoneId, now: Long): WeekSheet {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val from = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val flagged = ShiftFlags.flags(shifts, breakRule, now)
        val inWeek = shifts.withIndex().filter { (_, s) -> s.startAt < to && (s.endAt ?: now) > from }
        val week = TimesheetMath.week(inWeek.map { it.value }, rules, zone, now)
        val worked = week.days.associate { it.date to it.workedMillis }
        val flagsByDay = inWeek.groupBy({ Instant.ofEpochMilli(it.value.startAt).atZone(zone).toLocalDate() }, { flagged[it.index] })
            .mapValues { (_, sets) -> sets.flatten().toSet() }
        val days = (0L until 7L).map { monday.plusDays(it) }.map { d ->
            SheetDay(d, worked[d] ?: 0L, flagsByDay[d].orEmpty())
        }
        return WeekSheet(days, week.regularMillis, week.overtimeMillis, week.doubleMillis)
    }

    /** 7h 45m style label for a duration, "0h" at zero. */
    fun hm(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes % 60 == 0L) "${minutes / 60}h" else "${minutes / 60}h ${minutes % 60}m"
    }
}
