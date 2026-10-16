package com.kurtz.homerecord.fieldlog.jobs

import com.kurtz.homerecord.core.logic.JobSummary

data class JobsUiState(
    val loaded: Boolean = false,
    /** Already filtered by [showArchived] and sorted by last activity (AC-53). */
    val jobs: List<JobSummary> = emptyList(),
    val showArchived: Boolean = false,
    val archivedCount: Int = 0,
)

sealed interface JobsEvent {
    data class ShowArchived(val show: Boolean) : JobsEvent
    data object NewJob : JobsEvent
    data class Open(val jobId: String) : JobsEvent
}
