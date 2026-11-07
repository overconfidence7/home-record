package com.kurtz.homerecord.fieldlog.clients

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun ClientsRoute(deps: () -> ClientsDeps, onBack: () -> Unit, onOpenJob: (String) -> Unit) {
    val vm = viewModel<ClientsViewModel>(factory = viewModelFactory { initializer { ClientsViewModel(deps()) } })
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    fun launch(intent: Intent) {
        try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { /* no dialer or mail app on this phone */ }
    }
    ClientsScreen(state, onEvent = { event ->
        when (event) {
            is ClientsEvent.Call -> launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + ClientListBuilder.dialable(event.phone))))
            is ClientsEvent.Email -> launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + event.email)))
            is ClientsEvent.OpenJob -> onOpenJob(event.jobId)
            ClientsEvent.Retry -> vm.retry()
        }
    }, onBack = onBack)
}
