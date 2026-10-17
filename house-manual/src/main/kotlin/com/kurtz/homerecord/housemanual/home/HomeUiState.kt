package com.kurtz.homerecord.housemanual.home

import com.kurtz.homerecord.core.logic.SearchResult

data class HomeUiState(
    val homeName: String = "",
    val search: String = "",
    val panelSlotCount: Int = 0,
    val percentLabeled: Int = 0,
    val hasPanel: Boolean = false,
    val shutoffCount: Int = 0,
    val equipmentCount: Int = 0,
    val roomCount: Int = 0,
    val results: List<SearchResult> = emptyList(),
    val roomNames: Map<String, String> = emptyMap(),
    /** Panel id -> name, used to say which panel a search hit lives in once there are several (AC-98). */
    val panelNames: Map<String, String> = emptyMap(),
) {
    /** AC-19: a non-blank query replaces the hub cards with results or "Nothing found". */
    val isSearching: Boolean get() = search.isNotBlank()
}

sealed interface HomeEvent {
    data class SearchChanged(val value: String) : HomeEvent
    data object OpenPanel : HomeEvent
    /** AC-18: open HM-Panel scrolled to and highlighting [slot]. */
    data class OpenSlot(val slot: Int, val panelId: String) : HomeEvent
    data class OpenRoom(val roomId: String) : HomeEvent
    data object OpenEmergency : HomeEvent
    data object OpenEquipment : HomeEvent
    data object OpenRooms : HomeEvent
    data object OpenSettings : HomeEvent
    data object OpenImport : HomeEvent
    data object OpenReminders : HomeEvent
}
