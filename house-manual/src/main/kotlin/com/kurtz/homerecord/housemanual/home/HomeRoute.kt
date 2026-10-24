package com.kurtz.homerecord.housemanual.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms

@Composable
fun HomeRoute(repo: ItemRepository, homeId: String, homeName: String, onAddItem: () -> Unit, onOpenItem: (String) -> Unit) {
    val pseudo = mapOf(
        PseudoRooms.EXTERIOR to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_exterior),
        PseudoRooms.WHOLE_HOUSE to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_whole_house),
    )
    val vm = viewModel<HomeViewModel>(
        factory = viewModelFactory { initializer { HomeViewModel(repo.observeHomeSummary(homeId, pseudo), homeName) } },
    )
    HomeScreen(vm.state.collectAsState().value, onEvent = {
        when (it) {
            HomeEvent.AddItem, HomeEvent.ScanDataPlate -> onAddItem()
            is HomeEvent.OpenItem -> onOpenItem(it.itemId)
            HomeEvent.PhotographWall -> vm.onEvent(it)
        }
    })
}
