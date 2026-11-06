package com.kurtz.homerecord.housemanual.emergency

import com.kurtz.homerecord.core.model.ShutoffSection

/** HM-Emergency (AC-C44): the home's shutoffs grouped Water / Gas / Electrical / HVAC / Other, mains first. Everything is local. */
data class EmergencyUiState(
    val loading: Boolean = true,
    val sections: List<ShutoffSection> = emptyList(),
) {
    val count: Int get() = sections.sumOf { it.shutoffs.size }
}

sealed interface EmergencyEvent {
    data object Back : EmergencyEvent
    /** Open the shutoff's item page to change its instructions or photo. */
    data class Open(val itemId: String) : EmergencyEvent
    /** Manage shutoffs: add a new shutoff item. */
    data object Add : EmergencyEvent
}
