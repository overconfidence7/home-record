package com.kurtz.homerecord.housemanual.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import kotlinx.coroutines.flow.map

@Composable
fun PanelRoute(repo: PanelRepository, items: ItemRepository, itemId: String, onBack: () -> Unit, onOpenBreaker: (slot: Int, breakerId: String?) -> Unit = { _, _ -> }) {
    val vm = viewModel<PanelViewModel>(
        key = "panel:$itemId",
        factory = viewModelFactory { initializer { PanelViewModel(repo, itemId, items.observeItem(itemId).map { it?.name }) } },
    )
    PanelScreen(vm.state.collectAsState().value, onEvent = {
        when (it) {
            PanelEvent.Back -> onBack()
            is PanelEvent.OpenSlot -> onOpenBreaker(it.slot, it.breakerId)
        }
    })
}
