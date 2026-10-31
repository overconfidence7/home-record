package com.kurtz.homerecord.fieldlog.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun LogRoute(deps: LogDeps, modifier: Modifier = Modifier, onEditWeather: (java.time.LocalDate) -> Unit = {}) {
    val vm = viewModel<LogViewModel>(
        key = "log-${deps.jobId}",
        factory = viewModelFactory { initializer { LogViewModel(deps, createSavedStateHandle()) } },
    )
    val state by vm.state.collectAsState()
    LogScreen(state, { e -> if (e == LogEvent.EditWeather) onEditWeather(state.day) else vm.onEvent(e) }, modifier)
}
