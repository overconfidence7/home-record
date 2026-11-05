package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.UnplacedRoom
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanOpening
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import com.kurtz.homerecord.housemodel.plan.RoomKind
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayItemChoice
import com.kurtz.homerecord.housemodel.overlays.OverlayItemPicker
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.overlays.PlanOverlayPlacer

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
    /**
     * False until the view model has a view for this canvas (fitted, restored, panned). Until then the screen draws the
     * plan fitted to the canvas, so the plan never shows up pinned to a corner for a frame (or in previews).
     */
    val viewSet: Boolean = false,
    /** Rooms that overlap another or are under 2 x 2 ft. A warning only. */
    val flaggedIds: Set<String> = emptySet(),
    /** Bumps when the size fields must reload from the rooms (selection, drag end, undo), not while typing. */
    val fieldsVersion: Int = 0,
    val system: UnitSystem = UnitSystem.IMPERIAL,
    /** Edit shape mode for the selected room (AC-C7): corners drag, tapping an edge adds one, tapping a corner deletes it. */
    val editShape: Boolean = false,
    /** Edge of the selected room the user tapped, where a door or window can be added (AC-C9). */
    val selectedWall: Int? = null,
    /** Where on that wall the tap landed, so a new door or window starts there. */
    val wallTap: Pt? = null,
    val selectedOpeningId: String? = null,
    /** The editor can draw overlays (the app gave it the overlays repository). */
    val canEditOverlays: Boolean = false,
    /** Overlays mode (AC-C25, AC-C28): taps draw and pick overlays instead of rooms; rooms stay put. */
    val overlayMode: Boolean = false,
    /** The floor's plan overlays, points in plan inches. Shown in both modes, picked only in Overlays mode. */
    val overlays: List<Overlay> = emptyList(),
    val overlaySelectedId: String? = null,
    /** The points of a new overlay being drawn, plan inches; null when not drawing. */
    val drawing: List<OverlayPoint>? = null,
    val overlayForm: OverlayForm? = null,
    val confirmDeleteOverlay: Boolean = false,
    val overlaySaveFailed: Boolean = false,
    /** The home's items an overlay can serve (AC-C26). */
    val items: List<OverlayItemChoice> = emptyList(),
    /** The overlay form offers a photo (the app gave the editor a place to keep one). */
    val canAddPhoto: Boolean = false,
    /** A camera or picker the route should open for the overlay form, then answer with PhotoLaunchHandled. */
    val photoLaunch: OverlayPhotoLaunch? = null,
) {
    val selected: PlanRoom? get() = rooms.firstOrNull { it.id == selectedId }
    val selectedOpening: PlanOpening? get() = selected?.openings?.firstOrNull { it.id == selectedOpeningId }
    val selectedOverlay: Overlay? get() = overlays.firstOrNull { it.id == overlaySelectedId }

    /** The item [itemId] names, when it is still in the house. */
    fun item(itemId: String?): OverlayItemChoice? = itemId?.let { id -> items.firstOrNull { it.id == id } }

    /** The linked-item sheet's rows, narrowed by the typed query; the room the overlay starts in leads. */
    val itemRows: List<OverlayItemChoice>
        get() = OverlayItemPicker.filter(items, overlayForm?.itemQuery.orEmpty(), overlayForm?.points?.let { PlanOverlayPlacer.roomFor(rooms, it) })
}

sealed interface PlanEditorEvent {
    data class SelectFloor(val floorId: String) : PlanEditorEvent
    data class AddRoom(val center: Pt) : PlanEditorEvent
    data class PlaceRoom(val roomId: String, val center: Pt) : PlanEditorEvent
    data class Select(val roomId: String?) : PlanEditorEvent
    data class DragMove(val roomId: String, val dxIn: Int, val dyIn: Int) : PlanEditorEvent
    data class DragEdge(val roomId: String, val edgeIndex: Int, val deltaIn: Int) : PlanEditorEvent
    data object DragEnd : PlanEditorEvent
    /** Typed name, shown on the plan at once and committed with [CommitFields] or the next other edit. */
    data class Rename(val name: String) : PlanEditorEvent
    data class SetKind(val kind: RoomKind) : PlanEditorEvent
    /** Typed width and length; held until [CommitFields] so a half-typed number never resizes the room. */
    data class SetSize(val widthIn: Int, val lengthIn: Int) : PlanEditorEvent
    /** IME Done or focus left a field: the typed name and size become one undo step each and are saved. */
    data object CommitFields : PlanEditorEvent
    data class SetEditShape(val on: Boolean) : PlanEditorEvent
    data class AddCorner(val edgeIndex: Int, val at: Pt) : PlanEditorEvent
    data class DragCorner(val index: Int, val dxIn: Int, val dyIn: Int) : PlanEditorEvent
    data class DeleteCorner(val index: Int) : PlanEditorEvent
    data class SelectWall(val edgeIndex: Int, val at: Pt) : PlanEditorEvent
    data class AddOpening(val kind: OpeningKind) : PlanEditorEvent
    data class SelectOpening(val openingId: String?) : PlanEditorEvent
    /** Slides the opening so its centre follows [at], a point on the plan in inches. */
    data class DragOpening(val openingId: String, val at: Pt) : PlanEditorEvent
    data class SetOpeningWidth(val widthIn: Int) : PlanEditorEvent
    data object DeleteOpening : PlanEditorEvent
    data object AddStair : PlanEditorEvent
    data class LinkStair(val stairId: String, val toFloorId: String?) : PlanEditorEvent
    data class DeleteStair(val stairId: String) : PlanEditorEvent
    data object Delete : PlanEditorEvent
    data object Undo : PlanEditorEvent
    data object Redo : PlanEditorEvent
    data class Transform(val zoom: Float, val panX: Float, val panY: Float) : PlanEditorEvent
    /** Fits all rooms into a viewport of the given size in dp. */
    data class Fit(val widthDp: Float, val heightDp: Float) : PlanEditorEvent
    /** Pans (zooming out only if it must) so the selected room shows whole in a viewport of the given size in dp. */
    data class Reveal(val widthDp: Float, val heightDp: Float) : PlanEditorEvent
    /** Switches between editing rooms and drawing overlays (AC-C25). */
    data class SetOverlayMode(val on: Boolean) : PlanEditorEvent
    /**
     * An overlay event in Overlays mode, the same set the wall editor uses: points are plan inches here (AC-C25, AC-C28).
     * Stage and Back events do not apply to the plan and are ignored.
     */
    data class OverlayEvent(val event: WallOverlaysEvent) : PlanEditorEvent
}
