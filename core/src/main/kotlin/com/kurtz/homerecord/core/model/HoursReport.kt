package com.kurtz.homerecord.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Pay buckets for one person or one job in a report. */
data class HoursLine(
    val id: String?,
    val name: String,
    val workedMillis: Long,
    val regularMillis: Long = workedMillis,
    val overtimeMillis: Long = 0,
    val doubleMillis: Long = 0,
)

data class HoursReportResult(
    val byPerson: List<HoursLine>,
    val byJob: List<HoursLine>,
    val totalMillis: Long,
    val regularMillis: Long,
    val overtimeMillis: Long,
    val doubleMillis: Long,
)

/**
 * Hours by person and by job over whole Monday weeks (AC-F46). Pure.
 * Person totals run [TimesheetMath.week] one week at a time, so they equal the FL-Timesheets totals for the same weeks;
 * job totals are the time on each job segment minus the breaks inside it.
 */
object HoursReport {
    fun build(
        shifts: List<Shift>,
        weekStarts: List<LocalDate>,
        memberNames: Map<String, String>,
        jobNames: Map<String, String>,
        rules: TimesheetRules,
        zone: ZoneId,
        now: Long,
    ): HoursReportResult {
        val weeks = weekStarts.sorted()
        val people = mutableMapOf<String, HoursLine>()
        for ((member, mine) in shifts.groupBy { it.memberId }) {
            var line = HoursLine(member, memberNames[member] ?: member, 0)
            for (w in weeks) {
                val (from, to) = weekBounds(w, zone)
                val inWeek = mine.filter { it.startAt < to && (it.endAt ?: now) > from }
                if (inWeek.isEmpty()) continue
                val t = TimesheetMath.week(inWeek, rules, zone, now)
                line = line.copy(
                    workedMillis = line.workedMillis + t.workedMillis,
                    regularMillis = line.regularMillis + t.regularMillis,
                    overtimeMillis = line.overtimeMillis + t.overtimeMillis,
                    doubleMillis = line.doubleMillis + t.doubleMillis,
                )
            }
            if (line.workedMillis > 0) people[member] = line
        }
        val windowFrom = weekBounds(weeks.firstOrNull() ?: return empty(), zone).first
        val windowTo = weekBounds(weeks.last(), zone).second
        val perJob = mutableMapOf<String?, Long>()
        for (s in shifts) {
            val end = s.endAt ?: now
            for (seg in s.segments) {
                val from = maxOf(seg.startAt, windowFrom)
                val to = minOf(seg.endAt.coerceAtMost(end), windowTo)
                if (to <= from) continue
                val breaks = s.breaks.sumOf { b -> (minOf(b.last, to) - maxOf(b.first, from)).coerceAtLeast(0) }
                perJob[seg.jobId] = (perJob[seg.jobId] ?: 0L) + (to - from - breaks).coerceAtLeast(0)
            }
        }
        val jobs = perJob.filterValues { it > 0 }
            .map { (id, ms) -> HoursLine(id, id?.let(jobNames::get) ?: "No job", ms) }
            .sortedWith(compareByDescending<HoursLine> { it.workedMillis }.thenBy { it.name.lowercase() })
        val persons = people.values.sortedBy { it.name.lowercase() }
        return HoursReportResult(
            byPerson = persons,
            byJob = jobs,
            totalMillis = persons.sumOf { it.workedMillis },
            regularMillis = persons.sumOf { it.regularMillis },
            overtimeMillis = persons.sumOf { it.overtimeMillis },
            doubleMillis = persons.sumOf { it.doubleMillis },
        )
    }

    private fun empty() = HoursReportResult(emptyList(), emptyList(), 0, 0, 0, 0)

    private fun weekBounds(monday: LocalDate, zone: ZoneId): Pair<Long, Long> =
        monday.atStartOfDay(zone).toInstant().toEpochMilli() to monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()

    internal fun dateOf(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}
