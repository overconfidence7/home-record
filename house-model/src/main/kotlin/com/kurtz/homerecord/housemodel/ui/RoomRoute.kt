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
import com.kurtz.homerecord.housemodel.overlays.OverlayRepository
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
    /** The room's overlays; with [onOpenWall], wall rows open HM-Wall to draw them (AC-C25..C28). */
    overlays: OverlayRepository? = null,
    onOpenWall: ((WallOpenRequest) -> Unit)? = null,
    /** Opens the plan editor with a plan overlay picked (AC-C28). */
    onOpenPlanOverlay: (String) -> Unit = {},
) {
    val vm = viewModel<RoomViewModel>(
        key = "room:$roomId",
        factory = viewModelFactory {
            initializer {
                RoomViewModel(
                    repo, roomId, walls = wallPhotos.observeRoomWalls(roomId), defaultStage = WallStage.BehindTheWall,
                    savedState = createSavedStateHandle(), media = media, canView3D = onView3D != null,
                    overlays = overlays?.takeIf { onOpenWall != null }?.observeForRoom(roomId),
                    removedWallPhotos = wallPhotos.observeRemovedWallPhotos(roomId),
                )
            }
        },
    )
    val state = vm.state.collectAsState().value.copy(system = LocalUnitSystem.current)
    WallPhotoLaunchers(state, vm, onOutline)
    RoomScreen(state) { event ->
        when (event) {
            RoomEvent.Back -> onBack()
            RoomEvent.View3D -> state.view3DTarget?.let { onView3D?.invoke(it) }
            is RoomEvent.SelectStage -> vm.selectStage(event.stage)
            is RoomEvent.OpenWall -> onOpenWall?.invoke(WallOpenRequest(event.wallId, state.wallStage, event.overlayId))
            is RoomEvent.OpenPlanOverlay -> onOpenPlanOverlay(event.overlayId)
            is RoomEvent.OpenItem, RoomEvent.AddItem, is RoomEvent.OpenBreaker -> Unit
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
    /** "Add an item here"; null hides it (a Field Log member who cannot edit the job's house, T062). */
    onAddItem: ((roomId: String) -> Unit)?,
    onOutline: (WallOutlineRequest) -> Unit,
    /** Opens the room's floor in 3D standing inside the room (AC-C22); null hides "View in 3D". */
    onView3D: ((Room3DTarget) -> Unit)? = null,
    /** The room's overlays; with [onOpenWall], wall rows open HM-Wall to draw them (AC-C25..C28). */
    overlays: OverlayRepository? = null,
    onOpenWall: ((WallOpenRequest) -> Unit)? = null,
    /** Opens the plan editor with a plan overlay picked (AC-C28). */
    onOpenPlanOverlay: (String) -> Unit = {},
    /** The stage the walls open on: Finished in House Manual (AC-C17), Rough-in on a job (AC-F49). */
    defaultStage: WallStage = WallStage.HouseManualDefault,
    /** The home's panels; with [onOpenBreaker], the room lists the breakers feeding it (AC-C41). */
    panels: com.kurtz.homerecord.housemodel.panel.PanelRepository? = null,
    onOpenBreaker: ((com.kurtz.homerecord.housemodel.panel.FeedingBreaker) -> Unit)? = null,
) {
    val vm = viewModel<RoomViewModel>(
        key = "room:$roomId",
        factory = viewModelFactory {
            initializer {
                RoomViewModel(
                    repo, roomId, items.observeRows(homeId, emptyMap()), wallPhotos.observeRoomWalls(roomId),
                    defaultStage, createSavedStateHandle(), media, canView3D = onView3D != null,
                    overlays = overlays?.takeIf { onOpenWall != null }?.observeForRoom(roomId),
                    removedWallPhotos = wallPhotos.observeRemovedWallPhotos(roomId),
                    canAddItem = onAddItem != null,
                    feeding = panels?.takeIf { onOpenBreaker != null }?.observeFeedingRoom(roomId),
                )
            }
        },
    )
    val state = vm.state.collectAsState().value.copy(system = LocalUnitSystem.current)
    WallPhotoLaunchers(state, vm, onOutline)
    RoomScreen(state) { event ->
        when (event) {
            RoomEvent.Back -> onBack()
            is RoomEvent.OpenItem -> onOpenItem(event.id)
            is RoomEvent.OpenBreaker -> onOpenBreaker?.invoke(event.breaker)
            RoomEvent.AddItem -> onAddItem?.invoke(roomId)
            RoomEvent.View3D -> state.view3DTarget?.let { onView3D?.invoke(it) }
            is RoomEvent.SelectStage -> vm.selectStage(event.stage)
            is RoomEvent.OpenWall -> onOpenWall?.invoke(WallOpenRequest(event.wallId, state.wallStage, event.overlayId))
            is RoomEvent.OpenPlanOverlay -> onOpenPlanOverlay(event.overlayId)
            else -> vm.onPhotoEvent(event)
        }
    }
}

/** HM-Wall for [wallId] opened from the room page at [stage], with [overlayId] picked (AC-C28). */
data class WallOpenRequest(val wallId: String, val stage: WallStage, val overlayId: String?)

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
