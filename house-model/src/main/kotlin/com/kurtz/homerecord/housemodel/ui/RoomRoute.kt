package com.kurtz.homerecord.housemodel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallStage

/** Room page without the item list, for apps that keep no items (Field Log); its walls open on the Rough-in stage. */
@Composable
fun RoomRoute(repo: HomeRepository, wallPhotos: WallPhotoRepository, roomId: String, onBack: () -> Unit) {
    val vm = viewModel<RoomViewModel>(
        key = "room:$roomId",
        factory = viewModelFactory {
            initializer {
                RoomViewModel(repo, roomId, walls = wallPhotos.observeRoomWalls(roomId), defaultStage = WallStage.BehindTheWall, savedState = createSavedStateHandle())
            }
        },
    )
    RoomScreen(vm.state.collectAsState().value) { event ->
        when (event) {
            RoomEvent.Back -> onBack()
            is RoomEvent.SelectStage -> vm.selectStage(event.stage)
            else -> Unit
        }
    }
}

/** Room page with the room's items and "Add an item here" (House Manual, AC-H10); its walls open on Finished (AC-C17). */
@Composable
fun RoomRoute(
    repo: HomeRepository,
    items: ItemRepository,
    wallPhotos: WallPhotoRepository,
    homeId: String,
    roomId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onAddItem: (roomId: String) -> Unit,
) {
    val vm = viewModel<RoomViewModel>(
        key = "room:$roomId",
        factory = viewModelFactory {
            initializer {
                RoomViewModel(
                    repo, roomId, items.observeRows(homeId, emptyMap()), wallPhotos.observeRoomWalls(roomId),
                    WallStage.HouseManualDefault, createSavedStateHandle(),
                )
            }
        },
    )
    RoomScreen(vm.state.collectAsState().value) { event ->
        when (event) {
            RoomEvent.Back -> onBack()
            is RoomEvent.OpenItem -> onOpenItem(event.id)
            RoomEvent.AddItem -> onAddItem(roomId)
            is RoomEvent.SelectStage -> vm.selectStage(event.stage)
            // "Photograph this wall" arrives with T048c; until then canPhotograph is false and no button sends this.
            is RoomEvent.PhotographWall -> Unit
        }
    }
}
