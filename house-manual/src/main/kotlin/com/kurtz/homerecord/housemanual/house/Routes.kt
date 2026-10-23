package com.kurtz.homerecord.housemanual.house

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.onboarding.OnboardingEvent
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
fun OnboardingRoute(repo: HomeRepository, modifier: Modifier = Modifier, onDrawPlan: () -> Unit = {}) {
    val vm = viewModel<OnboardingViewModel>(
        factory = viewModelFactory { initializer { OnboardingViewModel(repo, createSavedStateHandle()) } },
    )
    val state = vm.state.collectAsState().value
    OnboardingScreen(
        state,
        { event ->
            // Only a valid form creates the home, so only then should the editor open.
            if (event == OnboardingEvent.DrawPlan && state.name.isNotBlank() && state.type != null) onDrawPlan()
            vm.onEvent(event)
        },
        modifier,
    )
}
