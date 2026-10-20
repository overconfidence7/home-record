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
