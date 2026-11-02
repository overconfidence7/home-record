package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.core.media.CaptureTarget
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallStage

data class RoomUiState(
    val loading: Boolean = true,
    val room: PlanRoom? = null,
    val system: UnitSystem = UnitSystem.IMPERIAL,
    val floorName: String? = null,
    val homeName: String? = null,
    val items: List<RoomItem> = emptyList(),
    /** House Manual keeps items and offers "Add an item here"; Field Log does not. */
    val canAddItem: Boolean = false,
    /** The room's walls by side and length with a picture per photographed stage (AC-C17). */
    val walls: List<RoomWall> = emptyList(),
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
)

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
)

sealed interface RoomEvent {
    data object Back : RoomEvent
    data class OpenItem(val id: String) : RoomEvent
    data object AddItem : RoomEvent
    data class SelectStage(val stage: WallStage) : RoomEvent
    data class PhotographWall(val wallId: String) : RoomEvent
    data object ClosePhotoSheet : RoomEvent
    data object TakeWallPhoto : RoomEvent
    data object ChooseWallPhoto : RoomEvent
    data class CameraResult(val ok: Boolean) : RoomEvent
    data class GalleryResult(val uri: String?) : RoomEvent
    data object PhotoLaunchHandled : RoomEvent
    data object OutlineOpened : RoomEvent
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
        )
    }
