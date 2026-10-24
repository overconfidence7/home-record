package com.kurtz.homerecord.housemanual.home

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
)

sealed interface HomeEvent {
    data class OpenItem(val itemId: String) : HomeEvent
    data object AddItem : HomeEvent
    data object ScanDataPlate : HomeEvent
    data object PhotographWall : HomeEvent
}
