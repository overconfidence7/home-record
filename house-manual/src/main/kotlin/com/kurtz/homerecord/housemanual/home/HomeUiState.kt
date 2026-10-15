package com.kurtz.homerecord.housemanual.home

data class HomeUiState(
    val homeName: String = "",
    val search: String = "",
    val panelSlotCount: Int = 0,
    val percentLabeled: Int = 0,
    val hasPanel: Boolean = false,
    val shutoffCount: Int = 0,
    val equipmentCount: Int = 0,
    val roomCount: Int = 0,
)

sealed interface HomeEvent {
    data class SearchChanged(val value: String) : HomeEvent
    data object OpenPanel : HomeEvent
    data object OpenEmergency : HomeEvent
    data object OpenEquipment : HomeEvent
    data object OpenRooms : HomeEvent
}
