package com.kurtz.homerecord.housemanual.breaker

import com.kurtz.homerecord.core.logic.AmpsRule
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.core.model.Room

/** Why Save is blocked by slot occupancy (AC-9). */
sealed interface SlotProblem {
    data class Occupied(val slot: Int) : SlotProblem
    data object OutOfRange : SlotProblem
}

/** An outlet/light/etc. attached to this breaker; [id] is blank until saved. */
data class EndpointDraft(val id: String, val kind: EndpointKind, val label: String, val roomId: String)

data class BreakerUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val slot: Int = 1,
    /** 0 or 1 when this breaker is one half of a tandem; null otherwise. */
    val tandemIndex: Int? = null,
    val label: String = "",
    val amps: String = "",
    val poles: Int = 1,
    val type: BreakerType = BreakerType.STANDARD,
    val confidence: Confidence = Confidence.UNKNOWN,
    val rooms: List<Room> = emptyList(),
    val selectedRoomIds: Set<String> = emptySet(),
    val endpoints: List<EndpointDraft> = emptyList(),
    val newEndpointKind: EndpointKind = EndpointKind.OUTLET,
    val newEndpointLabel: String = "",
    val newEndpointRoomId: String? = null,
    val problem: SlotProblem? = null,
    val confirmingDelete: Boolean = false,
    /** AC-103: flagged as this panel's main breaker. */
    val isMain: Boolean = false,
    /** Label of another breaker in this panel already flagged main; choosing main here clears it. */
    val otherMainLabel: String? = null,
) {
    val canSave: Boolean get() = loaded && label.isNotBlank() && AmpsRule.isValid(amps)
    val canAddEndpoint: Boolean get() = newEndpointLabel.isNotBlank() && newEndpointRoomId != null
    val showPoles: Boolean get() = tandemIndex == null
    val showMainToggle: Boolean get() = tandemIndex == null
}

sealed interface BreakerEvent {
    data class LabelChanged(val value: String) : BreakerEvent
    data class AmpsChanged(val value: String) : BreakerEvent
    data class PolesChanged(val poles: Int) : BreakerEvent
    data class TypeChanged(val type: BreakerType) : BreakerEvent
    data class ConfidenceChanged(val confidence: Confidence) : BreakerEvent
    data class RoomToggled(val roomId: String) : BreakerEvent
    data class EndpointKindChanged(val kind: EndpointKind) : BreakerEvent
    data class EndpointLabelChanged(val value: String) : BreakerEvent
    data class EndpointRoomChanged(val roomId: String) : BreakerEvent
    data object AddEndpoint : BreakerEvent
    data class RemoveEndpoint(val index: Int) : BreakerEvent
    data class MainChanged(val isMain: Boolean) : BreakerEvent
    data object Save : BreakerEvent
    data object DeleteClicked : BreakerEvent
    data object DeleteConfirmed : BreakerEvent
    data object DeleteDismissed : BreakerEvent
}
