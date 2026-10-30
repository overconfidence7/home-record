package com.kurtz.homerecord.housemanual.items

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.housemodel.items.ItemFilter
import com.kurtz.homerecord.housemodel.items.ItemGrouping
import com.kurtz.homerecord.housemodel.items.ItemListBuilder
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.items.WarrantyFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Items tab (AC-H6): grouping, filters and folded groups live in [SavedStateHandle] so they survive process death. */
class ItemsViewModel(
    rows: Flow<List<ItemRow>>,
    private val saved: SavedStateHandle = SavedStateHandle(),
    /** The category's display name, so a search for "heating" finds the furnace. */
    private val categoryName: (ItemCategory) -> String,
) : ViewModel() {
    constructor(
        repo: ItemRepository,
        homeId: String,
        pseudoNames: Map<String, String>,
        saved: SavedStateHandle,
        categoryName: (ItemCategory) -> String,
    ) : this(repo.observeRows(homeId, pseudoNames), saved, categoryName)

    private val grouping = MutableStateFlow(saved.get<String>(GROUPING)?.let { runCatching { ItemGrouping.valueOf(it) }.getOrNull() } ?: ItemGrouping.CATEGORY)
    private val filter = MutableStateFlow(
        ItemFilter(
            category = saved.get<String>(CATEGORY)?.let(ItemCategory::fromId), roomId = saved[ROOM], query = saved[QUERY] ?: "",
            warranty = saved.get<String>(WARRANTY)?.let { runCatching { WarrantyFilter.valueOf(it) }.getOrNull() },
        ),
    )

    private val collapsed = MutableStateFlow(saved.get<ArrayList<String>>(COLLAPSED)?.toSet().orEmpty())

    val state: StateFlow<ItemsUiState> = combine(rows, grouping, filter, collapsed) { all, g, f, c ->
        ItemsUiState(
            collapsed = c,
            loading = false, totalCount = all.size, grouping = g, filter = f,
            groups = ItemListBuilder.build(all, g, f, categoryName),
            categoryOptions = all.map { it.category }.distinct().sorted(),
            roomOptions = all.filter { it.roomId != null }.map { RoomOption(it.roomId.orEmpty(), it.roomName) }.distinct().sortedBy { it.name.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemsUiState())

    fun onEvent(e: ItemsEvent) {
        when (e) {
            is ItemsEvent.SetGrouping -> { grouping.value = e.grouping; saved[GROUPING] = e.grouping.name }
            is ItemsEvent.SetQuery -> update { copy(query = e.query) }
            is ItemsEvent.SetCategory -> update { copy(category = e.category) }
            is ItemsEvent.SetRoom -> update { copy(roomId = e.roomId) }
            is ItemsEvent.SetWarranty -> update { copy(warranty = e.filter) }
            ItemsEvent.ClearFilters -> update { ItemFilter() }
            is ItemsEvent.ToggleGroup -> {
                val c = collapsed.value.let { if (e.key in it) it - e.key else it + e.key }
                collapsed.value = c
                saved[COLLAPSED] = ArrayList(c)
            }
            is ItemsEvent.Open, ItemsEvent.Add -> Unit // navigation is the route's job
        }
    }

    private fun update(change: ItemFilter.() -> ItemFilter) {
        val f = filter.value.change()
        filter.value = f
        saved[CATEGORY] = f.category?.name; saved[ROOM] = f.roomId; saved[QUERY] = f.query; saved[WARRANTY] = f.warranty?.name
    }

    private companion object {
        const val GROUPING = "grouping"; const val CATEGORY = "category"; const val ROOM = "room"; const val QUERY = "query"; const val WARRANTY = "warranty"; const val COLLAPSED = "collapsed"
    }
}
