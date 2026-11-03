package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.walls.BehindTheWall
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallStage

/**
 * FL-BehindWall (AC-F49): pick a room, then a wall, and the camera opens. [roomId] null shows the rooms; otherwise the
 * walls of that room, each with the stage its next photo is saved at.
 */
data class BehindWallUiState(
    val loading: Boolean = true,
    val title: String = "",
    val rooms: List<BehindWallRoom> = emptyList(),
    val roomId: String? = null,
    val system: UnitSystem = UnitSystem.IMPERIAL,
    /** The wall whose "Take photo / Choose from gallery" sheet is open. */
    val photoSheetFor: String? = null,
    /** A camera or gallery request for the route to launch once. */
    val photoLaunch: WallPhotoLaunch? = null,
    /** A new wall photo ready to outline; the route opens the outline screen once. */
    val outlineRequest: WallOutlineRequest? = null,
    /** The picked photo could not be copied into the app. */
    val photoImportFailed: Boolean = false,
) {
    val room: BehindWallRoom? get() = roomId?.let { id -> rooms.firstOrNull { it.id == id } }

    /** The room was removed from the plan while it was open (on another phone). */
    val roomMissing: Boolean get() = !loading && roomId != null && room == null
}

/** One room of the job's house with its walls and how many are photographed at Rough-in and Finished. */
data class BehindWallRoom(
    val id: String,
    val name: String,
    val floorName: String,
    val walls: List<RoomWall> = emptyList(),
) {
    val progress: BehindTheWall.Progress get() = BehindTheWall.progress(walls)
}

/** The stage the next photo of this wall is saved at (AC-F49). */
val RoomWall.nextBehindWallStage: WallStage get() = BehindTheWall.captureStage(thumbnails.keys)

sealed interface BehindWallEvent {
    data object Back : BehindWallEvent
    data class OpenRoom(val roomId: String) : BehindWallEvent
    data object EditPlan : BehindWallEvent
    /** A wall row: opens the Take photo / Choose from gallery sheet for that wall. */
    data class PickWall(val wallId: String) : BehindWallEvent
    data object CloseSheet : BehindWallEvent
    data object TakePhoto : BehindWallEvent
    data object ChoosePhoto : BehindWallEvent
    data class CameraResult(val ok: Boolean) : BehindWallEvent
    data class GalleryResult(val uri: String?) : BehindWallEvent
    data object PhotoLaunchHandled : BehindWallEvent
    data object OutlineOpened : BehindWallEvent
    /** Opens the room page for overlays and the stage picker. */
    data object OpenRoomPage : BehindWallEvent
}
