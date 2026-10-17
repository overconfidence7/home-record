package com.kurtz.homerecord.housemanual.rooms

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.Photo
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Shutoff

data class RoomsUiState(
    val rooms: List<Room> = emptyList(),
    val newName: String = "",
) {
    val canAdd: Boolean get() = newName.isNotBlank()
}

sealed interface RoomsEvent {
    data class NewNameChanged(val value: String) : RoomsEvent
    data object Add : RoomsEvent
    data class Open(val roomId: String) : RoomsEvent
}

data class RoomUiState(
    val loaded: Boolean = false,
    val room: Room? = null,
    val renameInput: String = "",
    val renaming: Boolean = false,
    val confirmingDelete: Boolean = false,
    val breakers: List<Breaker> = emptyList(),
    val endpoints: List<Endpoint> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val shutoffs: List<Shutoff> = emptyList(),
    /** AC-66: photos for this room, including behind-wall ones imported from Field Log. */
    val photos: List<Photo> = emptyList(),
) {
    val canSaveRename: Boolean get() = renameInput.isNotBlank()
}

sealed interface RoomEvent {
    data object StartRename : RoomEvent
    data class RenameChanged(val value: String) : RoomEvent
    data object SaveRename : RoomEvent
    data object CancelRename : RoomEvent
    data class KindChanged(val kind: com.kurtz.homerecord.core.model.RoomKind) : RoomEvent
    data object AskDelete : RoomEvent
    data object CancelDelete : RoomEvent
    data object ConfirmDelete : RoomEvent
}
