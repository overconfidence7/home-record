package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Job
import com.kurtz.homerecord.core.model.LogEntry

/** A job plus the timestamp it is sorted by (AC-53). */
data class JobSummary(val job: Job, val lastActivity: Long, val entryCount: Int)

/** FL-Jobs ordering: most recent LogEntry first; jobs with no entries fall back to createdAt. */
object JobOrdering {
    fun summarize(jobs: List<Job>, entries: List<LogEntry>): List<JobSummary> {
        val byJob = entries.groupBy { it.jobId }
        return jobs.map { job ->
            val own = byJob[job.id].orEmpty()
            JobSummary(job, own.maxOfOrNull { it.createdAt } ?: job.createdAt, own.size)
        }.sortedWith(compareByDescending<JobSummary> { it.lastActivity }.thenBy { it.job.name.lowercase() })
    }

    fun visible(summaries: List<JobSummary>, showArchived: Boolean): List<JobSummary> =
        if (showArchived) summaries else summaries.filter { !it.job.archived }
}
