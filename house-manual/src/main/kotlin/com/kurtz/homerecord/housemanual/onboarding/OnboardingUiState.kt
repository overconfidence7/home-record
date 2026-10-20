package com.kurtz.homerecord.housemanual.onboarding

import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HouseType

data class OnboardingUiState(
    val name: String = "",
    val address: String = "",
    val type: HouseType? = null,
    val foundation: Foundation = Foundation.BASEMENT,
    val attachedGarage: Boolean = false,
    val busy: Boolean = false,
    /** Set once the user pressed a button with required fields empty, so errors show then and not before. */
    val showErrors: Boolean = false,
) {
    val canSubmit: Boolean get() = name.isNotBlank() && type != null && !busy
}

sealed interface OnboardingEvent {
    data class NameChanged(val value: String) : OnboardingEvent
    data class AddressChanged(val value: String) : OnboardingEvent
    data class TypeChanged(val value: HouseType) : OnboardingEvent
    data class FoundationChanged(val value: Foundation) : OnboardingEvent
    data class GarageChanged(val value: Boolean) : OnboardingEvent
    data object DrawPlan : OnboardingEvent
    data object Skip : OnboardingEvent
}
