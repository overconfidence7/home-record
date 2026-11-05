package com.kurtz.homerecord.fieldlog.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun SettingsRoute(deps: () -> SettingsDeps, onBack: () -> Unit) {
    val vm = viewModel<SettingsViewModel>(factory = viewModelFactory { initializer { SettingsViewModel(deps()) } })
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    // Coming back from system settings may have turned notifications on or off.
    LifecycleResumeEffect(vm) {
        vm.refresh()
        onPauseOrDispose { }
    }
    SettingsScreen(state, onEvent = { event ->
        when (event) {
            is SettingsEvent.SetUnits -> vm.setUnits(event.system)
            SettingsEvent.OpenNotificationSettings -> context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            SettingsEvent.FreeUp -> vm.freeUp()
            SettingsEvent.Back -> onBack()
        }
    })
}
