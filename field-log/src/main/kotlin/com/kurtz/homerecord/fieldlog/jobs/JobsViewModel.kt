package com.kurtz.homerecord.fieldlog.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.JobOrdering
import com.kurtz.homerecord.core.repo.JobRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class JobsViewModel(jobs: JobRepository) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    val state: StateFlow<JobsUiState> =
        combine(jobs.observeSummaries(), showArchived) { all, show ->
            JobsUiState(
                loaded = true,
                jobs = JobOrdering.visible(all, show),
                showArchived = show,
                archivedCount = all.count { it.job.archived },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobsUiState())

    fun onEvent(event: JobsEvent) {
        if (event is JobsEvent.ShowArchived) showArchived.value = event.show
    }
}
