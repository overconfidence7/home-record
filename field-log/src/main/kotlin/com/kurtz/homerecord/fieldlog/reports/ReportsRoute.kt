package com.kurtz.homerecord.fieldlog.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun ReportsRoute(deps: () -> ReportsDeps, onBack: () -> Unit) {
    val vm = viewModel<ReportsViewModel>(factory = viewModelFactory { initializer { ReportsViewModel(deps(), createSavedStateHandle()) } })
    ReportsScreen(vm.state.collectAsState().value, vm::onEvent, onBack)
}
