package com.kurtz.homerecord.fieldlog.clock

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.location.LocationPermissions
import com.kurtz.homerecord.coreui.platform.Intents

@Composable
fun ClockRoute(deps: (Context) -> ClockDeps, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm = viewModel<ClockViewModel>(
        factory = viewModelFactory { initializer { ClockViewModel(deps(context.applicationContext), createSavedStateHandle()) } },
    )
    val state = vm.state.collectAsState().value
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        vm.onEvent(ClockEvent.PermissionResult(LocationPermissions.allowed(results)))
    }
    LaunchedEffect(state.requestPermission) {
        if (state.requestPermission) launcher.launch(LocationPermissions.ALL)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onEvent(ClockEvent.Resumed) }
    ClockScreen(
        state,
        { event ->
            if (event == ClockEvent.OpenSettings) {
                vm.onEvent(ClockEvent.OpenSettings)
                Intents.openAppSettings(context)
            } else vm.onEvent(event)
        },
        modifier,
    )
}
