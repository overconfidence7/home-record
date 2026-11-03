package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayPoints
import com.kurtz.homerecord.housemodel.overlays.OverlayRepository
import com.kurtz.homerecord.housemodel.overlays.OverlaySave
import com.kurtz.homerecord.housemodel.walls.WallPhoto
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallStage
import com.kurtz.homerecord.housemodel.walls.WallTextureMaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * HM-Wall in both apps (AC-C25..C28): the wall's picture at the chosen stage with every overlay on it. Draw a new overlay
 * by tapping points along it, then fill in the form; tap one to select it, drag its points, edit or delete it. The stage,
 * the selection, the points being drawn and the open form survive process death through [savedState].
 */
class WallOverlaysViewModel(
    private val repo: HomeRepository,
    private val wallPhotos: WallPhotoRepository,
    private val overlays: OverlayRepository,
    private val photoSize: WallTextureMaker?,
    private val roomId: String,
    private val wallId: String,
    defaultStage: WallStage,
    selectId: String? = null,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /** Where an overlay's photo is kept; null leaves the photo off the form. */
    private val media: MediaFiles? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(WallOverlaysUiState())
    val state: StateFlow<WallOverlaysUiState> = _state.asStateFlow()
    private val forms = OverlayFormController(
        savedState, media, viewModelScope,
        current = { _state.value.form },
        show = { form -> _state.update { it.copy(form = form) } },
        launchPhoto = { launch -> _state.update { it.copy(photoLaunch = launch) } },
    )

    private var homeId: String? = null
    private var photos: Map<WallStage, WallPhoto> = emptyMap()

    init {
        _state.value = restore(defaultStage, selectId).copy(roomId = roomId, canAddPhoto = media != null)
        viewModelScope.launch {
            val home = repo.roomLocation(roomId)?.homeId ?: return@launch
            homeId = home
            overlays.observeItemChoices(home).collect { items -> _state.update { it.copy(items = items) } }
        }
        viewModelScope.launch {
            combine(wallPhotos.observeRoomWalls(roomId), repo.observeRoom(roomId), ::Pair).collect { (walls, room) ->
                val wall = walls.firstOrNull { it.id == wallId }
                _state.update {
                    it.copy(loading = false, wall = wall, roomName = room?.name, error = if (wall == null) WallOverlaysError.WallGone else it.error)
                }
            }
        }
        viewModelScope.launch {
            wallPhotos.observeByStage(wallId).collect { byStage ->
                photos = byStage
                refreshPicture()
            }
        }
        viewModelScope.launch {
            overlays.observeForWall(wallId).collect { list ->
                _state.update { s -> s.copy(overlays = list, selectedId = s.selectedId?.takeIf { id -> list.any { it.id == id } }) }
            }
        }
    }

    fun onEvent(event: WallOverlaysEvent) {
        when (event) {
            WallOverlaysEvent.Back -> Unit
            is WallOverlaysEvent.SelectStage -> {
                savedState[KEY_STAGE] = event.stage.name
                _state.update { it.copy(stage = event.stage) }
                viewModelScope.launch { refreshPicture() }
            }
            is WallOverlaysEvent.Select -> {
                savedState[KEY_SELECTED] = event.id
                _state.update { it.copy(selectedId = event.id) }
            }
            WallOverlaysEvent.StartDrawing -> setDrawing(emptyList(), clearSelection = true)
            is WallOverlaysEvent.AddPoint -> _state.value.drawing?.let { setDrawing(it + event.point.clampedToWall()) }
            WallOverlaysEvent.UndoPoint -> _state.value.drawing?.let { setDrawing(it.dropLast(1)) }
            WallOverlaysEvent.CancelDrawing -> setDrawing(null)
            WallOverlaysEvent.FinishDrawing -> {
                val points = _state.value.drawing?.takeIf { it.isNotEmpty() } ?: return
                setDrawing(null)
                setForm(OverlayForm(points = points, stage = _state.value.stage))
            }
            is WallOverlaysEvent.MovePoint -> movePoint(event)
            WallOverlaysEvent.EditSelected -> {
                val o = _state.value.selected ?: return
                setForm(OverlayForm(
                    editingId = o.id, points = o.points, kind = o.kind, label = o.label, note = o.note.orEmpty(), depth = o.depth, stage = o.stage,
                    photoPath = o.photoPath, itemId = o.itemId,
                ))
            }
            WallOverlaysEvent.AskDelete -> if (_state.value.selected != null) _state.update { it.copy(confirmDelete = true) }
            WallOverlaysEvent.CancelDelete -> _state.update { it.copy(confirmDelete = false) }
            WallOverlaysEvent.ConfirmDelete -> {
                val id = _state.value.selectedId
                savedState[KEY_SELECTED] = null
                _state.update { it.copy(confirmDelete = false, selectedId = null) }
                if (id != null) viewModelScope.launch { overlays.delete(id) }
            }
            WallOverlaysEvent.DismissError -> _state.update { it.copy(error = null) }
            WallOverlaysEvent.Save -> save()
            WallOverlaysEvent.PhotoLaunchHandled -> _state.update { it.copy(photoLaunch = null) }
            else -> forms.onEvent(event)
        }
    }

    private suspend fun refreshPicture() {
        val stage = _state.value.stage
        val photo = photos[stage]
        val picture = when {
            photo == null -> null
            photo.texturePath != null -> WallPicture(photo.texturePath, null, wallAspect())
            else -> {
                val size = photoSize?.photoSize(photo.originalPath)
                WallPicture(photo.originalPath, photo.outline, size?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: (4f / 3f))
            }
        }
        // A later stage switch or photo list may have finished first while this one read the photo's size (R10).
        if (photo != photos[_state.value.stage] || stage != _state.value.stage) return
        _state.update { it.copy(picture = picture) }
    }

    private fun wallAspect(): Float {
        val wall = _state.value.wall ?: return 0f
        return if (wall.lengthIn > 0 && wall.heightIn > 0) wall.lengthIn.toFloat() / wall.heightIn else 0f
    }

    private fun movePoint(event: WallOverlaysEvent.MovePoint) {
        val to = event.to.clampedToWall()
        // Shown moved at once; the row written below comes back the same through the flow.
        _state.update { s ->
            s.copy(overlays = s.overlays.map { o ->
                if (o.id != event.id || event.index !in o.points.indices) o else o.copy(points = o.points.toMutableList().also { it[event.index] = to })
            })
        }
        viewModelScope.launch { overlays.movePoint(event.id, event.index, to) }
    }

    private fun save() {
        val (editing, draft) = forms.draftForSave() ?: return
        viewModelScope.launch {
            val result = when {
                editing != null -> overlays.update(editing, draft)
                else -> {
                    val home = homeId ?: repo.roomLocation(roomId)?.homeId
                    if (home == null) null else overlays.addOnWall(home, wallId, draft)
                }
            }
            if (result is OverlaySave.Saved) {
                setForm(null)
                savedState[KEY_SELECTED] = result.id
                _state.update { it.copy(selectedId = result.id) }
            } else {
                forms.saveFailed()
                _state.update { it.copy(error = WallOverlaysError.SaveFailed) }
            }
        }
    }

    private fun setDrawing(points: List<com.kurtz.homerecord.housemodel.overlays.OverlayPoint>?, clearSelection: Boolean = false) {
        savedState[KEY_DRAWING] = points?.let(OverlayPoints::encode)
        if (clearSelection) savedState[KEY_SELECTED] = null
        _state.update { it.copy(drawing = points, selectedId = if (clearSelection) null else it.selectedId) }
    }

    private fun setForm(form: OverlayForm?) = forms.open(form)

    private fun restore(defaultStage: WallStage, selectId: String?): WallOverlaysUiState {
        val stage = WallStage.entries.firstOrNull { it.name == savedState.get<String>(KEY_STAGE) } ?: defaultStage
        val drawing = savedState.get<String>(KEY_DRAWING)?.let(OverlayPoints::decode)
        val selected = if (savedState.contains(KEY_SELECTED)) savedState.get<String>(KEY_SELECTED) else selectId
        return WallOverlaysUiState(stage = stage, drawing = drawing, selectedId = selected, form = forms.restore())
    }

    private fun com.kurtz.homerecord.housemodel.overlays.OverlayPoint.clampedToWall() =
        com.kurtz.homerecord.housemodel.overlays.OverlayPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))

    private companion object {
        const val KEY_STAGE = "stage"
        const val KEY_SELECTED = "selected"
        const val KEY_DRAWING = "drawing"
    }
}

/** The overlay's name on screen: its label, else its kind's name (resolved by the caller). */
fun Overlay.title(kindName: String): String = label.ifBlank { kindName }
