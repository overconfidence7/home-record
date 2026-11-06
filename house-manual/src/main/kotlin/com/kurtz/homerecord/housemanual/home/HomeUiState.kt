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
    val query: String = "",
    val results: List<SearchResultRow> = emptyList(),
) {
    val searching: Boolean get() = query.isNotBlank()
}

/**
 * [subkind] is [com.kurtz.homerecord.core.model.SearchDocument.subkind]: an overlay row draws its kind's icon and color.
 * A breaker row opens its panel item [openId] at [slot].
 */
data class SearchResultRow(
    val kind: SearchKind,
    val id: String,
    val title: String,
    val subtitle: String?,
    val subkind: String? = null,
    val openId: String? = null,
    val slot: Int? = null,
)

sealed interface HomeEvent {
    data class OpenItem(val itemId: String) : HomeEvent
    data object AddItem : HomeEvent
    data object ScanDataPlate : HomeEvent
    data object AddShutoff : HomeEvent
    /** HM-Emergency from the Emergency card (AC-H11). */
    data object OpenEmergency : HomeEvent
    data class QueryChanged(val query: String) : HomeEvent
    /** [id] is what opens: the record itself, or for a breaker its panel item, with the breaker's [slot]. */
    data class OpenResult(val kind: SearchKind, val id: String, val slot: Int? = null) : HomeEvent
}
