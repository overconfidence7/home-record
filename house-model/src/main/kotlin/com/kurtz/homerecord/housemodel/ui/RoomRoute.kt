package com.kurtz.homerecord.housemodel.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallStage

/** Room page without the item list, for apps that keep no items (Field Log); its walls open on the Rough-in stage. */
@Composable
fun RoomRoute(
    repo: HomeRepository,
    wallPhotos: WallPhotoRepository,
    media: MediaFiles,
    roomId: String,
    onBack: () -> Unit,
    onOutline: (WallOutlineRequest) -> Unit,
    /** Opens the room's floor in 3D standing inside the room (AC-C22); null hides "View in 3D". */
    onView3D: ((Room3DTarget) -> Unit)? = null,
) {
    val vm = viewModel<RoomViewModel>(
        key = "room:$roomId",
        factory = viewModelFactory {
            initializer {
                RoomViewModel(
                    repo, roomId, walls = wallPhotos.observeRoomWalls(roomId), defaultStage = WallStage.BehindTheWall,
                    savedState = createSavedStateHandle(), media = media, canView3D = onView3D != null,
                )
            }
        },
    )
    val state = vm.state.collectAsState().value
    WallPhotoLaunchers(state, vm, onOutline)
    RoomScreen(state) { event ->
        when (event) {
            RoomEvent.Back -> onBack()
            RoomEvent.View3D -> state.view3DTarget?.let { onView3D?.invoke(it) }
            is RoomEvent.SelectStage -> vm.selectStage(event.stage)
            is RoomEvent.OpenItem, RoomEvent.AddItem -> Unit
            else -> vm.onPhotoEvent(event)
        }
    }
}

/** Room page with the room's items and "Add an item here" (House Manual, AC-H10); its walls open on Finished (AC-C17). */
@Composable
fun RoomRoute(
    repo: HomeRepository,
    items: ItemRepository,
    wallPhotos: WallPhotoRepository,
    media: MediaFiles,
    homeId: String,
    roomId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onAddItem: (roomId: String) -> Unit,
    onOutline: (WallOutlineRequest) -> Unit,
    /** Opens the room's floor in 3D standing inside the room (AC-C22); null hides "View in 3D". */
    onView3D: ((Room3DTarget) -> Unit)? = null,
) {
    val vm = viewModel<RoomViewModel>(
        key = "room:$roomId",
        factory = viewModelFactory {
            initializer {
                RoomViewModel(
                    repo, roomId, items.observeRows(homeId, emptyMap()), wallPhotos.observeRoomWalls(roomId),
                    WallStage.HouseManualDefault, createSavedStateHandle(), media, canView3D = onView3D != null,
                )
            }
        },
    )
    val state = vm.state.collectAsState().value
    WallPhotoLaunchers(state, vm, onOutline)
    RoomScreen(state) { event ->
        when (event) {
            RoomEvent.Back -> onBack()
            is RoomEvent.OpenItem -> onOpenItem(event.id)
            RoomEvent.AddItem -> onAddItem(roomId)
            RoomEvent.View3D -> state.view3DTarget?.let { onView3D?.invoke(it) }
            is RoomEvent.SelectStage -> vm.selectStage(event.stage)
            else -> vm.onPhotoEvent(event)
        }
    }
}

/** Launches the camera or photo picker the view model asks for and opens the outline screen once a photo is in (AC-C18). */
@Composable
private fun WallPhotoLaunchers(state: RoomUiState, vm: RoomViewModel, onOutline: (WallOutlineRequest) -> Unit) {
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.onPhotoEvent(RoomEvent.CameraResult(ok)) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        vm.onPhotoEvent(RoomEvent.GalleryResult(uri?.toString()))
    }
    LaunchedEffect(state.photoLaunch) {
        val launch = state.photoLaunch ?: return@LaunchedEffect
        vm.onPhotoEvent(RoomEvent.PhotoLaunchHandled)
        val target = launch.target
        if (target != null) camera.launch(Uri.parse(target.uri)) else gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(state.outlineRequest) {
        val request = state.outlineRequest ?: return@LaunchedEffect
        vm.onPhotoEvent(RoomEvent.OutlineOpened)
        onOutline(request)
    }
}
