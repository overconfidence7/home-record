package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.data.ClientDao
import com.kurtz.homerecord.core.data.JobDao

@Composable
fun JobEditRoute(
    companyId: String,
    jobId: String?,
    jobs: JobDao,
    clients: ClientDao,
    recordChange: suspend (String, String) -> Unit,
    onClose: () -> Unit,
) {
    val vm = viewModel<JobEditViewModel>(
        factory = viewModelFactory { initializer { JobEditViewModel(companyId, jobId, jobs, clients, recordChange, createSavedStateHandle()) } },
    )
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    JobEditScreen(state, vm::onEvent, onClose)
}
