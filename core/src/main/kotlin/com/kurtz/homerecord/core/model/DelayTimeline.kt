package com.kurtz.homerecord.core.model

import java.time.LocalDate

/** The delays typed on one job's daily log for one day (AC-F32). */
data class DelayDay(val jobId: String, val day: LocalDate, val delays: List<LogDelay>)

/** One delay on the timeline. [index] is its place in that day's list, so two delays on a day keep apart. */
data class DelayEntry(val day: LocalDate, val index: Int, val reason: DelayReason, val note: String)

/** A job's delays in the report range, oldest first; [days] counts the days that had at least one. */
data class JobDelays(val jobId: String, val jobName: String, val days: Int, val entries: List<DelayEntry>)

/**
 * The job timeline of delays in FL-Reports (AC-F32): every delay typed on a daily log between [from] and [to]
 * (inclusive), grouped by job. Jobs with the most delayed days come first, then by name; inside a job the
 * entries run oldest first in the order they were typed. Logs of jobs not in [jobNames] (deleted, or another
 * company's) are left out, as are days without a delay.
 */
object DelayTimeline {
    fun build(days: List<DelayDay>, jobNames: Map<String, String>, from: LocalDate, to: LocalDate): List<JobDelays> =
        days.asSequence()
            .filter { it.delays.isNotEmpty() && it.jobId in jobNames && !it.day.isBefore(from) && !it.day.isAfter(to) }
            .groupBy { it.jobId }
            .map { (jobId, jobDays) ->
                val sorted = jobDays.sortedBy { it.day }
                JobDelays(
                    jobId = jobId,
                    jobName = jobNames.getValue(jobId),
                    days = sorted.map { it.day }.distinct().size,
                    entries = sorted.flatMap { d -> d.delays.mapIndexed { i, delay -> DelayEntry(d.day, i, delay.reason, delay.note.trim()) } },
                )
            }
            .sortedWith(compareByDescending<JobDelays> { it.days }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.jobName })
}
