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
) {
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

sealed interface ItemPlaceEvent {
    data class PickFloor(val floorId: String) : ItemPlaceEvent
    /** A tap on the plan at ([xIn], [yIn]) plan inches: the item moves there and is saved right away. */
    data class Place(val xIn: Int, val yIn: Int) : ItemPlaceEvent
    data object Remove : ItemPlaceEvent
    /** Done or the back arrow: the spot is already saved. */
    data object Done : ItemPlaceEvent
    /** The floor has no drawn rooms: open the plan editor on it. */
    data object EditPlan : ItemPlaceEvent
}
