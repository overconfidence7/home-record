package com.kurtz.homerecord.housemanual.contacts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.upkeep.UpkeepRepository

@Composable
fun ContactsRoute(repo: UpkeepRepository, homeId: String, onBack: () -> Unit) {
    val vm = viewModel<ContactsViewModel>(
        factory = viewModelFactory { initializer { ContactsViewModel(repo, homeId, createSavedStateHandle()) } },
    )
    val context = LocalContext.current
    ContactsScreen(vm.state.collectAsState().value, onEvent = {
        when (it) {
            ContactsEvent.Back -> onBack()
            is ContactsEvent.Call -> context.launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + it.phone.filter { c -> c.isDigit() || c == '+' })))
            is ContactsEvent.Email -> context.launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + it.address)))
            else -> vm.onEvent(it)
        }
    })
}

private fun Context.launch(intent: Intent) {
    try { startActivity(intent) } catch (_: ActivityNotFoundException) { /* no dialer or mail app on this device */ }
}
