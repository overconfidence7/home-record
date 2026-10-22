package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.data.AssignmentDao
import com.kurtz.homerecord.core.data.ClientDao
import com.kurtz.homerecord.core.data.JobDao

@Composable
fun JobsRoute(companyId: String, jobs: JobDao, clients: ClientDao, assignments: AssignmentDao, onEditJob: (String?) -> Unit, modifier: Modifier = Modifier) {
    val vm = viewModel<JobsViewModel>(
        factory = viewModelFactory { initializer { JobsViewModel(companyId, jobs, clients, assignments, createSavedStateHandle()) } },
    )
    JobsScreen(
        vm.state.collectAsState().value,
        { event ->
            when (event) {
                JobsEvent.New -> onEditJob(null)
                is JobsEvent.Open -> onEditJob(event.jobId) // Overview tab shell replaces this (T018e)
                else -> vm.onEvent(event)
            }
        },
        modifier,
    )
}
