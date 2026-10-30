package com.kurtz.homerecord.housemodel.items

import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus

/** One row of the Items list. [roomName] is already resolved (pseudo-rooms included). */
data class ItemRow(
    val id: String,
    val name: String,
    val make: String?,
    val model: String?,
    val category: ItemCategory,
    val subtypeId: String,
    val roomId: String?,
    val roomName: String,
    val warranty: WarrantyStatus,
    val createdAt: Long = 0,
)

enum class WarrantyFilter { ACTIVE, EXPIRING, EXPIRED, UNKNOWN;
    fun matches(s: WarrantyStatus) = when (this) {
        ACTIVE -> s is WarrantyStatus.Active
        EXPIRING -> s is WarrantyStatus.Expiring
        EXPIRED -> s is WarrantyStatus.Expired
        UNKNOWN -> s is WarrantyStatus.Unknown
    }
}

enum class ItemGrouping { CATEGORY, ROOM }

data class ItemFilter(
    val category: ItemCategory? = null,
    val roomId: String? = null,
    val warranty: WarrantyFilter? = null,
    val query: String = "",
)

/** A category group has [category] set and an empty [title] (the screen names it from resources); a room group has its room name. */
data class ItemGroup(val key: String, val title: String, val count: Int, val items: List<ItemRow>, val category: ItemCategory? = null)

/** Groups, filters and orders the Items list (AC-H6). Pure; no Android types. A search also matches [categoryName]. */
object ItemListBuilder {
    fun build(
        rows: List<ItemRow>,
        grouping: ItemGrouping,
        filter: ItemFilter = ItemFilter(),
        categoryName: (ItemCategory) -> String,
    ): List<ItemGroup> {
        val tokens = filter.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val kept = rows.filter { r ->
            (filter.category == null || r.category == filter.category) &&
                (filter.roomId == null || r.roomId == filter.roomId) &&
                (filter.warranty == null || filter.warranty.matches(r.warranty)) &&
                tokens.all { t -> listOfNotNull(r.name, r.make, r.model, r.roomName, categoryName(r.category)).any { it.lowercase().contains(t) } }
        }
        val byName = compareBy<ItemRow>({ it.name.lowercase() }, { it.id })
        return when (grouping) {
            ItemGrouping.CATEGORY -> kept.groupBy { it.category }.entries
                .sortedBy { it.key.ordinal }
                .map { (c, v) -> ItemGroup(c.name, "", v.size, v.sortedWith(byName), c) }
            ItemGrouping.ROOM -> kept.groupBy { it.roomId.orEmpty() to it.roomName }.entries
                .sortedBy { it.key.second.lowercase() }
                .map { (k, v) -> ItemGroup(k.first, k.second, v.size, v.sortedWith(byName)) }
        }
    }
}
