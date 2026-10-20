package com.kurtz.homerecord.housemanual.house

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemanual.onboarding.OnboardingScreen
import com.kurtz.homerecord.housemanual.onboarding.OnboardingViewModel
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.blueprint.LayerStore
import com.kurtz.homerecord.housemodel.plan.PlanRoom

@Composable
fun OnboardingRoute(repo: HomeRepository, modifier: Modifier = Modifier) {
    val vm = viewModel<OnboardingViewModel>(
        factory = viewModelFactory { initializer { OnboardingViewModel(repo, createSavedStateHandle()) } },
    )
    OnboardingScreen(vm.state.collectAsState().value, vm::onEvent, modifier)
}

@Composable
fun FloorsRoute(repo: HomeRepository, homeId: String, homeName: String, onBack: () -> Unit) {
    val vm = viewModel<FloorsViewModel>(factory = viewModelFactory { initializer { FloorsViewModel(repo, homeId) } })
    FloorsScreen(vm.state.collectAsState().value, vm::onEvent, onBack, homeName)
}

@Composable
fun HouseRoute(
    repo: HomeRepository,
    layerStore: LayerStore,
    homeId: String,
    homeName: String,
    summary: String,
    onOpenRoom: (PlanRoom) -> Unit,
    onEditPlan: (floorId: String?) -> Unit,
    onEditShape: (floorId: String?, PlanRoom) -> Unit,
    onOpenFloors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = viewModel<HouseViewModel>(
        factory = viewModelFactory { initializer { HouseViewModel(repo, homeId, layerStore, createSavedStateHandle(), homeName, summary) } },
    )
    val state = vm.state.collectAsState().value
    HouseScreen(
        state = state,
        onEvent = vm::onEvent,
        onOpenRoom = onOpenRoom,
        onEditPlan = { onEditPlan(state.floorId) },
        onEditShape = { onEditShape(state.floorId, it) },
        onOpenFloors = onOpenFloors,
        modifier = modifier,
    )
}
