package com.kurtz.homerecord.housemanual.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.plan.GridSnapper
import com.kurtz.homerecord.housemodel.plan.PlanEditor
import com.kurtz.homerecord.housemodel.plan.PlanValidator
import com.kurtz.homerecord.housemodel.plan.RoomKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * HM-Plan (AC-C5, AC-C6, AC-C10, AC-C12). Every committed edit is saved to the database right away, so leaving the
 * editor or losing the process never drops work. Floor, selection and view survive through [SavedStateHandle].
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
        ),
    )
    val state: StateFlow<PlanEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeFloors(homeId).collect { floors ->
                val current = _state.value.floorId
                val chosen = floors.firstOrNull { it.id == current }
                    ?: floors.firstOrNull { it.roomCount > 0 }
                    ?: floors.firstOrNull()
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
            is PlanEditorEvent.Rename -> selectedId()?.let { editor.rename(it, event.name); commit(it, bumpFields = false) }
            is PlanEditorEvent.SetKind -> selectedId()?.let { editor.setKind(it, event.kind); commit(it, bumpFields = false) }
            is PlanEditorEvent.SetSize -> selectedId()?.let { editor.setSize(it, event.widthIn, event.lengthIn); commit(it, bumpFields = false) }
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
        }
    }

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
            )
        }
        refresh()
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
        val floorId = _state.value.floorId ?: return
        val rooms = editor.rooms
        launch { saveLock.withLock { repo.savePlan(floorId, rooms) } }
    }

    private fun refresh() {
        val rooms = editor.rooms
        _state.update {
            it.copy(
                rooms = rooms,
                canUndo = editor.canUndo,
                canRedo = editor.canRedo,
                flaggedIds = validator.flaggedRoomIds(rooms),
                selectedOpeningId = it.selectedOpeningId?.takeIf { oid -> rooms.any { r -> r.openings.any { o -> o.id == oid } } },
                selectedWall = it.selectedWall?.takeIf { e -> rooms.firstOrNull { r -> r.id == it.selectedId }?.let { r -> e in r.points.indices } == true },
            )
        }
    }

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
