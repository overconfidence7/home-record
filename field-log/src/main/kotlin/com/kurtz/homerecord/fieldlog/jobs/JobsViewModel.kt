package com.kurtz.homerecord.fieldlog.jobs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.data.AssignmentDao
import com.kurtz.homerecord.core.data.ClientDao
import com.kurtz.homerecord.core.data.JobDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** FL-Jobs: jobs grouped Active / Upcoming / Done, searchable (AC-F6). The query survives process death. */
class JobsViewModel(
    companyId: String,
    jobs: JobDao,
    clients: ClientDao,
    assignments: AssignmentDao,
    gates: Flow<RoleGates>,
    private val saved: SavedStateHandle,
    private val todayProvider: () -> String = { LocalDate.now().toString() },
    /** Joining, filtering and grouping a few hundred jobs runs here, not on the main thread that draws the list (AC-U11). */
    mapping: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val query = saved.getStateFlow(QUERY, "")
    private val showArchived = saved.getStateFlow(ARCHIVED, false)

    val state: StateFlow<JobsUiState> = combine(
        combine(jobs.observeFor(companyId), gates) { j, g -> j to g },
        clients.observeFor(companyId),
        assignments.observeForCompany(companyId),
        query,
        showArchived,
    ) { (j, g), c, a, q, arch ->
        val today = todayProvider()
        val all = JobListMapper.rows(j, c, a, today)
        val groups = JobListMapper.group(all, q, today, arch)
        JobsUiState(
            loading = false,
            query = q,
            showArchived = arch,
            groups = groups,
            noMatches = all.isNotEmpty() && groups.isEmpty && q.isNotBlank(),
            today = today,
            canAddJob = g.canEditJobs,
        )
    }.flowOn(mapping).stateIn(viewModelScope, SharingStarted.Eagerly, JobsUiState(query = saved.get<String>(QUERY).orEmpty(), canAddJob = false))

    fun onEvent(event: JobsEvent) {
        when (event) {
            is JobsEvent.Query -> saved[QUERY] = event.text
            JobsEvent.ToggleArchived -> saved[ARCHIVED] = !(saved.get<Boolean>(ARCHIVED) ?: false)
            is JobsEvent.Open, JobsEvent.New -> Unit // routed by JobsRoute
        }
    }

    private companion object {
        const val QUERY = "query"
        const val ARCHIVED = "archived"
    }
}
