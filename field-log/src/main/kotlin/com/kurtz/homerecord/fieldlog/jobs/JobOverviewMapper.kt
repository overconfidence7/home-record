package com.kurtz.homerecord.fieldlog.jobs

import com.kurtz.homerecord.core.data.AssignmentEntity
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.JobCrewEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.TaskEntity
import com.kurtz.homerecord.core.model.JobOrdering
import com.kurtz.homerecord.core.model.effectiveStatus
import com.kurtz.homerecord.core.model.JobSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** Builds the FL-Job Overview numbers from rows (AC-F7). */
object JobOverviewMapper {
    private const val HOUR_MILLIS = 3_600_000.0

    fun build(
        job: JobEntity,
        client: ClientEntity?,
        crew: List<JobCrewEntity>,
        members: List<MemberEntity>,
        assignments: List<AssignmentEntity>,
        tasks: List<TaskEntity>,
        workedMillis: Long,
        photoCount: Int,
        today: String,
    ): JobOverviewUiState {
        val status = job.effectiveStatus(today)
        val summary = JobSummary(job.id, job.name, status, job.startDate, job.endDate, assignments.map { it.date })
        val names = members.associate { it.id to it.name }
        return JobOverviewUiState(
            loading = false,
            name = job.name,
            clientName = client?.name,
            address = job.address,
            lat = job.lat,
            lng = job.lng,
            status = status,
            startDate = job.startDate,
            endDate = job.endDate,
            phases = runCatching { Json.decodeFromString(ListSerializer(String.serializer()), job.phasesJson) }.getOrDefault(emptyList()),
            hoursThisWeek = Math.round(workedMillis / HOUR_MILLIS * 10) / 10.0,
            photoCount = photoCount,
            openTasks = tasks.count { !it.done },
            nextDay = JobOrdering.nextDay(summary, today),
            crew = crew.mapNotNull { names[it.memberId] }.distinct().sorted(),
            today = today,
            homeId = job.homeId,
        )
    }
}
