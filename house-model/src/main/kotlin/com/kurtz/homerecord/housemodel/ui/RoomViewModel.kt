package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.walls.RemovedWallPhoto
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * HM-Room for both apps (AC-C14, AC-H10, AC-C17). The room follows the plan, so a rename or a delete on another
 * device shows here; [itemRows] is the home's items in House Manual and null in Field Log, which keeps none.
 * [walls] is the room's walls with their photos; the chosen stage survives process death through [savedState].
 */
class RoomViewModel(
    private val repo: HomeRepository,
    private val roomId: String,
    itemRows: Flow<List<ItemRow>>? = null,
    walls: Flow<List<RoomWall>>? = null,
    defaultStage: WallStage = WallStage.HouseManualDefault,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /** Where wall photos go; null where the app cannot take one, which hides "Photograph". */
    private val media: MediaFiles? = null,
    /** The app has a 3D view to open the room in (AC-C22). */
    canView3D: Boolean = false,
    /** The room's overlays (AC-C25); with it, wall rows open HM-Wall. */
    overlays: Flow<List<Overlay>>? = null,
    /** Photos of the room's removed walls (N001). */
    removedWallPhotos: Flow<List<RemovedWallPhoto>>? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        RoomUiState(
            canAddItem = itemRows != null,
            canView3D = canView3D,
            canPhotograph = media != null && walls != null,
            wallStage = WallStage.entries.firstOrNull { it.name == savedState.get<String>(KEY_STAGE) } ?: defaultStage,
            canOpenWalls = overlays != null && walls != null,
            overlays = if (overlays != null) emptyList() else null,
        ),
    )
    val state: StateFlow<RoomUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val where = repo.roomLocation(roomId)
            _state.update { it.copy(floorName = where?.floorName, floorId = where?.floorId, homeId = where?.homeId, homeName = where?.homeName) }
            repo.observeRoom(roomId).collect { room -> _state.update { it.copy(loading = false, room = room) } }
        }
        if (itemRows != null) {
            viewModelScope.launch {
                itemRows.collect { rows -> _state.update { it.copy(items = roomItemsFor(rows, roomId)) } }
            }
        }
        if (walls != null) {
            viewModelScope.launch { walls.collect { list -> _state.update { it.copy(walls = list) } } }
        }
        if (removedWallPhotos != null) {
            viewModelScope.launch { removedWallPhotos.collect { list -> _state.update { it.copy(removedWallPhotos = list) } } }
        }
        if (overlays != null) {
            viewModelScope.launch { overlays.collect { list -> _state.update { it.copy(overlays = list) } } }
        }
    }

    fun selectStage(stage: WallStage) {
        savedState[KEY_STAGE] = stage.name
        _state.update { it.copy(wallStage = stage) }
    }

    /**
     * "Photograph this wall" (AC-C18): the sheet offers the camera or the photo picker; the wall, stage and camera file wait
     * in [savedState] so a result after rotation or process death still reaches the outline screen.
     */
    fun onPhotoEvent(event: RoomEvent) {
        val files = media ?: return
        when (event) {
            is RoomEvent.PhotographWall -> _state.update { it.copy(photoSheetFor = event.wallId, photoImportFailed = false) }
            RoomEvent.ClosePhotoSheet -> _state.update { it.copy(photoSheetFor = null) }
            RoomEvent.TakeWallPhoto -> {
                val wallId = _state.value.photoSheetFor ?: return
                val target = files.newCaptureTarget()
                keepPending(wallId, target.path)
                _state.update { it.copy(photoSheetFor = null, photoLaunch = WallPhotoLaunch(target)) }
            }
            RoomEvent.ChooseWallPhoto -> {
                val wallId = _state.value.photoSheetFor ?: return
                keepPending(wallId, null)
                _state.update { it.copy(photoSheetFor = null, photoLaunch = WallPhotoLaunch(null)) }
            }
            RoomEvent.PhotoLaunchHandled -> _state.update { it.copy(photoLaunch = null) }
            is RoomEvent.CameraResult -> {
                val wallId = savedState.get<String>(KEY_PENDING_WALL)
                val stage = pendingStage()
                val path = savedState.get<String>(KEY_PENDING_PATH)
                keepPending(null, null)
                if (wallId == null || path == null) return
                if (event.ok && files.exists(path)) {
                    _state.update { it.copy(outlineRequest = WallOutlineRequest(wallId, stage, path)) }
                } else {
                    files.discard(path)
                }
            }
            is RoomEvent.GalleryResult -> {
                val wallId = savedState.get<String>(KEY_PENDING_WALL)
                val stage = pendingStage()
                keepPending(null, null)
                val uri = event.uri
                if (wallId == null || uri == null) return
                viewModelScope.launch {
                    val path = files.import(uri)
                    _state.update {
                        if (path == null) it.copy(photoImportFailed = true) else it.copy(outlineRequest = WallOutlineRequest(wallId, stage, path))
                    }
                }
            }
            RoomEvent.OutlineOpened -> _state.update { it.copy(outlineRequest = null) }
            else -> Unit
        }
    }

    private fun keepPending(wallId: String?, path: String?) {
        savedState[KEY_PENDING_WALL] = wallId
        savedState[KEY_PENDING_PATH] = path
        savedState[KEY_PENDING_STAGE] = if (wallId == null) null else _state.value.wallStage.name
    }

    private fun pendingStage(): WallStage =
        WallStage.entries.firstOrNull { it.name == savedState.get<String>(KEY_PENDING_STAGE) } ?: _state.value.wallStage

    private companion object {
        const val KEY_STAGE = "wallStage"
        const val KEY_PENDING_WALL = "pendingWall"
        const val KEY_PENDING_PATH = "pendingPath"
        const val KEY_PENDING_STAGE = "pendingStage"
    }
}
