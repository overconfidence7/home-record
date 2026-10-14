package com.kurtz.homerecord.housemanual.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.repo.HomeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val homes: HomeRepository,
    private val roomLabel: (QuickRoom) -> String,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    /** Emits once the home has been created (navigate to HM-Panel). */
    val done = _done.receiveAsFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.HomeNameChanged -> _state.update { it.copy(homeName = event.value) }
            is OnboardingEvent.ToggleRoom -> _state.update {
                it.copy(selected = if (event.room in it.selected) it.selected - event.room else it.selected + event.room)
            }
            is OnboardingEvent.CustomInputChanged -> _state.update { it.copy(customInput = event.value) }
            OnboardingEvent.AddCustomRoom -> _state.update {
                val name = it.customInput.trim()
                val exists = it.customRooms.any { r -> r.equals(name, ignoreCase = true) }
                if (name.isEmpty()) it
                else it.copy(customRooms = if (exists) it.customRooms else it.customRooms + name, customInput = "")
            }
            is OnboardingEvent.RemoveCustomRoom -> _state.update { it.copy(customRooms = it.customRooms - event.name) }
            OnboardingEvent.Continue -> submit()
        }
    }

    private fun submit() {
        val s = _state.value
        if (!s.canContinue) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val rooms = QuickRoom.entries.filter { it in s.selected }.map(roomLabel) + s.customRooms
            homes.createHomeWithRooms(s.homeName, rooms)
            _done.send(Unit)
        }
    }
}
