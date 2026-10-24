package com.kurtz.homerecord.housemanual.home

import com.kurtz.homerecord.core.model.SearchKind
import com.kurtz.homerecord.housemodel.home.AttentionRow
import com.kurtz.homerecord.housemodel.items.ItemRow

data class HomeUiState(
    val loading: Boolean = true,
    val homeName: String = "",
    val itemCount: Int = 0,
    val shutoffCount: Int = 0,
    val attention: List<AttentionRow> = emptyList(),
    val recent: List<ItemRow> = emptyList(),
    val wallNoteShown: Boolean = false,
    val query: String = "",
    val results: List<SearchResultRow> = emptyList(),
) {
    val searching: Boolean get() = query.isNotBlank()
}

data class SearchResultRow(val kind: SearchKind, val id: String, val title: String, val subtitle: String?)

sealed interface HomeEvent {
    data class OpenItem(val itemId: String) : HomeEvent
    data object AddItem : HomeEvent
    data object ScanDataPlate : HomeEvent
    data object PhotographWall : HomeEvent
    data class QueryChanged(val query: String) : HomeEvent
    data class OpenResult(val kind: SearchKind, val id: String) : HomeEvent
}
