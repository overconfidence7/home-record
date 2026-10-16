package com.kurtz.homerecord.fieldlog.jobedit

import androidx.annotation.StringRes
import com.kurtz.homerecord.fieldlog.R

/** The quick-add room list, same as House Manual's onboarding (SPEC §5 FL-JobEdit). */
enum class QuickRoom(@StringRes val label: Int) {
    KITCHEN(R.string.room_kitchen),
    LIVING_ROOM(R.string.room_living_room),
    DINING_ROOM(R.string.room_dining_room),
    PRIMARY_BEDROOM(R.string.room_primary_bedroom),
    BEDROOM_2(R.string.room_bedroom_2),
    BEDROOM_3(R.string.room_bedroom_3),
    BATHROOM(R.string.room_bathroom),
    PRIMARY_BATH(R.string.room_primary_bath),
    GARAGE(R.string.room_garage),
    BASEMENT(R.string.room_basement),
    ATTIC(R.string.room_attic),
    LAUNDRY(R.string.room_laundry),
    OFFICE(R.string.room_office),
    HALLWAY(R.string.room_hallway),
    EXTERIOR(R.string.room_exterior),
}

data class JobEditUiState(
    val isEdit: Boolean = false,
    val loaded: Boolean = true,
    val name: String = "",
    val address: String = "",
    val clientName: String = "",
    val clientContact: String = "",
    val archived: Boolean = false,
    val selected: Set<QuickRoom> = emptySet(),
    val customRooms: List<String> = emptyList(),
    val customInput: String = "",
    val saving: Boolean = false,
) {
    /** AC-52: saving needs a name with at least one non-space character. */
    val canSave: Boolean get() = loaded && name.isNotBlank() && !saving
    val canAddCustom: Boolean get() = customInput.isNotBlank()
}

sealed interface JobEditEvent {
    data class NameChanged(val value: String) : JobEditEvent
    data class AddressChanged(val value: String) : JobEditEvent
    data class ClientNameChanged(val value: String) : JobEditEvent
    data class ClientContactChanged(val value: String) : JobEditEvent
    data class ArchivedChanged(val value: Boolean) : JobEditEvent
    data class ToggleRoom(val room: QuickRoom) : JobEditEvent
    data class CustomInputChanged(val value: String) : JobEditEvent
    data object AddCustomRoom : JobEditEvent
    data class RemoveCustomRoom(val name: String) : JobEditEvent
    data object Save : JobEditEvent
}
