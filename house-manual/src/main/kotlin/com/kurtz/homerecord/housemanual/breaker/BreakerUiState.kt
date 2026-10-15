package com.kurtz.homerecord.housemanual.breaker

import com.kurtz.homerecord.core.logic.AmpsRule
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.Confidence

/** Why Save is blocked by slot occupancy (AC-9). */
sealed interface SlotProblem {
    data class Occupied(val slot: Int) : SlotProblem
    data object OutOfRange : SlotProblem
}

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
    val problem: SlotProblem? = null,
    val confirmingDelete: Boolean = false,
) {
    val canSave: Boolean get() = loaded && label.isNotBlank() && AmpsRule.isValid(amps)
    val showPoles: Boolean get() = tandemIndex == null
}

sealed interface BreakerEvent {
    data class LabelChanged(val value: String) : BreakerEvent
    data class AmpsChanged(val value: String) : BreakerEvent
    data class PolesChanged(val poles: Int) : BreakerEvent
    data class TypeChanged(val type: BreakerType) : BreakerEvent
    data class ConfidenceChanged(val confidence: Confidence) : BreakerEvent
    data object Save : BreakerEvent
    data object DeleteClicked : BreakerEvent
    data object DeleteConfirmed : BreakerEvent
    data object DeleteDismissed : BreakerEvent
}
