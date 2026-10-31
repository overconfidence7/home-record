package com.kurtz.homerecord.fieldlog.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun TasksRoute(deps: TasksDeps, onOpenTask: (String) -> Unit, onAddTask: () -> Unit, modifier: Modifier = Modifier) {
    val vm = viewModel<TasksViewModel>(
        key = "tasks-${deps.jobId}",
        factory = viewModelFactory { initializer { TasksViewModel(deps, createSavedStateHandle()) } },
    )
    val state by vm.state.collectAsState()
    TasksScreen(
        state,
        { e ->
            when (e) {
                is TasksEvent.Open -> if (vm.state.value.canManage) onOpenTask(e.id)
                TasksEvent.Add -> if (vm.state.value.canManage) onAddTask()
                else -> vm.onEvent(e)
            }
        },
        modifier,
    )
}
