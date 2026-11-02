package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.walls.PhotoPoint
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallOutline
import com.kurtz.homerecord.housemodel.walls.WallStage

/** HM-WallOutline (AC-C18): the new wall photo with its four corner handles, the wall's shape as a guide, and Use. */
data class WallOutlineUiState(
    val loading: Boolean = true,
    val wall: RoomWall? = null,
    val stage: WallStage = WallStage.HouseManualDefault,
    val system: UnitSystem = UnitSystem.IMPERIAL,
    val photoPath: String = "",
    /** The photo's upright size in pixels; 0 until it has been read. */
    val photoWidth: Int = 0,
    val photoHeight: Int = 0,
    val outline: WallOutline = WallOutline.Initial,
    /** The corner being dragged (0..3, top-left clockwise), which the loupe magnifies; null when no finger is down. */
    val dragging: Int? = null,
    val saving: Boolean = false,
    val error: WallOutlineError? = null,
    /** True once the wall photo is saved; the route goes back to the room. */
    val done: Boolean = false,
)

enum class WallOutlineError {
    /** The file is missing or not an image: the screen offers only going back. */
    PhotoUnreadable,

    /** The corners cross or enclose almost nothing. */
    NotUsable,

    /** The straightened copy could not be written (out of space, out of memory). */
    TextureFailed,

    /** The wall was removed from the plan while the camera was open. */
    WallGone,
}

sealed interface WallOutlineEvent {
    data object Back : WallOutlineEvent
    data class DragStart(val corner: Int) : WallOutlineEvent
    data class Drag(val corner: Int, val to: PhotoPoint) : WallOutlineEvent
    data object DragEnd : WallOutlineEvent
    data object Reset : WallOutlineEvent
    data object Use : WallOutlineEvent
}
