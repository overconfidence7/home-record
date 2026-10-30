package com.kurtz.homerecord.fieldlog.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.AssignmentDao
import com.kurtz.homerecord.core.data.ClientDao
import com.kurtz.homerecord.core.data.JobCrewDao
import com.kurtz.homerecord.core.data.JobDao
import com.kurtz.homerecord.core.data.MemberDao
import com.kurtz.homerecord.core.data.PhotoDao
import com.kurtz.homerecord.core.data.PunchDao
import com.kurtz.homerecord.core.data.TaskDao
import com.kurtz.homerecord.core.model.JobHours
import com.kurtz.homerecord.core.model.PunchPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** FL-Job Overview: address, dates, crew and the three totals (AC-F7). The week runs Monday to Sunday. */
@OptIn(ExperimentalCoroutinesApi::class)
class JobOverviewViewModel(
    private val companyId: String,
    private val jobId: String,
    jobs: JobDao,
    clients: ClientDao,
    crew: JobCrewDao,
    members: MemberDao,
    assignments: AssignmentDao,
    tasks: TaskDao,
    punches: PunchDao,
    photos: PhotoDao,
    gates: Flow<com.kurtz.homerecord.core.company.RoleGates>,
    private val provisionHouse: suspend (jobId: String) -> String? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val job: Flow<com.kurtz.homerecord.core.data.JobEntity?> = jobs.observeFor(companyId).map { list -> list.firstOrNull { it.id == jobId } }

    val state: StateFlow<JobOverviewUiState> = combine(job.flatMapLatest { j ->
        if (j == null) {
            flowOf(JobOverviewUiState(loading = false, notFound = true))
        } else {
            val client = clients.observeFor(companyId).map { l -> l.firstOrNull { it.id == j.clientId } }
            combine(
                combine(client, crew.observeFor(jobId), members.observeFor(companyId)) { c, cr, m -> Triple(c, cr, m) },
                combine(assignments.observeFor(jobId), tasks.observeFor(jobId)) { a, t -> a to t },
                combine(punches.observeFor(companyId), photos.observeCountForJob(jobId)) { p, n -> p to n },
            ) { (c, cr, m), (a, t), (p, n) ->
                val now = clock()
                val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
                val weekEnd = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
                val points = p.map { PunchPoint(it.memberId, it.jobId, it.kind, it.at) }
                val worked = JobHours.workedMillis(points, jobId, weekStart, weekEnd, now)
                JobOverviewMapper.build(j, c, cr, m, a, t, worked, n, today.toString())
            }
        }
    }, gates) { s, g -> s.copy(canEdit = g.canEditJobs) }.stateIn(viewModelScope, SharingStarted.Eagerly, JobOverviewUiState(canEdit = false))

    private var startingHouse = false

    /** House tab "Start the house": creates the job's home with a Main floor once; a second tap while it runs does nothing (AC-F48). */
    fun startHouse() {
        if (startingHouse || state.value.homeId != null) return
        startingHouse = true
        viewModelScope.launch {
            try {
                provisionHouse(jobId)
            } finally {
                startingHouse = false
            }
        }
    }
}
