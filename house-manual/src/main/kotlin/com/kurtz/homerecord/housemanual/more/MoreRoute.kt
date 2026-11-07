package com.kurtz.homerecord.housemanual.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.units.UnitsStore
import com.kurtz.homerecord.housemodel.reminders.ReminderRepository
import kotlinx.coroutines.flow.Flow

/** Wires HM-More: the pickers go to [MoreViewModel], every other row to [onNavigate]. */
@Composable
fun MoreRoute(units: UnitsStore, reminders: ReminderRepository, homeId: String, canEdit: Flow<Boolean>, onNavigate: (MoreEvent) -> Unit) {
    val vm: MoreViewModel = viewModel(
        key = "more-$homeId",
        factory = viewModelFactory { initializer { MoreViewModel(units, reminders.observeZone(homeId), canEdit) { reminders.setClimateZone(homeId, it) } } },
    )
    val state by vm.state.collectAsState()
    MoreScreen(state, onEvent = { e ->
        when (e) {
            MoreEvent.OpenUnits, MoreEvent.OpenZone, MoreEvent.DismissDialog, is MoreEvent.SetUnits, is MoreEvent.PickZone -> vm.onEvent(e)
            else -> onNavigate(e)
        }
    })
}
