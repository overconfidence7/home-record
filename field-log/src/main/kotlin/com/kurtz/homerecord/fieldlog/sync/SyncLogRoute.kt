package com.kurtz.homerecord.fieldlog.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun SyncLogRoute(deps: () -> SyncLogDeps, onBack: () -> Unit) {
    val vm = viewModel<SyncLogViewModel>(factory = viewModelFactory { initializer { SyncLogViewModel(deps()) } })
    val state by vm.state.collectAsState()
    SyncLogScreen(state, onEvent = { event ->
        when (event) {
            SyncLogEvent.SyncNow -> vm.syncNow()
            SyncLogEvent.Clear -> vm.clear()
            SyncLogEvent.Back -> onBack()
        }
    })
}
