package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.RoomChoice
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * FL-BehindWall (AC-F49): the job's rooms, then one room's walls; a wall opens the camera (or the gallery) and the photo
 * goes to the outline screen at the stage [com.kurtz.homerecord.housemodel.walls.BehindTheWall] picks for that wall:
 * Rough-in first, Finished once the Rough-in photo is in. The open room and the pending photo survive process death.
 */
class BehindWallViewModel(
    title: String,
    private val rooms: suspend () -> List<RoomChoice>,
    private val walls: (roomId: String) -> Flow<List<RoomWall>>,
    private val media: MediaFiles,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /** Opens straight on this room's walls; Back then leaves the screen instead of going to the rooms. */
    private val startRoomId: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(BehindWallUiState(title = title, roomId = savedState.get<String>(KEY_ROOM) ?: startRoomId))
    val state: StateFlow<BehindWallUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val choices = rooms()
            val flows = choices.map { c -> walls(c.id).let { f -> combine(f, flowOf(c)) { w, room -> BehindWallRoom(room.id, room.name, room.floorName, w) } } }
            if (flows.isEmpty()) {
                _state.update { it.copy(loading = false, rooms = emptyList()) }
            } else {
                combine(flows) { it.toList() }.collect { list -> _state.update { it.copy(loading = false, rooms = list) } }
            }
        }
    }

    /** Handles a back press: the walls step goes back to the rooms; returns false when the screen should close. */
    fun back(): Boolean {
        val s = _state.value
        if (s.roomId == null || s.roomId == startRoomId) return false
        savedState[KEY_ROOM] = null
        _state.update { it.copy(roomId = null, photoSheetFor = null, photoImportFailed = false) }
        return true
    }

    fun onEvent(event: BehindWallEvent) {
        when (event) {
            is BehindWallEvent.OpenRoom -> {
                savedState[KEY_ROOM] = event.roomId
                _state.update { it.copy(roomId = event.roomId, photoImportFailed = false) }
            }
            is BehindWallEvent.PickWall -> _state.update { it.copy(photoSheetFor = event.wallId, photoImportFailed = false) }
            BehindWallEvent.CloseSheet -> _state.update { it.copy(photoSheetFor = null) }
            BehindWallEvent.TakePhoto -> {
                val wall = sheetWall() ?: return
                val target = media.newCaptureTarget()
                keepPending(wall, target.path)
                _state.update { it.copy(photoSheetFor = null, photoLaunch = WallPhotoLaunch(target)) }
            }
            BehindWallEvent.ChoosePhoto -> {
                val wall = sheetWall() ?: return
                keepPending(wall, null)
                _state.update { it.copy(photoSheetFor = null, photoLaunch = WallPhotoLaunch(null)) }
            }
            BehindWallEvent.PhotoLaunchHandled -> _state.update { it.copy(photoLaunch = null) }
            is BehindWallEvent.CameraResult -> {
                val wallId = savedState.get<String>(KEY_PENDING_WALL)
                val stage = pendingStage()
                val path = savedState.get<String>(KEY_PENDING_PATH)
                clearPending()
                if (wallId == null || path == null || stage == null) return
                if (event.ok && media.exists(path)) {
                    _state.update { it.copy(outlineRequest = WallOutlineRequest(wallId, stage, path)) }
                } else {
                    media.discard(path)
                }
            }
            is BehindWallEvent.GalleryResult -> {
                val wallId = savedState.get<String>(KEY_PENDING_WALL)
                val stage = pendingStage()
                clearPending()
                val uri = event.uri
                if (wallId == null || uri == null || stage == null) return
                viewModelScope.launch {
                    val path = media.import(uri)
                    _state.update {
                        if (path == null) it.copy(photoImportFailed = true) else it.copy(outlineRequest = WallOutlineRequest(wallId, stage, path))
                    }
                }
            }
            BehindWallEvent.OutlineOpened -> _state.update { it.copy(outlineRequest = null) }
            BehindWallEvent.Back, BehindWallEvent.EditPlan, BehindWallEvent.OpenRoomPage -> Unit
        }
    }

    private fun sheetWall(): RoomWall? {
        val s = _state.value
        val id = s.photoSheetFor ?: return null
        return s.room?.walls?.firstOrNull { it.id == id }
    }

    /** The wall and the stage its photo goes to are fixed when the camera opens, so a result after process death lands the same. */
    private fun keepPending(wall: RoomWall, path: String?) {
        savedState[KEY_PENDING_WALL] = wall.id
        savedState[KEY_PENDING_PATH] = path
        savedState[KEY_PENDING_STAGE] = wall.nextBehindWallStage.name
    }

    private fun clearPending() {
        savedState[KEY_PENDING_WALL] = null
        savedState[KEY_PENDING_PATH] = null
        savedState[KEY_PENDING_STAGE] = null
    }

    private fun pendingStage(): WallStage? = WallStage.entries.firstOrNull { it.name == savedState.get<String>(KEY_PENDING_STAGE) }

    private companion object {
        const val KEY_ROOM = "behindWallRoom"
        const val KEY_PENDING_WALL = "pendingWall"
        const val KEY_PENDING_PATH = "pendingPath"
        const val KEY_PENDING_STAGE = "pendingStage"
    }
}
