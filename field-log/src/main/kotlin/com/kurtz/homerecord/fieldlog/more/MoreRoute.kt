package com.kurtz.homerecord.fieldlog.more

import androidx.compose.runtime.Composable
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
    onOpenPlan: () -> Unit,
    onOpenClients: () -> Unit,
    onOpenMaterials: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit,
) {
    val vm = viewModel<MoreViewModel>(factory = viewModelFactory { initializer { MoreViewModel(deps()) } })
    val state by vm.state.collectAsState()
    MoreScreen(state, onEvent = { event ->
        when (event) {
            MoreEvent.OpenTeam -> onOpenTeam()
            MoreEvent.OpenTimesheets -> onOpenTimesheets()
            MoreEvent.OpenReports -> onOpenReports()
            MoreEvent.OpenCompany -> onOpenCompany()
            MoreEvent.OpenPlan -> onOpenPlan()
            MoreEvent.OpenClients -> onOpenClients()
            MoreEvent.OpenMaterials -> onOpenMaterials()
            MoreEvent.OpenSync -> onOpenSync()
            MoreEvent.OpenSettings -> onOpenSettings()
            MoreEvent.OpenAbout -> onOpenAbout()
            MoreEvent.SignOut -> onSignOut()
        }
    })
}
