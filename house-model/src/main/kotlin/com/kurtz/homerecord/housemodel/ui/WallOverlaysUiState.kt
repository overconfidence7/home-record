package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.core.media.CaptureTarget
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayDepth
import com.kurtz.homerecord.housemodel.overlays.OverlayItemChoice
import com.kurtz.homerecord.housemodel.overlays.OverlayItemPicker
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallOutline
import com.kurtz.homerecord.housemodel.walls.WallStage

/**
 * The picture of the wall the overlays are drawn on: the straightened copy (already in wall space, [outline] null) or,
 * when there is none, the photo as taken with its corner [outline] (AC-C27); [aspect] is the picture's width over height.
 * No picture at all shows the plain wall at the wall's own aspect.
 */
data class WallPicture(val path: String, val outline: WallOutline?, val aspect: Float)

/** The overlay form (AC-C26); [editingId] is null for a new overlay drawn with [points]. */
data class OverlayForm(
    val editingId: String? = null,
    val points: List<OverlayPoint> = emptyList(),
    val kind: OverlayKind? = null,
    val label: String = "",
    val note: String = "",
    val depth: OverlayDepth = OverlayDepth.BEHIND_DRYWALL,
    val stage: WallStage = WallStage.HouseManualDefault,
    /** The overlay's photo in app storage, or none. */
    val photoPath: String? = null,
    /** [photoPath] was taken or picked in this form and is not saved yet, so Close or a replacement deletes the file. */
    val photoIsNew: Boolean = false,
    /** The item this overlay serves, or none. */
    val itemId: String? = null,
    /** What is typed in the linked-item sheet's search field. */
    val itemQuery: String = "",
    /** Which picker sheet is open over the form. */
    val sheet: OverlayFormSheet? = null,
    /** Save was pressed with no kind chosen, so the kind field shows its error. */
    val triedSave: Boolean = false,
    val saving: Boolean = false,
    /** Fields changed since the form opened; Close asks before throwing them away (rule 3). */
    val dirty: Boolean = false,
) {
    val canSave: Boolean get() = kind != null && !saving
}

enum class OverlayFormSheet { KIND, DEPTH, PHOTO, ITEM }

/** One request for the route to open the camera on [target], or the photo picker when [target] is null. */
data class OverlayPhotoLaunch(val target: CaptureTarget?)

enum class WallOverlaysError { WallGone, SaveFailed }

/**
 * HM-Wall: one wall at one stage with every overlay on it (AC-C25..C28). Overlays are kept in wall space, so all of them
 * show on every stage's picture (AC-C27); the stage only picks the picture and is what a new overlay is recorded at.
 */
data class WallOverlaysUiState(
    val loading: Boolean = true,
    val wall: RoomWall? = null,
    /** The wall's room; its items lead the linked-item list. */
    val roomId: String? = null,
    val roomName: String? = null,
    val system: UnitSystem = UnitSystem.IMPERIAL,
    val stage: WallStage = WallStage.HouseManualDefault,
    /** The chosen stage's picture; null shows the plain wall. */
    val picture: WallPicture? = null,
    val overlays: List<Overlay> = emptyList(),
    val selectedId: String? = null,
    /** The points of a new overlay being drawn; null when not drawing. */
    val drawing: List<OverlayPoint>? = null,
    val form: OverlayForm? = null,
    val confirmDelete: Boolean = false,
    val error: WallOverlaysError? = null,
    /** The home's items an overlay can be linked to (AC-C26). */
    val items: List<OverlayItemChoice> = emptyList(),
    /** Whether the form offers a photo (the app gave the screen a place to keep one). */
    val canAddPhoto: Boolean = false,
    /** False for a viewer of a shared home (AC-H16): overlays show and can be picked, but nothing is drawn, moved, edited or deleted. */
    val canEdit: Boolean = true,
    /** A camera or picker the route should open, then answer with PhotoLaunchHandled. */
    val photoLaunch: OverlayPhotoLaunch? = null,
) {
    val selected: Overlay? get() = overlays.firstOrNull { it.id == selectedId }

    /** The item [itemId] names, when it is still in the house. */
    fun item(itemId: String?): OverlayItemChoice? = itemId?.let { id -> items.firstOrNull { it.id == id } }

    /** The linked-item sheet's rows: the wall's room first, narrowed by the typed query. */
    val itemRows: List<OverlayItemChoice> get() = OverlayItemPicker.filter(items, form?.itemQuery.orEmpty(), roomId)

    /** Width over height of what the overlays are drawn on: the picture, else the wall (8 ft high when unknown). */
    val aspect: Float
        get() = picture?.aspect?.takeIf { it > 0f }
            ?: wall?.takeIf { it.lengthIn > 0 }?.let { it.lengthIn.toFloat() / (it.heightIn.takeIf { h -> h > 0 } ?: 96) }
            ?: (4f / 3f)
}

sealed interface WallOverlaysEvent {
    data object Back : WallOverlaysEvent
    data class SelectStage(val stage: WallStage) : WallOverlaysEvent
    /** A tap on the wall in wall space, or on nothing (null) to clear the selection. */
    data class Select(val id: String?) : WallOverlaysEvent
    data object StartDrawing : WallOverlaysEvent
    data class AddPoint(val point: OverlayPoint) : WallOverlaysEvent
    data object UndoPoint : WallOverlaysEvent
    data object CancelDrawing : WallOverlaysEvent
    data object FinishDrawing : WallOverlaysEvent
    /** Point [index] of overlay [id] dragged to [to] (wall space), saved when the finger lifts (AC-C28). */
    data class MovePoint(val id: String, val index: Int, val to: OverlayPoint) : WallOverlaysEvent
    data object EditSelected : WallOverlaysEvent
    data object AskDelete : WallOverlaysEvent
    data object CancelDelete : WallOverlaysEvent
    data object ConfirmDelete : WallOverlaysEvent
    data object DismissError : WallOverlaysEvent

    data class OpenSheet(val sheet: OverlayFormSheet?) : WallOverlaysEvent
    data class SetKind(val kind: OverlayKind) : WallOverlaysEvent
    data class SetLabel(val label: String) : WallOverlaysEvent
    data class SetNote(val note: String) : WallOverlaysEvent
    data class SetDepth(val depth: OverlayDepth) : WallOverlaysEvent
    data class SetFormStage(val stage: WallStage) : WallOverlaysEvent
    data object TakePhoto : WallOverlaysEvent
    data object ChoosePhoto : WallOverlaysEvent
    data object PhotoLaunchHandled : WallOverlaysEvent
    /** The camera came back; [ok] false when it was cancelled. */
    data class CameraResult(val ok: Boolean) : WallOverlaysEvent
    /** The picker came back with [uri], or null when nothing was picked. */
    data class GalleryResult(val uri: String?) : WallOverlaysEvent
    data object RemovePhoto : WallOverlaysEvent
    data class SetItemQuery(val query: String) : WallOverlaysEvent
    /** Links the overlay to item [itemId], or to none. */
    data class SetItem(val itemId: String?) : WallOverlaysEvent
    data object Save : WallOverlaysEvent
    data object CloseForm : WallOverlaysEvent
}
