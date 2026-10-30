package com.kurtz.homerecord.fieldlog.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun MoreRoute(
    deps: () -> MoreDeps,
    onOpenTeam: () -> Unit,
    onOpenTimesheets: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenCompany: () -> Unit,
    onSignOut: () -> Unit,
) {
    val vm = viewModel<MoreViewModel>(factory = viewModelFactory { initializer { MoreViewModel(deps()) } })
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) { vm.refresh() }
    MoreScreen(state, onEvent = { event ->
        when (event) {
            MoreEvent.OpenTeam -> onOpenTeam()
            MoreEvent.OpenTimesheets -> onOpenTimesheets()
            MoreEvent.OpenReports -> onOpenReports()
            MoreEvent.OpenCompany -> onOpenCompany()
            MoreEvent.FreeUp -> vm.freeUp()
            MoreEvent.SignOut -> onSignOut()
        }
    })
}
