package com.kurtz.homerecord.housemanual.shutoffs

import com.kurtz.homerecord.core.logic.ShutoffRule
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.ShutoffKind

data class ShutoffEditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val kind: ShutoffKind? = null,
    val roomId: String? = null,
    val photoPath: String? = null,
    val instructions: String = "",
    val rooms: List<Room> = emptyList(),
    val confirmingDelete: Boolean = false,
) {
    val canSave: Boolean get() = ShutoffRule.canSave(kind, instructions)
}

sealed interface ShutoffEditEvent {
    data class KindChanged(val kind: ShutoffKind) : ShutoffEditEvent
    data class RoomChanged(val roomId: String?) : ShutoffEditEvent
    data class InstructionsChanged(val value: String) : ShutoffEditEvent
    data object TakePhoto : ShutoffEditEvent
    data object RemovePhoto : ShutoffEditEvent
    data object Save : ShutoffEditEvent
    data object AskDelete : ShutoffEditEvent
    data object CancelDelete : ShutoffEditEvent
    data object ConfirmDelete : ShutoffEditEvent
}
