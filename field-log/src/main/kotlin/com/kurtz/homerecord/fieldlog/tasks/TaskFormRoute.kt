package com.kurtz.homerecord.fieldlog.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** The task form; closes itself once saved or deleted. */
@Composable
fun TaskFormRoute(deps: TaskFormDeps, onClose: () -> Unit) {
    val vm = viewModel<TaskFormViewModel>(
        key = "task-form-${deps.jobId}-${deps.taskId ?: "new"}",
        factory = viewModelFactory { initializer { TaskFormViewModel(deps, createSavedStateHandle()) } },
    )
    val state by vm.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    TaskFormScreen(state, { e -> if (e == TaskFormEvent.Close) onClose() else vm.onEvent(e) })
}
