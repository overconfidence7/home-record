package com.kurtz.homerecord.fieldlog.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun LogPreviewRoute(deps: LogPreviewDeps, onBack: () -> Unit) {
    val vm = viewModel<LogPreviewViewModel>(
        key = "log-pdf-${deps.jobId}-${deps.day}",
        factory = viewModelFactory { initializer { LogPreviewViewModel(deps) } },
    )
    val state by vm.state.collectAsState()
    LogPreviewScreen(state, { e -> if (e == LogPreviewEvent.Back) onBack() else vm.onEvent(e) })
}
