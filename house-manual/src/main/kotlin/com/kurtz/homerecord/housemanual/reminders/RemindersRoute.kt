package com.kurtz.homerecord.housemanual.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.reminders.ReminderRepository

@Composable
fun RemindersRoute(reminders: ReminderRepository, homeId: String, onOpenItem: (String) -> Unit, modifier: Modifier = Modifier) {
    val vm = viewModel<RemindersViewModel>(factory = viewModelFactory { initializer { RemindersViewModel(reminders, homeId, createSavedStateHandle()) } })
    RemindersScreen(vm.state.collectAsState().value, onEvent = {
        if (it is RemindersEvent.OpenItem) onOpenItem(it.itemId) else vm.onEvent(it)
    }, modifier = modifier)
}
