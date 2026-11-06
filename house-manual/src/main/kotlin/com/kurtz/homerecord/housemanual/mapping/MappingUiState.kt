package com.kurtz.homerecord.housemanual.mapping

import com.kurtz.homerecord.core.mapping.MappingPhase
import com.kurtz.homerecord.housemodel.panel.FeedOption

/** One breaker to pick after the alarm: [number] is the slot on the panel door, [label] may be blank. */
data class MappingBreaker(val id: String, val number: Int, val label: String, val amps: Int?)

/** HM-Mapping (AC-C42). [roomId]/[itemId]/[breakerId] are the pick in progress after the alarm. */
data class MappingUiState(
    val loading: Boolean = true,
    val panelName: String = "",
    val phase: MappingPhase = MappingPhase.IDLE,
    val plugged: Boolean = false,
    val keepScreenOn: Boolean = false,
    val breakers: List<MappingBreaker> = emptyList(),
    val rooms: List<FeedOption> = emptyList(),
    val items: List<FeedOption> = emptyList(),
    val breakerId: String? = null,
    val roomId: String? = null,
    val itemId: String? = null,
    /** How many links this visit confirmed, for the line under the status. */
    val confirmed: Int = 0,
) {
    val canSave: Boolean get() = breakerId != null && (roomId != null || itemId != null)
}

sealed interface MappingEvent {
    data object Back : MappingEvent
    data object Start : MappingEvent
    data object Stop : MappingEvent
    data object Silence : MappingEvent
    data object Resume : MappingEvent
    data class PickBreaker(val id: String) : MappingEvent
    data class PickRoom(val id: String) : MappingEvent
    data class PickItem(val id: String) : MappingEvent
    data object Save : MappingEvent
    data object Skip : MappingEvent
}
