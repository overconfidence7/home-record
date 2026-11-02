package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.defaultFloor
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.plan.GridSnapper
import com.kurtz.homerecord.housemodel.plan.PlanEditor
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.PlanValidator
import com.kurtz.homerecord.housemodel.plan.RoomKind
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.overlays.OverlayPoints
import com.kurtz.homerecord.housemodel.overlays.OverlayRepository
import com.kurtz.homerecord.housemodel.overlays.OverlaySave
import com.kurtz.homerecord.housemodel.overlays.PlanOverlayPlacer
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * HM-Plan (AC-C5, AC-C6, AC-C10, AC-C12; overlays AC-C25, AC-C28). With [overlays] the editor also has an Overlays mode:
 * tap points to draw a run or a spot on the plan in plan inches, fill in the same form as on a wall, pick one to drag its
 * points, edit or delete it. A plan overlay is filed under the room it starts in ([PlanOverlayPlacer]). Every committed edit is saved to the database right away, so leaving the
 * editor or losing the process never drops work. A typed name or size is one edit: it is held (in [SavedStateHandle])
 * until IME Done, focus leaves the field, or the next other edit, then becomes one undo step and one save. Floor, selection and view survive through [SavedStateHandle].
 */
class PlanEditorViewModel(
    private val repo: HomeRepository,
    private val homeId: String,
    private val saved: SavedStateHandle,
    private val system: UnitSystem = UnitSystem.IMPERIAL,
    private val newId: () -> String = Ids::newId,
    initialFloorId: String? = null,
    initialRoomId: String? = null,
    initialEditShape: Boolean = false,
    private val overlays: OverlayRepository? = null,
    /** Where an overlay's photo is kept; null leaves the photo off the overlay form. */
    media: MediaFiles? = null,
    /** The stage a new plan overlay is recorded at (Finished in House Manual, Rough-in on a job). */
    private val overlayStage: WallStage = WallStage.HouseManualDefault,
    /** Opens in Overlays mode on this overlay's floor with it picked (a search result or a link, AC-C28). */
    initialOverlayId: String? = null,
) : ViewModel() {
    private var pendingEditShape = initialEditShape
    private val snapper = if (system == UnitSystem.METRIC) GridSnapper(gridIn = 4) else GridSnapper()
    private val validator = PlanValidator()
    private val saveLock = Mutex()
    private var editor = PlanEditor(emptyList(), snapper)

    private val _state = MutableStateFlow(
        PlanEditorUiState(
            floorId = saved[FLOOR] ?: initialFloorId,
            selectedId = saved[SELECTED] ?: initialRoomId,
            editShape = saved[EDIT_SHAPE] ?: false,
            zoom = saved[ZOOM] ?: 1f,
            panX = saved[PAN_X] ?: 0f,
            panY = saved[PAN_Y] ?: 0f,
            system = system,
            canEditOverlays = overlays != null,
            canAddPhoto = overlays != null && media != null,
            overlayMode = overlays != null && (saved[OVERLAY_MODE] ?: (initialOverlayId != null)),
            overlaySelectedId = if (saved.contains(OVERLAY_SELECTED)) saved[OVERLAY_SELECTED] else initialOverlayId,
            drawing = saved.get<String>(DRAWING)?.let(OverlayPoints::decode),
        ),
    )
    val state: StateFlow<PlanEditorUiState> = _state.asStateFlow()
    private val forms = OverlayFormController(
        saved, media, viewModelScope,
        current = { _state.value.overlayForm },
        show = { form -> _state.update { it.copy(overlayForm = form) } },
        launchPhoto = { launch -> _state.update { it.copy(photoLaunch = launch) } },
    )
    private var homeOverlays: List<Overlay> = emptyList()

    init {
        _state.update { it.copy(overlayForm = forms.restore()) }
        if (overlays != null) {
            viewModelScope.launch {
                overlays.observeForHome(homeId).collect { list ->
                    homeOverlays = list
                    refresh()
                }
            }
            viewModelScope.launch { overlays.observeItemChoices(homeId).collect { items -> _state.update { it.copy(items = items) } } }
        }
        viewModelScope.launch {
            // Opened on an overlay with no floor given: start on the floor of the room it is filed under.
            if (_state.value.floorId == null && initialOverlayId != null) {
                val floor = overlays?.get(initialOverlayId)?.roomId?.let { repo.roomLocation(it)?.floorId }
                if (floor != null) _state.update { it.copy(floorId = floor) }
            }
            repo.observeFloors(homeId).collect { floors ->
                val current = _state.value.floorId
                val chosen = floors.firstOrNull { it.id == current }
                    ?: floors.defaultFloor()
                _state.update { it.copy(floors = floors) }
                if (chosen == null) {
                    _state.update { it.copy(loading = false, floorId = null) }
                } else if (chosen.id != current || _state.value.loading) {
                    loadFloor(chosen)
                }
            }
        }
    }

    fun onEvent(event: PlanEditorEvent) {
        // Typing stays pending; any other edit, selection, undo or floor change first commits it.
        if (event !is PlanEditorEvent.Rename && event !is PlanEditorEvent.SetSize &&
            event !is PlanEditorEvent.Transform && event !is PlanEditorEvent.Fit && event !is PlanEditorEvent.OverlayEvent
        ) {
            commitFields()
        }
        when (event) {
            is PlanEditorEvent.SelectFloor -> floorById(event.floorId)?.let { launch { loadFloor(it) } }
            is PlanEditorEvent.AddRoom -> {
                val room = editor.addRoom(newId(), "", RoomKind.OTHER, event.center)
                commit(room.id, focusName = true)
            }
            is PlanEditorEvent.PlaceRoom -> {
                val u = _state.value.unplaced.firstOrNull { it.id == event.roomId } ?: return
                val probe = PlanEditor(emptyList(), snapper).addRoom(u.id, u.name, RoomKind.parse(u.kind), event.center)
                editor.place(probe)
                _state.update { it.copy(unplaced = it.unplaced.filterNot { r -> r.id == u.id }) }
                commit(u.id, focusName = false)
            }
            is PlanEditorEvent.Select -> select(event.roomId)
            is PlanEditorEvent.DragMove -> {
                editor.dragMove(event.roomId, event.dxIn, event.dyIn)
                if (_state.value.selectedId != event.roomId) select(event.roomId)
                refresh()
            }
            is PlanEditorEvent.DragEdge -> {
                editor.dragEdge(event.roomId, event.edgeIndex, event.deltaIn)
                refresh()
            }
            PlanEditorEvent.DragEnd -> {
                editor.commit()
                commit(_state.value.selectedId, focusName = false)
            }
            is PlanEditorEvent.Rename -> selectedId()?.let { id ->
                editor.previewRename(id, event.name)
                hold(id) { saved[PENDING_NAME] = event.name }
                refresh()
            }
            is PlanEditorEvent.SetKind -> selectedId()?.let { editor.setKind(it, event.kind); commit(it, bumpFields = false) }
            is PlanEditorEvent.SetSize -> selectedId()?.let { id ->
                hold(id) {
                    saved[PENDING_WIDTH] = event.widthIn
                    saved[PENDING_LENGTH] = event.lengthIn
                }
            }
            PlanEditorEvent.CommitFields -> Unit
            is PlanEditorEvent.SetEditShape -> setEditShape(event.on && selectedId() != null)
            is PlanEditorEvent.AddCorner -> selectedId()?.let { id ->
                if (editor.addCorner(id, event.edgeIndex, event.at) != null) commit(id, bumpFields = false)
            }
            is PlanEditorEvent.DragCorner -> selectedId()?.let { id ->
                editor.dragCorner(id, event.index, event.dxIn, event.dyIn)
                refresh()
            }
            is PlanEditorEvent.DeleteCorner -> selectedId()?.let { id ->
                if (editor.deleteCorner(id, event.index)) commit(id)
            }
            is PlanEditorEvent.SelectWall -> {
                _state.update { it.copy(selectedWall = event.edgeIndex, wallTap = event.at, selectedOpeningId = null) }
            }
            is PlanEditorEvent.AddOpening -> selectedId()?.let { id ->
                val state = _state.value
                val edge = state.selectedWall ?: return
                val openingId = newId()
                if (editor.addOpening(id, openingId, edge, event.kind, state.wallTap) != null) {
                    commit(id, bumpFields = false)
                    _state.update { it.copy(selectedOpeningId = openingId, selectedWall = null) }
                }
            }
            is PlanEditorEvent.SelectOpening -> _state.update { it.copy(selectedOpeningId = event.openingId, selectedWall = null) }
            is PlanEditorEvent.DragOpening -> selectedId()?.let { id ->
                editor.dragOpening(id, event.openingId, event.at)
                if (_state.value.selectedOpeningId != event.openingId) _state.update { it.copy(selectedOpeningId = event.openingId, selectedWall = null) }
                refresh()
            }
            is PlanEditorEvent.SetOpeningWidth -> selectedId()?.let { id ->
                _state.value.selectedOpeningId?.let { editor.setOpeningWidth(id, it, event.widthIn); commit(id, bumpFields = false) }
            }
            PlanEditorEvent.DeleteOpening -> selectedId()?.let { id ->
                _state.value.selectedOpeningId?.let { editor.deleteOpening(id, it); _state.update { s -> s.copy(selectedOpeningId = null) }; commit(id, bumpFields = false) }
            }
            PlanEditorEvent.AddStair -> selectedId()?.let { id ->
                // A new staircase leads to the floor above when there is one, otherwise the floor below.
                val floors = _state.value.floors
                val here = floors.indexOfFirst { it.id == _state.value.floorId }
                val target = (floors.getOrNull(here + 1) ?: floors.getOrNull(here - 1))?.id
                if (editor.addStair(id, newId(), target)) commit(id, bumpFields = false)
            }
            is PlanEditorEvent.LinkStair -> selectedId()?.let { editor.linkStair(it, event.stairId, event.toFloorId); commit(it, bumpFields = false) }
            is PlanEditorEvent.DeleteStair -> selectedId()?.let { editor.deleteStair(it, event.stairId); commit(it, bumpFields = false) }
            PlanEditorEvent.Delete -> selectedId()?.let { editor.delete(it); commit(null) }
            PlanEditorEvent.Undo -> { editor.undo(); keepSelectionIfPresent(); commit(_state.value.selectedId) }
            PlanEditorEvent.Redo -> { editor.redo(); keepSelectionIfPresent(); commit(_state.value.selectedId) }
            is PlanEditorEvent.Transform -> setView(event.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM), event.panX, event.panY)
            is PlanEditorEvent.Fit -> fit(event.widthDp, event.heightDp)
            is PlanEditorEvent.SetOverlayMode -> setOverlayMode(event.on)
            is PlanEditorEvent.OverlayEvent -> onOverlayEvent(event.event)
        }
    }

    private fun setOverlayMode(on: Boolean) {
        if (overlays == null) return
        if (on) select(null)
        saved[OVERLAY_MODE] = on
        if (!on) {
            setDrawing(null)
            selectOverlay(null)
        }
        _state.update { it.copy(overlayMode = on) }
    }

    private fun onOverlayEvent(event: WallOverlaysEvent) {
        val repo = overlays ?: return
        when (event) {
            is WallOverlaysEvent.Select -> selectOverlay(event.id)
            WallOverlaysEvent.StartDrawing -> if (editor.rooms.isNotEmpty()) {
                selectOverlay(null)
                setDrawing(emptyList())
            }
            is WallOverlaysEvent.AddPoint -> _state.value.drawing?.let { setDrawing(it + event.point.toWholeInches()) }
            WallOverlaysEvent.UndoPoint -> _state.value.drawing?.let { setDrawing(it.dropLast(1)) }
            WallOverlaysEvent.CancelDrawing -> setDrawing(null)
            WallOverlaysEvent.FinishDrawing -> {
                val points = _state.value.drawing?.takeIf { it.isNotEmpty() } ?: return
                setDrawing(null)
                forms.open(OverlayForm(points = points, stage = overlayStage))
            }
            is WallOverlaysEvent.MovePoint -> {
                val to = event.to.toWholeInches()
                homeOverlays = homeOverlays.map { o ->
                    if (o.id != event.id || event.index !in o.points.indices) o else o.copy(points = o.points.toMutableList().also { it[event.index] = to })
                }
                refresh()
                launch { repo.movePoint(event.id, event.index, to) }
            }
            WallOverlaysEvent.EditSelected -> {
                val o = _state.value.selectedOverlay ?: return
                forms.open(OverlayForm(
                    editingId = o.id, points = o.points, kind = o.kind, label = o.label, note = o.note.orEmpty(), depth = o.depth, stage = o.stage,
                    photoPath = o.photoPath, itemId = o.itemId,
                ))
            }
            WallOverlaysEvent.AskDelete -> if (_state.value.selectedOverlay != null) _state.update { it.copy(confirmDeleteOverlay = true) }
            WallOverlaysEvent.CancelDelete -> _state.update { it.copy(confirmDeleteOverlay = false) }
            WallOverlaysEvent.ConfirmDelete -> {
                val id = _state.value.overlaySelectedId
                _state.update { it.copy(confirmDeleteOverlay = false) }
                selectOverlay(null)
                if (id != null) launch { repo.delete(id) }
            }
            WallOverlaysEvent.Save -> saveOverlay(repo)
            WallOverlaysEvent.DismissError -> _state.update { it.copy(overlaySaveFailed = false) }
            WallOverlaysEvent.PhotoLaunchHandled -> _state.update { it.copy(photoLaunch = null) }
            WallOverlaysEvent.Back, is WallOverlaysEvent.SelectStage -> Unit
            else -> forms.onEvent(event)
        }
    }

    private fun saveOverlay(repo: OverlayRepository) {
        val (editing, draft) = forms.draftForSave() ?: return
        launch {
            val result = if (editing != null) {
                repo.update(editing, draft)
            } else {
                val room = PlanOverlayPlacer.roomFor(editor.rooms, draft.points)
                if (room == null) null else repo.addOnPlan(homeId, room, draft)
            }
            if (result is OverlaySave.Saved) {
                forms.open(null)
                selectOverlay(result.id)
                _state.update { it.copy(overlaySaveFailed = false) }
            } else {
                forms.saveFailed()
                _state.update { it.copy(overlaySaveFailed = true) }
            }
        }
    }

    private fun selectOverlay(id: String?) {
        saved[OVERLAY_SELECTED] = id
        _state.update { it.copy(overlaySelectedId = id) }
    }

    private fun setDrawing(points: List<OverlayPoint>?) {
        saved[DRAWING] = points?.let(OverlayPoints::encode)
        _state.update { it.copy(drawing = points) }
    }

    /** Plan overlays keep whole inches, like rooms. */
    private fun OverlayPoint.toWholeInches() = OverlayPoint(x.roundToInt().toFloat(), y.roundToInt().toFloat())

    private fun floorById(id: String): FloorSummary? = _state.value.floors.firstOrNull { it.id == id }

    private fun selectedId(): String? = _state.value.selectedId?.takeIf { id -> editor.rooms.any { it.id == id } }

    private suspend fun loadFloor(floor: FloorSummary) {
        val plan = repo.loadPlan(floor.id)
        editor = PlanEditor(plan.placed, snapper)
        saved[FLOOR] = floor.id
        val keep = _state.value.selectedId?.takeIf { id -> plan.placed.any { it.id == id } }
        val shape = pendingEditShape && keep != null
        pendingEditShape = false
        saved[SELECTED] = keep
        saved[EDIT_SHAPE] = shape
        // A name or size typed before the process died comes back as still pending on its room.
        val pendingName: String? = saved[PENDING_NAME]
        if (saved.get<String>(PENDING_ROOM) != keep) clearPending()
        else if (keep != null && pendingName != null) editor.previewRename(keep, pendingName)
        val rooms = editor.rooms
        // One update with the rooms: the editor counts as loaded only once its rooms are in the state, so a
        // later refresh never lands an older room list over the first edit.
        _state.update {
            it.copy(
                loading = false,
                floorId = floor.id,
                unplaced = plan.unplaced,
                selectedId = keep,
                editShape = shape,
                focusName = false,
                selectedWall = null,
                selectedOpeningId = null,
                fieldsVersion = it.fieldsVersion + 1,
            ).withRooms(rooms)
        }
    }

    private fun hold(roomId: String, write: () -> Unit) {
        if (saved.get<String>(PENDING_ROOM) != roomId) {
            commitFields()
            saved[PENDING_ROOM] = roomId
        }
        write()
    }

    /** Turns the pending name and size into undo steps and saves once. Nothing pending, nothing happens. */
    private fun commitFields() {
        val roomId: String = saved[PENDING_ROOM] ?: return
        val width: Int? = saved[PENDING_WIDTH]
        val length: Int? = saved[PENDING_LENGTH]
        clearPending()
        editor.commit()
        if (width != null && length != null) editor.setSize(roomId, width, length)
        refresh()
        save()
    }

    private fun clearPending() {
        saved.remove<String>(PENDING_ROOM)
        saved.remove<String>(PENDING_NAME)
        saved.remove<Int>(PENDING_WIDTH)
        saved.remove<Int>(PENDING_LENGTH)
    }

    private fun select(id: String?) {
        saved[SELECTED] = id
        saved[EDIT_SHAPE] = false
        _state.update {
            it.copy(selectedId = id, editShape = false, focusName = false, selectedWall = null, selectedOpeningId = null, fieldsVersion = it.fieldsVersion + 1)
        }
    }

    private fun setEditShape(on: Boolean) {
        saved[EDIT_SHAPE] = on
        _state.update { it.copy(editShape = on) }
    }

    private fun keepSelectionIfPresent() {
        val id = _state.value.selectedId
        if (id != null && editor.rooms.none { it.id == id }) select(null)
    }

    /** Pushes the editor's rooms into the state and, once an edit is final, to the database. */
    private fun commit(selectId: String?, focusName: Boolean = false, bumpFields: Boolean = true) {
        saved[SELECTED] = selectId
        if (selectId == null) saved[EDIT_SHAPE] = false
        _state.update {
            it.copy(
                selectedId = selectId,
                editShape = it.editShape && selectId != null,
                focusName = focusName,
                fieldsVersion = if (bumpFields) it.fieldsVersion + 1 else it.fieldsVersion,
            )
        }
        refresh()
        save()
    }

    /** Saves the floor; a save that has started finishes even when the editor closes right after. */
    private fun save() {
        val floorId = _state.value.floorId ?: return
        val rooms = editor.rooms
        launch { withContext(NonCancellable) { saveLock.withLock { repo.savePlan(floorId, rooms) } } }
    }

    private fun refresh() {
        val rooms = editor.rooms
        _state.update { it.withRooms(rooms) }
    }

    private fun PlanEditorUiState.withRooms(rooms: List<PlanRoom>): PlanEditorUiState = copy(
        rooms = rooms,
        overlays = PlanOverlayPlacer.onFloor(homeOverlays, rooms),
        canUndo = editor.canUndo,
        canRedo = editor.canRedo,
        flaggedIds = validator.flaggedRoomIds(rooms),
        selectedOpeningId = selectedOpeningId?.takeIf { oid -> rooms.any { r -> r.openings.any { o -> o.id == oid } } },
        selectedWall = selectedWall?.takeIf { e -> rooms.firstOrNull { r -> r.id == selectedId }?.let { r -> e in r.points.indices } == true },
    )

    private fun setView(zoom: Float, panX: Float, panY: Float) {
        saved[ZOOM] = zoom
        saved[PAN_X] = panX
        saved[PAN_Y] = panY
        _state.update { it.copy(zoom = zoom, panX = panX, panY = panY) }
    }

    private fun fit(widthDp: Float, heightDp: Float) {
        val rooms = editor.rooms
        if (rooms.isEmpty() || widthDp <= 0f || heightDp <= 0f) return
        val minX = rooms.minOf { it.minX }
        val maxX = rooms.maxOf { it.maxX }
        val minY = rooms.minOf { it.minY }
        val maxY = rooms.maxOf { it.maxY }
        val w = max(maxX - minX, 1)
        val h = max(maxY - minY, 1)
        val zoom = min((widthDp - 2 * FIT_MARGIN_DP) / w, (heightDp - 2 * FIT_MARGIN_DP) / h).coerceIn(MIN_ZOOM, MAX_ZOOM)
        setView(zoom, (widthDp - w * zoom) / 2f - minX * zoom, (heightDp - h * zoom) / 2f - minY * zoom)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        private const val FLOOR = "floorId"
        private const val SELECTED = "selectedId"
        private const val EDIT_SHAPE = "editShape"
        private const val ZOOM = "zoom"
        private const val PAN_X = "panX"
        private const val PAN_Y = "panY"
        private const val PENDING_ROOM = "pendingRoom"
        private const val PENDING_NAME = "pendingName"
        private const val PENDING_WIDTH = "pendingWidth"
        private const val PENDING_LENGTH = "pendingLength"
        private const val OVERLAY_MODE = "overlayMode"
        private const val OVERLAY_SELECTED = "overlaySelected"
        private const val DRAWING = "drawing"
        const val MIN_ZOOM = 0.15f
        const val MAX_ZOOM = 6f
        private const val FIT_MARGIN_DP = 24f

        /** Keeps the point under [focusDp] fixed while the scale changes (pinch). */
        fun zoomAround(state: PlanEditorUiState, factor: Float, focusX: Float, focusY: Float): PlanEditorEvent.Transform {
            val z = (state.zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
            val k = z / state.zoom
            return PlanEditorEvent.Transform(z, focusX - (focusX - state.panX) * k, focusY - (focusY - state.panY) * k)
        }

        fun toInches(dp: Float, zoom: Float): Int = (dp / zoom).roundToInt()
    }
}
