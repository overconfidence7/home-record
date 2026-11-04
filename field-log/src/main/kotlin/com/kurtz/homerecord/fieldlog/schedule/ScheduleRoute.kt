package com.kurtz.homerecord.fieldlog.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun ScheduleRoute(deps: () -> ScheduleDeps, modifier: Modifier = Modifier) {
    val vm = viewModel<ScheduleViewModel>(key = "schedule", factory = viewModelFactory { initializer { ScheduleViewModel(deps(), createSavedStateHandle()) } })
    val state by vm.state.collectAsState()
    ScheduleScreen(state, vm::onEvent, modifier)
}
