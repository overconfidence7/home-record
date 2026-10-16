package com.kurtz.homerecord.housemanual.mapping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.mapping.AlarmPlayer
import com.kurtz.homerecord.core.mapping.ChargingMonitor
import com.kurtz.homerecord.core.mapping.MappingState
import com.kurtz.homerecord.core.mapping.MappingStateMachine
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** HM-Mapping: one-person circuit mapping (AC-35..AC-41). */
class MappingViewModel(
    private val panels: PanelRepository,
    private val homes: HomeRepository,
    private val monitor: ChargingMonitor,
    private val alarm: AlarmPlayer,
) : ViewModel() {
    private val machine = MappingStateMachine(monitor.isCharging())
    private val _state = MutableStateFlow(MappingUiState(mapping = machine.state))
    val state: StateFlow<MappingUiState> = _state.asStateFlow()

    private var endpointJob: Job? = null

    init {
        viewModelScope.launch {
            val home = homes.getHome()
            val rooms = home?.let { homes.getRooms(it.id) }.orEmpty()
            _state.update { it.copy(loaded = true, rooms = rooms, breakers = loadBreakers()) }
        }
        // The receiver lives exactly as long as this ViewModel's scope (AC-41).
        viewModelScope.launch { monitor.events().collect { apply(machine.onPower(it)) } }
    }

    fun onEvent(event: MappingEvent) {
        when (event) {
            MappingEvent.Start -> apply(machine.start())
            MappingEvent.Stop -> apply(machine.stop())
            is MappingEvent.BreakerPicked -> _state.update { it.copy(selectedBreakerId = event.id) }
            is MappingEvent.RoomPicked -> pickRoom(event.id)
            is MappingEvent.EndpointPicked ->
                _state.update { it.copy(selectedEndpointId = event.id, newOutletLabel = null) }
            is MappingEvent.NewOutletChanged ->
                _state.update { it.copy(newOutletLabel = event.label, selectedEndpointId = null) }
            MappingEvent.Save -> save()
        }
    }

    private fun apply(mapping: MappingState) {
        if (mapping == MappingState.Alarm) alarm.start() else alarm.stop()
        _state.update { it.copy(mapping = mapping) }
    }

    private suspend fun loadBreakers(): List<Breaker> {
        val home = homes.getHome() ?: return emptyList()
        return panels.observeBreakersForHome(home.id).first()
            .sortedWith(compareBy({ it.slots.min() }, { it.tandemIndex ?: 0 }))
    }

    private fun pickRoom(roomId: String) {
        _state.update { it.copy(selectedRoomId = roomId, selectedEndpointId = null, newOutletLabel = null, endpoints = emptyList()) }
        endpointJob?.cancel()
        endpointJob = viewModelScope.launch {
            val endpoints = panels.observeEndpointsForRoom(roomId).first()
            _state.update { if (it.selectedRoomId == roomId) it.copy(endpoints = endpoints) else it }
        }
    }

    private fun save() {
        val s = _state.value
        val breakerId = s.selectedBreakerId ?: return
        val roomId = s.selectedRoomId ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            panels.confirmMapping(breakerId, roomId, s.selectedEndpointId, s.newOutletLabel) ?: return@launch
            machine.disarm()
            _state.update { it.copy(saved = true, breakers = loadBreakers(), mapping = machine.state) }
        }
    }

    override fun onCleared() {
        alarm.stop()
    }
}
