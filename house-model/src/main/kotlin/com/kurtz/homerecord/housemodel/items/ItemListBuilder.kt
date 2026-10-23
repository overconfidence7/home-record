package com.kurtz.homerecord.housemodel.items

import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus

/** One row of the Items list. [roomName] is already resolved (pseudo-rooms included). */
data class ItemRow(
    val id: String,
    val name: String,
    val make: String?,
    val model: String?,
    val categoryLabel: String,
    val categoryOrder: Int,
    val subtypeId: String,
    val roomId: String?,
    val roomName: String,
    val warranty: WarrantyStatus,
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
    val categoryLabel: String? = null,
    val roomId: String? = null,
    val warranty: WarrantyFilter? = null,
    val query: String = "",
)

data class ItemGroup(val key: String, val title: String, val count: Int, val items: List<ItemRow>)

/** Groups, filters and orders the Items list (AC-H6). Pure; no Android types. */
object ItemListBuilder {
    fun categoryOrder(label: String) = ItemCategory.entries.indexOfFirst { it.label == label }.let { if (it < 0) Int.MAX_VALUE else it }

    fun build(rows: List<ItemRow>, grouping: ItemGrouping, filter: ItemFilter = ItemFilter()): List<ItemGroup> {
        val tokens = filter.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val kept = rows.filter { r ->
            (filter.categoryLabel == null || r.categoryLabel == filter.categoryLabel) &&
                (filter.roomId == null || r.roomId == filter.roomId) &&
                (filter.warranty == null || filter.warranty.matches(r.warranty)) &&
                tokens.all { t -> listOfNotNull(r.name, r.make, r.model, r.roomName, r.categoryLabel).any { it.lowercase().contains(t) } }
        }
        val byName = compareBy<ItemRow>({ it.name.lowercase() }, { it.id })
        return when (grouping) {
            ItemGrouping.CATEGORY -> kept.groupBy { it.categoryLabel }.entries
                .sortedWith(compareBy({ categoryOrder(it.key) }, { it.key }))
                .map { (k, v) -> ItemGroup(k, k, v.size, v.sortedWith(byName)) }
            ItemGrouping.ROOM -> kept.groupBy { it.roomId.orEmpty() to it.roomName }.entries
                .sortedBy { it.key.second.lowercase() }
                .map { (k, v) -> ItemGroup(k.first, k.second, v.size, v.sortedWith(byName)) }
        }
    }
}
