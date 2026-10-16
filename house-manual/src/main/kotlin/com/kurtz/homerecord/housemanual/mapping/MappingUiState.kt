package com.kurtz.homerecord.housemanual.mapping

import com.kurtz.homerecord.core.mapping.MappingState
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.Room

data class MappingUiState(
    val loaded: Boolean = false,
    val mapping: MappingState = MappingState.Idle,
    val breakers: List<Breaker> = emptyList(),
    val rooms: List<Room> = emptyList(),
    /** Endpoints of [selectedRoomId], offered after the room is chosen (AC-39). */
    val endpoints: List<Endpoint> = emptyList(),
    val selectedBreakerId: String? = null,
    val selectedRoomId: String? = null,
    val selectedEndpointId: String? = null,
    val newOutletLabel: String? = null,
    val saved: Boolean = false,
) {
    val canStart: Boolean get() = mapping == MappingState.PluggedIn
    val canSave: Boolean
        get() = selectedBreakerId != null && selectedRoomId != null &&
            (selectedEndpointId != null || !newOutletLabel.isNullOrBlank())
}

sealed interface MappingEvent {
    data object Start : MappingEvent
    data object Stop : MappingEvent
    data class BreakerPicked(val id: String) : MappingEvent
    data class RoomPicked(val id: String) : MappingEvent
    data class EndpointPicked(val id: String) : MappingEvent
    /** "New outlet…" with its name; a blank name keeps Save disabled. */
    data class NewOutletChanged(val label: String) : MappingEvent
    data object Save : MappingEvent
}
