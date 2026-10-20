package com.kurtz.homerecord.housemanual.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.HomeRepository

@Composable
fun PlanEditorRoute(repo: HomeRepository, homeId: String, homeName: String, onBack: () -> Unit, onOpenFloors: () -> Unit, floorId: String? = null, roomId: String? = null, editShape: Boolean = false) {
    val vm = viewModel<PlanEditorViewModel>(
        factory = viewModelFactory { initializer { PlanEditorViewModel(repo, homeId, createSavedStateHandle(), initialFloorId = floorId, initialRoomId = roomId, initialEditShape = editShape) } },
    )
    PlanEditorScreen(vm.state.collectAsState().value, vm::onEvent, onBack, homeName, onOpenFloors)
}
