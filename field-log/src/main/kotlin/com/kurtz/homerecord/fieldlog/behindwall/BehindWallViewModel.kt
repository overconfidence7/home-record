package com.kurtz.homerecord.fieldlog.behindwall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.WallRow
import com.kurtz.homerecord.core.logic.WallRows
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.repo.BehindWallRepository
import com.kurtz.homerecord.core.repo.JobRepository
import com.kurtz.homerecord.fieldlog.capture.PhotoCapturer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WallStep { ROOM, WALL, CAMERA }

data class BehindWallUiState(
    val loaded: Boolean = false,
    val rooms: List<Room> = emptyList(),
    val roomId: String? = null,
    val walls: List<WallRow> = emptyList(),
    val wallId: String? = null,
    val customLabel: String = "",
    val busy: Boolean = false,
    val failed: Boolean = false,
    val photosThisSession: Int = 0,
) {
    val room: Room? get() = rooms.firstOrNull { it.id == roomId }
    val wall: WallRow? get() = walls.firstOrNull { it.wallId != null && it.wallId == wallId }
    val step: WallStep
        get() = when {
            room == null -> WallStep.ROOM
            wall == null -> WallStep.WALL
            else -> WallStep.CAMERA
        }
}

sealed interface BehindWallEvent {
    data class SelectRoom(val roomId: String) : BehindWallEvent
    data class SelectWall(val label: String) : BehindWallEvent
    data class CustomChanged(val text: String) : BehindWallEvent
    data object AddCustom : BehindWallEvent
    data object Back : BehindWallEvent
    data object Shutter : BehindWallEvent
}

/** FL-BehindWall: room → wall → camera (AC-63..AC-65). Standard wall labels are passed in already localised. */
@OptIn(ExperimentalCoroutinesApi::class)
class BehindWallViewModel(
    private val jobId: String,
    private val standardLabels: List<String>,
    private val jobs: JobRepository,
    private val walls: BehindWallRepository,
    private val media: MediaFileStore,
    private val capturer: PhotoCapturer,
) : ViewModel() {
    private data class Selection(
        val roomId: String? = null,
        val wallId: String? = null,
        val customLabel: String = "",
        val busy: Boolean = false,
        val failed: Boolean = false,
        val photos: Int = 0,
    )

    private val selection = MutableStateFlow(Selection())
    private val homeId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { homeId.value = jobs.getJob(jobId)?.homeId }
    }

    private val roomsFlow = homeId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else walls.observeRooms(id) }
    private val photosFlow = homeId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else walls.observePhotos(id) }
    private val wallsFlow = selection.flatMapLatest { s ->
        if (s.roomId == null) flowOf(emptyList()) else walls.observeWalls(s.roomId)
    }

    val state: StateFlow<BehindWallUiState> = combine(selection, roomsFlow, wallsFlow, photosFlow, homeId) { s, rooms, wallList, photos, home ->
        BehindWallUiState(
            loaded = home != null,
            rooms = rooms,
            roomId = s.roomId,
            walls = WallRows.build(standardLabels, wallList, photos),
            wallId = s.wallId,
            customLabel = s.customLabel,
            busy = s.busy,
            failed = s.failed,
            photosThisSession = s.photos,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BehindWallUiState())

    fun onEvent(event: BehindWallEvent) {
        when (event) {
            is BehindWallEvent.SelectRoom -> selection.update { Selection(roomId = event.roomId) }
            is BehindWallEvent.SelectWall -> chooseWall(event.label)
            is BehindWallEvent.CustomChanged -> selection.update { it.copy(customLabel = event.text) }
            BehindWallEvent.AddCustom -> chooseWall(selection.value.customLabel)
            BehindWallEvent.Back -> selection.update {
                when {
                    it.wallId != null -> it.copy(wallId = null, failed = false)
                    else -> Selection()
                }
            }
            BehindWallEvent.Shutter -> onShutter()
        }
    }

    private fun chooseWall(label: String) {
        val roomId = selection.value.roomId ?: return
        viewModelScope.launch {
            val wall = walls.ensureWall(roomId, label) ?: return@launch
            selection.update { it.copy(wallId = wall.id, customLabel = "", photos = 0, failed = false) }
        }
    }

    /** AC-64: capture to media/, then save the BEHIND_WALL Photo and its PHOTO entry. */
    fun onShutter() {
        val s = selection.value
        val roomId = s.roomId ?: return
        val wallId = s.wallId ?: return
        if (s.busy) return
        selection.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            val home = homeId.value
            val path = media.newPhotoPath()
            val file = media.resolveForWrite(path)
            val saved = home != null && file != null && try {
                capturer.capture(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (saved && home != null) walls.addPhoto(jobId, home, roomId, wallId, path) else media.delete(path)
            selection.update { it.copy(busy = false, failed = !saved, photos = it.photos + if (saved) 1 else 0) }
        }
    }
}
