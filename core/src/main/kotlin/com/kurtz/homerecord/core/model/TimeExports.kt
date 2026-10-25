package com.kurtz.homerecord.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the shift CSV needs to know about one shift; GPS text is "lat,lng" or empty (AC-F15). */
data class ShiftExportRow(
    val shift: Shift,
    val person: String,
    val flags: Set<ShiftFlag>,
    val gpsIn: String = "",
    val gpsOut: String = "",
)

/** CSV exports of the time clock: one row per shift, and a QuickBooks-ready timesheet (AC-F15). Pure. */
object TimeExports {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private const val HOUR = 3_600_000.0

    fun shiftCsv(
        rows: List<ShiftExportRow>,
        jobNames: Map<String, String>,
        rules: TimesheetRules,
        zone: ZoneId,
        now: Long,
    ): String {
        val overtime = overtimeByShift(rows.map { it.shift }, rules, zone, now)
        val sb = StringBuilder("Person,Job,In,Out,Break minutes,Hours,OT hours,Flags,GPS in,GPS out\r\n")
        rows.sortedWith(compareBy({ it.person.lowercase() }, { it.shift.startAt })).forEach { r ->
            val s = r.shift
            val job = s.jobId?.let(jobNames::get).orEmpty()
            val out = s.endAt?.let { time(it, zone) }.orEmpty()
            val ot = overtime[s] ?: 0L
            sb.append(
                csvLine(
                    r.person, job, time(s.startAt, zone), out, (s.breakMillis() / 60_000).toString(),
                    hours(s.workedMillis(now)), hours(ot), r.flags.map { it.name }.sorted().joinToString(" "), r.gpsIn, r.gpsOut,
                ),
            )
        }
        return sb.toString()
    }

    /** One row per person, job and day: Customer:Job, Service Item, Hours, Date, Employee. */
    fun quickBooksCsv(
        shifts: List<Shift>,
        memberNames: Map<String, String>,
        customerJobs: Map<String, String>,
        zone: ZoneId,
        now: Long,
        serviceItem: String = "Labor",
    ): String {
        val totals = sortedMapOf<Triple<String, String, LocalDate>, Long>(
            compareBy({ it.first.lowercase() }, { it.third }, { it.second.lowercase() }),
        )
        for (s in shifts) {
            val end = s.endAt ?: now
            val who = memberNames[s.memberId] ?: s.memberId
            for (seg in s.segments) {
                val to = minOf(seg.endAt, end)
                var cursor = seg.startAt
                while (cursor < to) {
                    val date = HoursReport.dateOf(cursor, zone)
                    val chunkEnd = minOf(to, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                    val breaks = s.breaks.sumOf { b -> (minOf(b.last, chunkEnd) - maxOf(b.first, cursor)).coerceAtLeast(0) }
                    val key = Triple(who, seg.jobId?.let(customerJobs::get).orEmpty(), date)
                    totals[key] = (totals[key] ?: 0L) + (chunkEnd - cursor - breaks).coerceAtLeast(0)
                    cursor = chunkEnd
                }
            }
        }
        val sb = StringBuilder("Customer:Job,Service Item,Hours,Date,Employee\r\n")
        totals.filterValues { it > 0 }.forEach { (k, ms) ->
            sb.append(csvLine(k.second, serviceItem, hours(ms), k.third.toString(), k.first))
        }
        return sb.toString()
    }

    /** Overtime and double time per shift: each person-week's premium hours land on the latest shifts first. */
    private fun overtimeByShift(shifts: List<Shift>, rules: TimesheetRules, zone: ZoneId, now: Long): Map<Shift, Long> {
        val out = mutableMapOf<Shift, Long>()
        for ((_, mine) in shifts.groupBy { it.memberId }) {
            val byWeek = mine.groupBy { weekOf(it.startAt, zone) }
            for ((_, week) in byWeek) {
                var premium = TimesheetMath.week(week, rules, zone, now).let { it.overtimeMillis + it.doubleMillis }
                for (s in week.sortedByDescending { it.startAt }) {
                    val share = minOf(premium, s.workedMillis(now).coerceAtLeast(0))
                    if (share > 0) out[s] = share
                    premium -= share
                }
            }
        }
        return out
    }

    private fun weekOf(at: Long, zone: ZoneId): LocalDate =
        HoursReport.dateOf(at, zone).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    private fun time(at: Long, zone: ZoneId): String = stamp.format(Instant.ofEpochMilli(at).atZone(zone))

    private fun hours(ms: Long): String = String.format(Locale.US, "%.2f", ms / HOUR)

    private fun csvLine(vararg cells: String): String = cells.joinToString(",") { csv(it) } + "\r\n"

    /** Quotes a cell that holds a comma, quote or line break, and defuses a leading spreadsheet formula character. */
    internal fun csv(cell: String): String {
        val safe = if (cell.isNotEmpty() && cell[0] in "=+@" || cell.startsWith("-") && cell.length > 1 && !cell[1].isDigit()) "'$cell" else cell
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
