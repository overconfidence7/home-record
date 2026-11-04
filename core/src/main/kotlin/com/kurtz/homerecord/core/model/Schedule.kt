package com.kurtz.homerecord.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One scheduled day: [memberId] works on [jobId] on [date] (SPEC data model `Assignment`). */
data class ScheduleEntry(val id: String, val jobId: String, val memberId: String, val date: LocalDate)

/** A job chip in a schedule cell. */
data class ScheduledJob(val assignmentId: String, val jobId: String, val jobName: String)

/** One person on one day. Two or more different jobs on the same day is allowed and flagged (AC-F43). */
data class ScheduleCell(val date: LocalDate, val jobs: List<ScheduledJob>) {
    val conflict: Boolean get() = jobs.map { it.jobId }.distinct().size > 1
}

/** One crew row of the week view: a person and their seven days, Monday first (AC-F41). */
data class ScheduleRow(val memberId: String, val name: String, val cells: List<ScheduleCell>) {
    val conflicts: Int get() = cells.count { it.conflict }
}

/** The next day [ScheduleWeek.nextUp] found for a person: the day and the jobs on it. */
data class NextScheduledDay(val date: LocalDate, val jobNames: List<String>)

/** Builds the FL-Schedule week (AC-F41, AC-F43) and the "Tomorrow" line on Today. Weeks start Monday, like timesheets. */
object ScheduleWeek {
    fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun days(weekStart: LocalDate): List<LocalDate> = (0L until 7L).map { weekStart.plusDays(it) }

    /**
     * Rows for the week starting [weekStart]: one per member in [members] order (id to name), each with seven cells.
     * Entries for jobs missing from [jobNames] (deleted or archived) are left out. Within a cell jobs keep the order
     * they were scheduled in ([entries] order); the same job twice on one day shows once.
     */
    fun build(
        weekStart: LocalDate,
        members: List<Pair<String, String>>,
        entries: List<ScheduleEntry>,
        jobNames: Map<String, String>,
    ): List<ScheduleRow> {
        val days = days(weekStart)
        val inWeek = entries.filter { it.date >= days.first() && it.date <= days.last() && it.jobId in jobNames }
        val byCell = inWeek.groupBy { it.memberId to it.date }
        return members.map { (id, name) ->
            ScheduleRow(
                id, name,
                days.map { day ->
                    val jobs = byCell[id to day].orEmpty().distinctBy { it.jobId }
                        .map { ScheduledJob(it.id, it.jobId, jobNames.getValue(it.jobId)) }
                    ScheduleCell(day, jobs)
                },
            )
        }
    }

    /**
     * What Today says about the days ahead for [memberId] (AC-F41): tomorrow when they are scheduled tomorrow, otherwise
     * the next scheduled day within [lookAheadDays]; null when nothing is coming up.
     */
    fun nextUp(memberId: String, entries: List<ScheduleEntry>, jobNames: Map<String, String>, today: LocalDate, lookAheadDays: Long = 7): NextScheduledDay? {
        val ahead = entries.filter { it.memberId == memberId && it.jobId in jobNames && it.date > today && it.date <= today.plusDays(lookAheadDays) }
        val first = ahead.minOfOrNull { it.date } ?: return null
        return NextScheduledDay(first, ahead.filter { it.date == first }.distinctBy { it.jobId }.map { jobNames.getValue(it.jobId) })
    }
}
