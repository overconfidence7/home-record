package com.kurtz.homerecord.housemanual.items

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/** Items tab (AC-H6): grouping and filters live in [SavedStateHandle] so they survive process death. */
class ItemsViewModel(rows: Flow<List<ItemRow>>, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    constructor(repo: ItemRepository, homeId: String, pseudoNames: Map<String, String>, saved: SavedStateHandle) :
        this(repo.observeRows(homeId, pseudoNames), saved)

    private val grouping = MutableStateFlow(saved.get<String>(GROUPING)?.let { runCatching { ItemGrouping.valueOf(it) }.getOrNull() } ?: ItemGrouping.CATEGORY)
    private val filter = MutableStateFlow(
        ItemFilter(
            categoryLabel = saved[CATEGORY], roomId = saved[ROOM], query = saved[QUERY] ?: "",
            warranty = saved.get<String>(WARRANTY)?.let { runCatching { WarrantyFilter.valueOf(it) }.getOrNull() },
        ),
    )

    val state: StateFlow<ItemsUiState> = combine(rows, grouping, filter) { all, g, f ->
        ItemsUiState(
            loading = false, totalCount = all.size, grouping = g, filter = f,
            groups = ItemListBuilder.build(all, g, f),
            categoryOptions = all.map { it.categoryLabel }.distinct().sortedBy(ItemListBuilder::categoryOrder),
            roomOptions = all.filter { it.roomId != null }.map { RoomOption(it.roomId.orEmpty(), it.roomName) }.distinct().sortedBy { it.name.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemsUiState())

    fun onEvent(e: ItemsEvent) {
        when (e) {
            is ItemsEvent.SetGrouping -> { grouping.value = e.grouping; saved[GROUPING] = e.grouping.name }
            is ItemsEvent.SetQuery -> update { copy(query = e.query) }
            is ItemsEvent.SetCategory -> update { copy(categoryLabel = e.label) }
            is ItemsEvent.SetRoom -> update { copy(roomId = e.roomId) }
            is ItemsEvent.SetWarranty -> update { copy(warranty = e.filter) }
            ItemsEvent.ClearFilters -> update { ItemFilter() }
            is ItemsEvent.Open, ItemsEvent.Add -> Unit // navigation is the route's job
        }
    }

    private fun update(change: ItemFilter.() -> ItemFilter) {
        val f = filter.value.change()
        filter.value = f
        saved[CATEGORY] = f.categoryLabel; saved[ROOM] = f.roomId; saved[QUERY] = f.query; saved[WARRANTY] = f.warranty?.name
    }

    private companion object {
        const val GROUPING = "grouping"; const val CATEGORY = "category"; const val ROOM = "room"; const val QUERY = "query"; const val WARRANTY = "warranty"
    }
}
