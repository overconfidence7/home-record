package com.kurtz.homerecord.housemanual.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.model.SearchKind
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.search.SearchRepository

@Composable
fun HomeRoute(
    repo: ItemRepository,
    search: SearchRepository,
    homeId: String,
    homeName: String,
    onAddItem: () -> Unit,
    onScanDataPlate: () -> Unit,
    onAddShutoff: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenContacts: () -> Unit,
) {
    val pseudo = mapOf(
        PseudoRooms.EXTERIOR to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_exterior),
        PseudoRooms.WHOLE_HOUSE to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_whole_house),
    )
    val vm = viewModel<HomeViewModel>(
        factory = viewModelFactory { initializer { HomeViewModel(repo.observeHomeSummary(homeId, pseudo), homeName, search.observeIndex(homeId, pseudo), createSavedStateHandle()) } },
    )
    HomeScreen(vm.state.collectAsState().value, onEvent = {
        when (it) {
            HomeEvent.AddItem -> onAddItem()
            HomeEvent.ScanDataPlate -> onScanDataPlate()
            HomeEvent.AddShutoff -> onAddShutoff()
            is HomeEvent.OpenItem -> onOpenItem(it.itemId)
            is HomeEvent.QueryChanged -> vm.onEvent(it)
            is HomeEvent.OpenResult -> when (it.kind) {
                SearchKind.ITEM -> onOpenItem(it.id)
                SearchKind.ROOM -> onOpenRoom(it.id)
                SearchKind.DOCUMENT -> onOpenDocument(it.id)
                SearchKind.CONTACT -> onOpenContacts()
                SearchKind.OVERLAY, SearchKind.SHUTOFF, SearchKind.BREAKER -> Unit
            }
        }
    })
}
