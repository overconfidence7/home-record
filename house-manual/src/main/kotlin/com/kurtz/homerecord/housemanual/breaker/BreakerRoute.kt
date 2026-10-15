package com.kurtz.homerecord.housemanual.breaker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** Wires [BreakerViewModel] to [BreakerScreen]; pops when the breaker is saved or deleted. */
@Composable
fun BreakerRoute(viewModel: BreakerViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    BreakerScreen(state, viewModel::onEvent)
}
