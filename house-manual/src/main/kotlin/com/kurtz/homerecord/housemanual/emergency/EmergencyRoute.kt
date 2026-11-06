package com.kurtz.homerecord.housemanual.emergency

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms

@Composable
fun EmergencyRoute(items: ItemRepository, homeId: String, onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val pseudo = mapOf(
        PseudoRooms.EXTERIOR to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_exterior),
        PseudoRooms.WHOLE_HOUSE to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_whole_house),
    )
    val vm = viewModel<EmergencyViewModel>(factory = viewModelFactory { initializer { EmergencyViewModel(items.observeShutoffs(homeId, pseudo)) } })
    EmergencyScreen(vm.state.collectAsState().value, onEvent = {
        when (it) {
            EmergencyEvent.Back -> onBack()
            is EmergencyEvent.Open -> onOpen(it.itemId)
            EmergencyEvent.Add -> onAdd()
        }
    })
}
