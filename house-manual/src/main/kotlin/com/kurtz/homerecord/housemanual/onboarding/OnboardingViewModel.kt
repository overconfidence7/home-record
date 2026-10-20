package com.kurtz.homerecord.housemanual.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** HM-Onboarding (AC-C1). Typed answers survive process death. Success is seen by the app gate through [HomeRepository.observeHome]. */
class OnboardingViewModel(
    private val repo: HomeRepository,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        OnboardingUiState(
            name = saved.get<String>(NAME).orEmpty(),
            address = saved.get<String>(ADDRESS).orEmpty(),
            type = saved.get<String>(TYPE)?.let(HouseType::parse),
            foundation = saved.get<String>(FOUNDATION)?.let(Foundation::parse) ?: Foundation.BASEMENT,
            attachedGarage = saved.get<Boolean>(GARAGE) ?: false,
        ),
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.NameChanged -> update { copy(name = event.value) }.also { saved[NAME] = event.value }
            is OnboardingEvent.AddressChanged -> update { copy(address = event.value) }.also { saved[ADDRESS] = event.value }
            is OnboardingEvent.TypeChanged -> update { copy(type = event.value) }.also { saved[TYPE] = event.value.name }
            is OnboardingEvent.FoundationChanged -> update { copy(foundation = event.value) }.also { saved[FOUNDATION] = event.value.name }
            is OnboardingEvent.GarageChanged -> update { copy(attachedGarage = event.value) }.also { saved[GARAGE] = event.value }
            OnboardingEvent.DrawPlan -> submit(seedFromTemplate = true)
            OnboardingEvent.Skip -> submit(seedFromTemplate = false)
        }
    }

    private fun submit(seedFromTemplate: Boolean) {
        val s = _state.value
        val type = s.type
        if (s.busy) return
        if (s.name.isBlank() || type == null) {
            update { copy(showErrors = true) }
            return
        }
        update { copy(busy = true) }
        viewModelScope.launch {
            repo.createHome(NewHome(s.name, s.address, type, s.foundation, s.attachedGarage, seedFromTemplate))
        }
    }

    private fun update(block: OnboardingUiState.() -> OnboardingUiState) = _state.update(block)

    private companion object {
        const val NAME = "name"
        const val ADDRESS = "address"
        const val TYPE = "type"
        const val FOUNDATION = "foundation"
        const val GARAGE = "garage"
    }
}
