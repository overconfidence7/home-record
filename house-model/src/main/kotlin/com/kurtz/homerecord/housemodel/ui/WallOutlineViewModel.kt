package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.walls.WallOutline
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallStage
import com.kurtz.homerecord.housemodel.walls.WallTextureMaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * HM-WallOutline for both apps (AC-C18, AC-C19). Use straightens the outlined wall into a texture off the main thread and
 * saves the photo as the wall's [stage] picture; Back drops the new photo. The corners survive process death.
 */
class WallOutlineViewModel(
    private val wallPhotos: WallPhotoRepository,
    private val maker: WallTextureMaker,
    private val media: MediaFiles,
    private val roomId: String,
    private val wallId: String,
    stage: WallStage,
    private val photoPath: String,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        WallOutlineUiState(
            stage = stage,
            photoPath = photoPath,
            outline = WallOutline.decode(savedState.get<String>(KEY_OUTLINE)) ?: WallOutline.Initial,
        ),
    )
    val state: StateFlow<WallOutlineUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val wall = wallPhotos.observeRoomWalls(roomId).first().firstOrNull { it.id == wallId }
            val size = maker.photoSize(photoPath)
            _state.update {
                it.copy(
                    loading = false,
                    wall = wall,
                    photoWidth = size?.width ?: 0,
                    photoHeight = size?.height ?: 0,
                    error = when {
                        size == null -> WallOutlineError.PhotoUnreadable
                        wall == null -> WallOutlineError.WallGone
                        else -> null
                    },
                )
            }
        }
    }

    fun onEvent(event: WallOutlineEvent) {
        when (event) {
            is WallOutlineEvent.DragStart -> _state.update { it.copy(dragging = event.corner) }
            is WallOutlineEvent.Drag -> setOutline(_state.value.outline.moved(event.corner, event.to), dragging = event.corner)
            WallOutlineEvent.DragEnd -> _state.update { it.copy(dragging = null) }
            WallOutlineEvent.Reset -> setOutline(WallOutline.Initial, dragging = null)
            WallOutlineEvent.Use -> use()
            // The route leaves the screen; the photo was taken only for this outline, so it goes too.
            WallOutlineEvent.Back -> if (!_state.value.done) media.discard(photoPath)
        }
    }

    private fun setOutline(outline: WallOutline, dragging: Int?) {
        savedState[KEY_OUTLINE] = outline.encode()
        _state.update {
            // Moving a handle clears "the corners cross" once they no longer do, and any failed save.
            val error = it.error.takeIf { e -> e == WallOutlineError.PhotoUnreadable || e == WallOutlineError.WallGone }
            it.copy(outline = outline, dragging = dragging, error = error)
        }
    }

    private fun use() {
        val s = _state.value
        val wall = s.wall
        if (s.saving || s.done || s.loading) return
        if (s.error == WallOutlineError.PhotoUnreadable) return
        if (wall == null) {
            _state.update { it.copy(error = WallOutlineError.WallGone) }
            return
        }
        if (!s.outline.isUsable()) {
            _state.update { it.copy(error = WallOutlineError.NotUsable) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val texture = maker.make(photoPath, s.outline, wall.lengthIn, wall.heightIn)
            if (texture == null) {
                _state.update { it.copy(saving = false, error = WallOutlineError.TextureFailed) }
                return@launch
            }
            val id = wallPhotos.save(wallId, s.stage, photoPath, texture, s.outline)
            if (id == null) {
                media.discard(texture)
                _state.update { it.copy(saving = false, error = WallOutlineError.WallGone) }
            } else {
                _state.update { it.copy(saving = false, done = true) }
            }
        }
    }

    private companion object {
        const val KEY_OUTLINE = "outline"
    }
}
