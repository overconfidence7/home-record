package com.kurtz.homerecord.housemanual.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms

@Composable
fun PanelsRoute(items: ItemRepository, homeId: String, onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val pseudo = mapOf(
        PseudoRooms.EXTERIOR to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_exterior),
        PseudoRooms.WHOLE_HOUSE to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_whole_house),
    )
    val vm = viewModel<PanelsViewModel>(factory = viewModelFactory { initializer { PanelsViewModel(items.observeRows(homeId, pseudo)) } })
    PanelsScreen(vm.state.collectAsState().value, onEvent = {
        when (it) {
            PanelsEvent.Back -> onBack()
            is PanelsEvent.Open -> onOpen(it.itemId)
            PanelsEvent.Add -> onAdd()
        }
    })
}
