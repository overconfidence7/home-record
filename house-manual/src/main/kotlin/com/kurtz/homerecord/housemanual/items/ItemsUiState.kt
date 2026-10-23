package com.kurtz.homerecord.housemanual.items

import com.kurtz.homerecord.housemodel.items.ItemFilter
import com.kurtz.homerecord.housemodel.items.ItemGroup
import com.kurtz.homerecord.housemodel.items.ItemGrouping
import com.kurtz.homerecord.housemodel.items.WarrantyFilter

data class RoomOption(val id: String, val name: String)

data class ItemsUiState(
    val loading: Boolean = true,
    val totalCount: Int = 0,
    val grouping: ItemGrouping = ItemGrouping.CATEGORY,
    val filter: ItemFilter = ItemFilter(),
    val groups: List<ItemGroup> = emptyList(),
    val categoryOptions: List<String> = emptyList(),
    val roomOptions: List<RoomOption> = emptyList(),
) {
    val filtered: Boolean get() = filter != ItemFilter()
}

sealed interface ItemsEvent {
    data class SetGrouping(val grouping: ItemGrouping) : ItemsEvent
    data class SetQuery(val query: String) : ItemsEvent
    data class SetCategory(val label: String?) : ItemsEvent
    data class SetRoom(val roomId: String?) : ItemsEvent
    data class SetWarranty(val filter: WarrantyFilter?) : ItemsEvent
    data object ClearFilters : ItemsEvent
    data class Open(val itemId: String) : ItemsEvent
    data object Add : ItemsEvent
}
