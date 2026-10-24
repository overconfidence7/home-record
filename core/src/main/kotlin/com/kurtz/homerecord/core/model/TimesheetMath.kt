package com.kurtz.homerecord.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How hours turn into pay buckets (AC-F13). */
data class TimesheetRules(
    val weeklyOtOver40: Boolean = false,
    val dailyOtOver8: Boolean = false,
    /** California preset: daily hours past 12 are double time. */
    val doubleOver12: Boolean = false,
    val roundTo15: Boolean = false,
) {
    companion object {
        fun from(rule: com.kurtz.homerecord.core.company.OvertimeRule, californiaDoubleTime: Boolean = false) = TimesheetRules(
            weeklyOtOver40 = rule == com.kurtz.homerecord.core.company.OvertimeRule.Weekly40 ||
                rule == com.kurtz.homerecord.core.company.OvertimeRule.Daily8Weekly40,
            dailyOtOver8 = rule == com.kurtz.homerecord.core.company.OvertimeRule.Daily8 ||
                rule == com.kurtz.homerecord.core.company.OvertimeRule.Daily8Weekly40,
            doubleOver12 = californiaDoubleTime,
        )
    }
}

data class DayTotal(val date: LocalDate, val workedMillis: Long)

data class WeekTotal(
    val days: List<DayTotal>,
    val regularMillis: Long,
    val overtimeMillis: Long,
    val doubleMillis: Long,
) {
    val workedMillis: Long get() = regularMillis + overtimeMillis + doubleMillis
}

/** Daily and weekly totals with unpaid breaks removed (AC-F13). Pure; no clock reads. */
object TimesheetMath {
    private const val HOUR = 3_600_000L
    private const val QUARTER = 15 * 60_000L

    /** Worked time per local day, a shift that crosses midnight is split at midnight. Breaks come off the day they fall in. */
    fun dailyTotals(shifts: List<Shift>, zone: ZoneId, now: Long): List<DayTotal> {
        val perDay = sortedMapOf<LocalDate, Long>()
        for (s in shifts) {
            val end = s.endAt ?: now
            addSpan(perDay, s.startAt, end, zone, +1)
            for (b in s.breaks) addSpan(perDay, b.first, minOf(b.last, end), zone, -1)
        }
        return perDay.map { (d, ms) -> DayTotal(d, rounded(ms.coerceAtLeast(0), false)) }
    }

    private fun addSpan(map: MutableMap<LocalDate, Long>, from: Long, to: Long, zone: ZoneId, sign: Int) {
        var cursor = from
        while (cursor < to) {
            val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val chunkEnd = minOf(to, nextMidnight)
            map[date] = (map[date] ?: 0L) + sign * (chunkEnd - cursor)
            cursor = chunkEnd
        }
    }

    private fun rounded(ms: Long, round: Boolean): Long =
        if (!round) ms else ((ms + QUARTER / 2) / QUARTER) * QUARTER

    fun week(shifts: List<Shift>, rules: TimesheetRules, zone: ZoneId, now: Long): WeekTotal {
        val days = dailyTotals(shifts, zone, now).map { it.copy(workedMillis = rounded(it.workedMillis, rules.roundTo15)) }
        var regular = 0L
        var ot = 0L
        var dbl = 0L
        for (d in days) {
            val w = d.workedMillis
            if (rules.dailyOtOver8) {
                val doubleStart = if (rules.doubleOver12) 12 * HOUR else Long.MAX_VALUE
                dbl += (w - doubleStart).coerceAtLeast(0)
                ot += (minOf(w, doubleStart) - 8 * HOUR).coerceAtLeast(0)
                regular += minOf(w, 8 * HOUR)
            } else {
                regular += w
            }
        }
        if (rules.weeklyOtOver40 && regular > 40 * HOUR) {
            ot += regular - 40 * HOUR
            regular = 40 * HOUR
        }
        return WeekTotal(days, regular, ot, dbl)
    }
}
