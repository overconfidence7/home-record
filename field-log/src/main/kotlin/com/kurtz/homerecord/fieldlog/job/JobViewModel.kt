package com.kurtz.homerecord.fieldlog.job

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.model.Job
import com.kurtz.homerecord.core.repo.JobRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class JobUiState(val loaded: Boolean = false, val job: Job? = null)

class JobViewModel(jobs: JobRepository, jobId: String) : ViewModel() {
    val state: StateFlow<JobUiState> = jobs.observeJob(jobId)
        .map { JobUiState(loaded = true, job = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobUiState())
}
