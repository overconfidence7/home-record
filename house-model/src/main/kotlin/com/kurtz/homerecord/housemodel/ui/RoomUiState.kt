package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.plan.PlanRoom

data class RoomUiState(
    val loading: Boolean = true,
    val room: PlanRoom? = null,
    val system: UnitSystem = UnitSystem.IMPERIAL,
    val floorName: String? = null,
    val homeName: String? = null,
    val items: List<RoomItem> = emptyList(),
    /** House Manual keeps items and offers "Add an item here"; Field Log does not. */
    val canAddItem: Boolean = false,
)

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
