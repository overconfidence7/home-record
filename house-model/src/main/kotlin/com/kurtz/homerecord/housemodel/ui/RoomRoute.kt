package com.kurtz.homerecord.housemodel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.items.ItemRepository

/** Room page without the item list, for apps that keep no items (Field Log). */
@Composable
fun RoomRoute(repo: HomeRepository, roomId: String, onBack: () -> Unit) {
    val vm = viewModel<RoomViewModel>(key = "room:$roomId", factory = viewModelFactory { initializer { RoomViewModel(repo, roomId) } })
    RoomScreen(vm.state.collectAsState().value) { event -> if (event == RoomEvent.Back) onBack() }
}

/** Room page with the room's items and "Add an item here" (House Manual, AC-H10). */
@Composable
fun RoomRoute(
    repo: HomeRepository,
    items: ItemRepository,
    homeId: String,
    roomId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onAddItem: (roomId: String) -> Unit,
) {
    val vm = viewModel<RoomViewModel>(
        key = "room:$roomId",
        factory = viewModelFactory { initializer { RoomViewModel(repo, roomId, items.observeRows(homeId, emptyMap())) } },
    )
    RoomScreen(vm.state.collectAsState().value) { event ->
        when (event) {
            RoomEvent.Back -> onBack()
            is RoomEvent.OpenItem -> onOpenItem(event.id)
            RoomEvent.AddItem -> onAddItem(roomId)
        }
    }
}
