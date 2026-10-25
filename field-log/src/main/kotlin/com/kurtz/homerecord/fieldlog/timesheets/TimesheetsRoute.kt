package com.kurtz.homerecord.fieldlog.timesheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun TimesheetsRoute(deps: () -> TimesheetsDeps, onBack: () -> Unit) {
    val vm = viewModel<TimesheetsViewModel>(factory = viewModelFactory { initializer { TimesheetsViewModel(deps(), createSavedStateHandle()) } })
    TimesheetsScreen(vm.state.collectAsState().value, vm::onEvent, onBack)
}
