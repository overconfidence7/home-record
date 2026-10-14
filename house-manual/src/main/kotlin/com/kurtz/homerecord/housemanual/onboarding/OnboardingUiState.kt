package com.kurtz.homerecord.housemanual.onboarding

import androidx.annotation.StringRes
import com.kurtz.homerecord.housemanual.R


/** The quick-add room list from SPEC §5 HM-Onboarding, in display order. */
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

data class OnboardingUiState(
    val homeName: String = "",
    val selected: Set<QuickRoom> = emptySet(),
    val customRooms: List<String> = emptyList(),
    val customInput: String = "",
    val saving: Boolean = false,
) {
    /** AC-2: Continue needs at least one non-space character in the home name. */
    val canContinue: Boolean get() = homeName.isNotBlank() && !saving
    val canAddCustom: Boolean get() = customInput.isNotBlank()
}

sealed interface OnboardingEvent {
    data class HomeNameChanged(val value: String) : OnboardingEvent
    data class ToggleRoom(val room: QuickRoom) : OnboardingEvent
    data class CustomInputChanged(val value: String) : OnboardingEvent
    data object AddCustomRoom : OnboardingEvent
    data class RemoveCustomRoom(val name: String) : OnboardingEvent
    data object Continue : OnboardingEvent
}
