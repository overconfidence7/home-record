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
fun RemindersRoute(reminders: ReminderRepository, homeId: String, onOpenItem: (String) -> Unit, modifier: Modifier = Modifier, viewOnly: Boolean = false) {
    val vm = viewModel<RemindersViewModel>(
        key = "reminders:$homeId:$viewOnly",
        factory = viewModelFactory { initializer { RemindersViewModel(reminders, homeId, createSavedStateHandle(), canEdit = !viewOnly) } },
    )
    RemindersScreen(vm.state.collectAsState().value.copy(viewOnly = viewOnly), onEvent = {
        if (it is RemindersEvent.OpenItem) onOpenItem(it.itemId) else vm.onEvent(it)
    }, modifier = modifier)
}
