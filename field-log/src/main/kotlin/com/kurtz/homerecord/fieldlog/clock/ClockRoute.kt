package com.kurtz.homerecord.fieldlog.clock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun ClockRoute(deps: (Context) -> ClockDeps, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm = viewModel<ClockViewModel>(
        factory = viewModelFactory { initializer { ClockViewModel(deps(context.applicationContext), createSavedStateHandle()) } },
    )
    val state = vm.state.collectAsState().value
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onEvent(ClockEvent.PermissionResult(granted))
    }
    LaunchedEffect(state.requestPermission) {
        if (state.requestPermission) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    ClockScreen(
        state,
        { event ->
            if (event == ClockEvent.OpenSettings) {
                vm.onEvent(ClockEvent.DismissGps)
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } else vm.onEvent(event)
        },
        modifier,
    )
}
