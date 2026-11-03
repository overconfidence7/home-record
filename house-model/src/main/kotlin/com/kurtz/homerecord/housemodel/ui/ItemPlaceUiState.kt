package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import com.kurtz.homerecord.housemodel.blueprint.LayerSet
import com.kurtz.homerecord.housemodel.blueprint.PlanMark
import com.kurtz.homerecord.housemodel.items.ItemPlace
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.scene.MarkerTarget
import com.kurtz.homerecord.housemodel.walls.RoomWall

/** Place on plan for one item (AC-C30 position): one floor's plan at a time with the item's marker where it sits. */
data class ItemPlaceUiState(
    val loading: Boolean = true,
    /** The item is gone (deleted elsewhere). */
    val missing: Boolean = false,
    val itemId: String = "",
    val itemName: String = "",
    /** The item's category icon name, drawn on its marker (rule 26). */
    val iconName: String = "Category",
    val floors: List<FloorSummary> = emptyList(),
    val floorId: String? = null,
    /** The shown floor's drawn rooms. */
    val rooms: List<PlanRoom> = emptyList(),
    /** Where the item sits, on any floor; null while it is not on the plan. */
    val spot: ItemPlace? = null,
    val system: UnitSystem = UnitSystem.IMPERIAL,
    /** The walls of the item's room when that room is on the shown floor, for "On a wall". */
    val walls: List<RoomWall> = emptyList(),
    /** On a wall: the screen shows one wall of the item's room instead of the plan. */
    val wallMode: Boolean = false,
    /** The wall shown in wall mode. */
    val wallId: String? = null,
    /** The wall the item hangs on and its spot there in wall space (x 0..1 left to right, y 0 ceiling to 1 floor). */
    val hungOn: WallSpot? = null,
) {
    /** The wall shown in wall mode, if the room still has it. */
    val wall: RoomWall? get() = walls.firstOrNull { it.id == wallId } ?: walls.firstOrNull()

    /** The item's spot on the shown wall, when it hangs there. */
    val wallSpotHere: WallSpot? get() = hungOn?.takeIf { it.wallId == wall?.id }

    /** "On a wall" is offered once the item is in a room on this floor that has walls. */
    val canHang: Boolean get() = walls.isNotEmpty()

    val floorName: String? get() = floors.firstOrNull { it.id == floorId }?.name

    /** The spot when it is on the shown floor. */
    val spotHere: ItemPlace? get() = spot?.takeIf { s -> rooms.any { it.id == s.roomId } }

    /** The name of the room the item sits in, when that room is on the shown floor. */
    val spotRoomName: String? get() = spotHere?.let { s -> rooms.firstOrNull { it.id == s.roomId }?.name }

    /** The item's marker, drawn on the Items layer. */
    val marks: List<PlanMark>
        get() = listOfNotNull(
            spotHere?.let {
                PlanMark(
                    MarkerTarget.ItemMark(itemId), it.roomId, BlueprintLayer.ITEMS, null,
                    listOf(OverlayPoint(it.xIn.toFloat(), it.yIn.toFloat())), iconName = iconName, label = itemName,
                )
            },
        )

    val layers: LayerSet get() = LayerSet(setOf(BlueprintLayer.ITEMS))
}

/** An item's spot on a wall, in wall space. */
data class WallSpot(val wallId: String, val x: Float, val y: Float)

sealed interface ItemPlaceEvent {
    data class PickFloor(val floorId: String) : ItemPlaceEvent
    /** A tap on the plan at ([xIn], [yIn]) plan inches: the item moves there and is saved right away. */
    data class Place(val xIn: Int, val yIn: Int) : ItemPlaceEvent
    data object Remove : ItemPlaceEvent
    /** Show the walls of the item's room ([on] true) or the plan again. */
    data class WallMode(val on: Boolean) : ItemPlaceEvent
    data class PickWall(val wallId: String) : ItemPlaceEvent
    /** A tap on the shown wall at wall-space ([x], [y]): the item hangs there, saved right away. */
    data class HangOnWall(val wallId: String, val x: Float, val y: Float) : ItemPlaceEvent
    /** Done or the back arrow: the spot is already saved. */
    data object Done : ItemPlaceEvent
    /** The floor has no drawn rooms: open the plan editor on it. */
    data object EditPlan : ItemPlaceEvent
}
