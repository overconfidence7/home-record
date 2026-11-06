package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.core.media.CaptureTarget
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.walls.RemovedWallPhoto
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallStage

data class RoomUiState(
    val loading: Boolean = true,
    val room: PlanRoom? = null,
    val system: UnitSystem = UnitSystem.IMPERIAL,
    val floorName: String? = null,
    /** The floor the room is on; with [canView3D], "View in 3D" opens that floor standing inside the room (AC-C22). */
    val floorId: String? = null,
    val homeId: String? = null,
    val canView3D: Boolean = false,
    val homeName: String? = null,
    val items: List<RoomItem> = emptyList(),
    /** "Add an item here": House Manual, and on a job for whoever may edit its house (T062). */
    val canAddItem: Boolean = false,
    /** The room's walls by side and length with a picture per photographed stage (AC-C17). */
    val walls: List<RoomWall> = emptyList(),
    /** Photos of walls a plan edit removed, kept on the room (N001); listed under the walls, newest first. */
    val removedWallPhotos: List<RemovedWallPhoto> = emptyList(),
    /** The stage whose photos the Walls section shows; House Manual starts on Finished, Field Log on Rough-in. */
    val wallStage: WallStage = WallStage.HouseManualDefault,
    /** True where the app can take a wall photo ("Photograph" on each wall). */
    val canPhotograph: Boolean = false,
    /** The wall whose "Take photo / Choose from photos" sheet is open (AC-C18). */
    val photoSheetFor: String? = null,
    /** A camera or gallery request for the route to launch once. */
    val photoLaunch: WallPhotoLaunch? = null,
    /** A new wall photo ready to outline; the route opens HM-WallOutline once. */
    val outlineRequest: WallOutlineRequest? = null,
    /** The picked photo could not be copied into the app. */
    val photoImportFailed: Boolean = false,
    /** The room's overlays on its walls and on the plan (AC-C25); null where the app has no overlays to show. */
    val overlays: List<Overlay>? = null,
    /** A wall row opens HM-Wall to draw and edit overlays on it (AC-C28). */
    val canOpenWalls: Boolean = false,
    /** Breakers feeding the room with their link confidence (AC-C41); null where the app keeps no panels. */
    val breakers: List<com.kurtz.homerecord.housemodel.panel.FeedingBreaker>? = null,
    /** Shutoff items in this room, mains first (AC-C45 "Shutoffs here"); null where the app keeps no items. */
    val shutoffs: List<RoomItem>? = null,
) {
    /** "View in 3D" shows once the room is drawn on its floor and the app has a 3D view. */
    val showView3D: Boolean get() = canView3D && floorId != null && (room?.points?.size ?: 0) >= 3

    /** Where "View in 3D" goes, once the room's floor and home are known. */
    val view3DTarget: Room3DTarget?
        get() {
            val r = room ?: return null
            val floor = floorId ?: return null
            return Room3DTarget(homeId.orEmpty(), homeName.orEmpty(), floor, r.id)
        }
}

/** The 3D view of [homeId]'s floor [floorId], standing inside [roomId] (AC-C22). */
data class Room3DTarget(val homeId: String, val homeName: String, val floorId: String, val roomId: String)

/** Launch the camera writing to [target], or the photo picker when [target] is null. */
data class WallPhotoLaunch(val target: CaptureTarget?)

/** The photo at [photoPath] is to be outlined as [wallId]'s [stage] picture. */
data class WallOutlineRequest(val wallId: String, val stage: WallStage, val photoPath: String)

/** One row of the room's item list: icon by subtype, make and model (or category), warranty status (AC-H10). */
data class RoomItem(
    val id: String,
    val name: String,
    val detail: String,
    val iconName: String = "Category",
    val warranty: WarrantyStatus = WarrantyStatus.Unknown,
    val category: ItemCategory = ItemCategory.OTHER,
    val subtypeId: String = "",
)

sealed interface RoomEvent {
    data object Back : RoomEvent
    data class OpenItem(val id: String) : RoomEvent
    data object AddItem : RoomEvent
    /** A "Breakers feeding this room" row: open its panel at the slot (AC-H7). */
    data class OpenBreaker(val breaker: com.kurtz.homerecord.housemodel.panel.FeedingBreaker) : RoomEvent
    data object View3D : RoomEvent
    data class SelectStage(val stage: WallStage) : RoomEvent
    data class PhotographWall(val wallId: String) : RoomEvent
    data object ClosePhotoSheet : RoomEvent
    data object TakeWallPhoto : RoomEvent
    data object ChooseWallPhoto : RoomEvent
    data class CameraResult(val ok: Boolean) : RoomEvent
    data class GalleryResult(val uri: String?) : RoomEvent
    data object PhotoLaunchHandled : RoomEvent
    data object OutlineOpened : RoomEvent
    /** Opens HM-Wall for [wallId] at the shown stage, with [overlayId] picked when one was tapped (AC-C28). */
    data class OpenWall(val wallId: String, val overlayId: String? = null) : RoomEvent
    /** An overlay drawn on the floor plan: opens the plan editor with it picked (AC-C28). */
    data class OpenPlanOverlay(val overlayId: String) : RoomEvent
}

/** The room's items by name; detail is make and model (the screen shows [RoomItem.category] when neither is set, AC-H10). */
fun roomItemsFor(rows: List<ItemRow>, roomId: String): List<RoomItem> =
    rows.filter { it.roomId == roomId }.sortedBy { it.name.lowercase() }.map {
        RoomItem(
            id = it.id,
            name = it.name,
            detail = listOfNotNull(it.make, it.model).joinToString(" "),
            category = it.category,
            iconName = ItemCategory.subtype(it.subtypeId)?.iconName ?: "Category",
            warranty = it.warranty,
            subtypeId = it.subtypeId,
        )
    }

/** "Shutoffs here" (AC-C45): the room's shutoff items, mains first, then by name. */
fun roomShutoffsFor(items: List<RoomItem>): List<RoomItem> =
    items.filter { it.category == ItemCategory.SHUTOFFS }.sortedBy { !it.subtypeId.endsWith("_main") }
