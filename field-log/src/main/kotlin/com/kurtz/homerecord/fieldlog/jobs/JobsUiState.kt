package com.kurtz.homerecord.fieldlog.jobs

data class JobsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val showArchived: Boolean = false,
    val groups: JobRowGroups = JobRowGroups(emptyList(), emptyList(), emptyList(), emptyList()),
    /** True when there are jobs but the search text hides all of them. */
    val noMatches: Boolean = false,
    /** Today, ISO, so rows can say "Today" and "Tomorrow". */
    val today: String = "",
)

sealed interface JobsEvent {
    data class Query(val text: String) : JobsEvent
    data object ToggleArchived : JobsEvent
    data class Open(val jobId: String) : JobsEvent
}
