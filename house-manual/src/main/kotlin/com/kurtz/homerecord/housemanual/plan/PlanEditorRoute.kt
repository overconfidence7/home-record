package com.kurtz.homerecord.housemanual.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.HomeRepository

@Composable
fun PlanEditorRoute(repo: HomeRepository, homeId: String, homeName: String, onBack: () -> Unit, onOpenFloors: () -> Unit) {
    val vm = viewModel<PlanEditorViewModel>(
        factory = viewModelFactory { initializer { PlanEditorViewModel(repo, homeId, createSavedStateHandle()) } },
    )
    PlanEditorScreen(vm.state.collectAsState().value, vm::onEvent, onBack, homeName, onOpenFloors)
}
