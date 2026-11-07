package com.kurtz.homerecord.housemanual.emergency

import com.kurtz.homerecord.core.model.ShutoffSection

/** HM-Emergency (AC-C44): the home's shutoffs grouped Water / Gas / Electrical / HVAC / Other, mains first. Everything is local. */
data class EmergencyUiState(
    val loading: Boolean = true,
    /** The shutoffs could not be read; the screen shows what failed and Retry (AC-U4). */
    val loadFailed: Boolean = false,
    val sections: List<ShutoffSection> = emptyList(),
    /** A viewer on a shared home: no Add shutoff (AC-H16). */
    val viewOnly: Boolean = false,
) {
    val count: Int get() = sections.sumOf { it.shutoffs.size }
}

sealed interface EmergencyEvent {
    data object Back : EmergencyEvent
    /** Open the shutoff's item page to change its instructions or photo. */
    data class Open(val itemId: String) : EmergencyEvent
    /** Manage shutoffs: add a new shutoff item. */
    data object Add : EmergencyEvent
    data object Retry : EmergencyEvent
}
