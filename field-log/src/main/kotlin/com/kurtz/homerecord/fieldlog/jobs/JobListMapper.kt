package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.runtime.Immutable
import com.kurtz.homerecord.core.data.AssignmentEntity
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.model.JobGroups
import com.kurtz.homerecord.core.model.JobOrdering
import com.kurtz.homerecord.core.model.effectiveStatus
import com.kurtz.homerecord.core.model.JobSummary

/**
 * One row of FL-Jobs. Immutable so a list that re-emits equal rows (a sync pull rewriting the same jobs) skips them
 * instead of recomposing every visible row (AC-U11).
 */
@Immutable
data class JobRow(
    val summary: JobSummary,
    val clientName: String?,
    val address: String?,
    /** Next scheduled day (today or later), ISO, for the row's trailing date. */
    val nextDay: String?,
) {
    /** "Client, address", built once with the row instead of on every composition while the list scrolls. */
    val supporting: String? = listOfNotNull(clientName, address).joinToString(", ").ifBlank { null }
}

@Immutable
data class JobRowGroups(val active: List<JobRow>, val upcoming: List<JobRow>, val done: List<JobRow>, val archived: List<JobRow>) {
    val isEmpty: Boolean get() = active.isEmpty() && upcoming.isEmpty() && done.isEmpty() && archived.isEmpty()
}

/** Joins jobs with their client and schedule, filters by search text, then groups through [JobOrdering] (AC-F6). */
object JobListMapper {
    fun rows(jobs: List<JobEntity>, clients: List<ClientEntity>, assignments: List<AssignmentEntity>, today: String): List<JobRow> {
        val clientById = clients.associateBy { it.id }
        val daysByJob = assignments.groupBy({ it.jobId }, { it.date })
        return jobs.map { job ->
            val summary = JobSummary(
                id = job.id,
                name = job.name,
                status = job.effectiveStatus(today),
                startDate = job.startDate,
                endDate = job.endDate,
                scheduledDays = daysByJob[job.id].orEmpty(),
            )
            JobRow(summary, job.clientId?.let { clientById[it]?.name }, job.address, JobOrdering.nextDay(summary, today))
        }
    }

    fun matches(row: JobRow, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return listOfNotNull(row.summary.name, row.clientName, row.address).any { it.contains(q, ignoreCase = true) }
    }

    fun group(rows: List<JobRow>, query: String, today: String, showArchived: Boolean): JobRowGroups {
        val kept = rows.filter { matches(it, query) }
        val byId = kept.associateBy { it.summary.id }
        val g: JobGroups = JobOrdering.group(kept.map { it.summary }, today, showArchived)
        fun pick(list: List<JobSummary>) = list.mapNotNull { byId[it.id] }
        return JobRowGroups(pick(g.active), pick(g.upcoming), pick(g.done), pick(g.archived))
    }
}
