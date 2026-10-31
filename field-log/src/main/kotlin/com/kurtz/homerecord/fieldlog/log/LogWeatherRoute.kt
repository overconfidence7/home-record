package com.kurtz.homerecord.fieldlog.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** The weather form for [LogWeatherDeps.day]; closes itself once saved or looked up again. */
@Composable
fun LogWeatherRoute(deps: LogWeatherDeps, onClose: () -> Unit) {
    val vm = viewModel<LogWeatherViewModel>(
        key = "log-weather-${deps.jobId}-${deps.day}",
        factory = viewModelFactory { initializer { LogWeatherViewModel(deps, createSavedStateHandle()) } },
    )
    val state by vm.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    LogWeatherScreen(state, { e -> if (e == LogWeatherEvent.Close) onClose() else vm.onEvent(e) })
}
