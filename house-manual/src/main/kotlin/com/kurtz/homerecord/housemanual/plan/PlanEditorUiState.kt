package com.kurtz.homerecord.housemanual.plan

import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.UnplacedRoom
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import com.kurtz.homerecord.housemodel.plan.RoomKind

data class PlanEditorUiState(
    val loading: Boolean = true,
    /** Bottom floor first, as stored. */
    val floors: List<FloorSummary> = emptyList(),
    val floorId: String? = null,
    val rooms: List<PlanRoom> = emptyList(),
    val unplaced: List<UnplacedRoom> = emptyList(),
    val selectedId: String? = null,
    /** True right after Add room so the name field takes focus (AC-C6). */
    val focusName: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Screen dp per inch. */
    val zoom: Float = 1f,
    /** Screen dp offset of the plan origin. */
    val panX: Float = 0f,
    val panY: Float = 0f,
    /** Rooms that overlap another or are under 2 x 2 ft. A warning only. */
    val flaggedIds: Set<String> = emptySet(),
    /** Bumps when the size fields must reload from the rooms (selection, drag end, undo), not while typing. */
    val fieldsVersion: Int = 0,
    val system: UnitSystem = UnitSystem.IMPERIAL,
    /** Edit shape mode for the selected room (AC-C7): corners drag, tapping an edge adds one, tapping a corner deletes it. */
    val editShape: Boolean = false,
) {
    val selected: PlanRoom? get() = rooms.firstOrNull { it.id == selectedId }
}

sealed interface PlanEditorEvent {
    data class SelectFloor(val floorId: String) : PlanEditorEvent
    data class AddRoom(val center: Pt) : PlanEditorEvent
    data class PlaceRoom(val roomId: String, val center: Pt) : PlanEditorEvent
    data class Select(val roomId: String?) : PlanEditorEvent
    data class DragMove(val roomId: String, val dxIn: Int, val dyIn: Int) : PlanEditorEvent
    data class DragEdge(val roomId: String, val edgeIndex: Int, val deltaIn: Int) : PlanEditorEvent
    data object DragEnd : PlanEditorEvent
    data class Rename(val name: String) : PlanEditorEvent
    data class SetKind(val kind: RoomKind) : PlanEditorEvent
    data class SetSize(val widthIn: Int, val lengthIn: Int) : PlanEditorEvent
    data class SetEditShape(val on: Boolean) : PlanEditorEvent
    data class AddCorner(val edgeIndex: Int, val at: Pt) : PlanEditorEvent
    data class DragCorner(val index: Int, val dxIn: Int, val dyIn: Int) : PlanEditorEvent
    data class DeleteCorner(val index: Int) : PlanEditorEvent
    data object Delete : PlanEditorEvent
    data object Undo : PlanEditorEvent
    data object Redo : PlanEditorEvent
    data class Transform(val zoom: Float, val panX: Float, val panY: Float) : PlanEditorEvent
    /** Fits all rooms into a viewport of the given size in dp. */
    data class Fit(val widthDp: Float, val heightDp: Float) : PlanEditorEvent
}
