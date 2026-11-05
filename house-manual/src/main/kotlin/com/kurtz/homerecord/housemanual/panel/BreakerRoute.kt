package com.kurtz.homerecord.housemanual.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.panel.PanelRepository

/** HM-Breaker for [breakerId] on the panel item [itemId], or a new breaker starting at [slot] (AC-C40). */
@Composable
fun BreakerRoute(repo: PanelRepository, items: ItemRepository, homeId: String, itemId: String, slot: Int, breakerId: String?, onClose: () -> Unit) {
    val pseudo = mapOf(
        PseudoRooms.EXTERIOR to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_exterior),
        PseudoRooms.WHOLE_HOUSE to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_whole_house),
    )
    val vm = viewModel<BreakerViewModel>(
        key = "breaker:$itemId:$slot:$breakerId",
        factory = viewModelFactory {
            initializer { BreakerViewModel(repo, itemId, slot, breakerId, homeId, items.observeRows(homeId, pseudo), createSavedStateHandle()) }
        },
    )
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.done) { if (state.done) onClose() }
    BreakerScreen(state, vm::onEvent, onClose)
}
