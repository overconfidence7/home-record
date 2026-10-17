package com.kurtz.homerecord.housemanual.breaker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.AmpsRule
import com.kurtz.homerecord.core.logic.MainBreakerRule
import com.kurtz.homerecord.core.logic.Placement
import com.kurtz.homerecord.core.logic.SlotOccupancy
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * HM-Breaker: edits one breaker (AC-8..AC-12). Opened with a [panelId] and either an existing
 * [breakerId] or an empty [slot] (plus [newTandemIndex] when filling an empty tandem half).
 */
class BreakerViewModel(
    private val panelId: String,
    private val slot: Int,
    private val breakerId: String?,
    private val newTandemIndex: Int?,
    private val panels: PanelRepository,
    private val homes: HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BreakerUiState(slot = slot, tandemIndex = newTandemIndex))
    val state: StateFlow<BreakerUiState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    /** Emits after save or delete so the route can pop. */
    val done = _done.receiveAsFlow()

    private var existing: Breaker? = null
    private var originalEndpointIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            val found = breakerId?.let { panels.getBreaker(it) }
            existing = found
            val rooms = panels.getPanel(panelId)?.let { homes.getRooms(it.homeId) }.orEmpty()
            val linkedRooms = found?.let { panels.observeBreakerRooms(it.id).first().map { l -> l.roomId }.toSet() }.orEmpty()
            val endpoints = found?.let { panels.observeEndpointsForBreaker(it.id).first() }.orEmpty()
                .map { EndpointDraft(it.id, it.kind, it.label, it.roomId) }
            originalEndpointIds = endpoints.map { it.id }.toSet()
            val otherMain = MainBreakerRule.toClear(panels.observeBreakers(panelId).first(), found?.id.orEmpty()).firstOrNull()
            _state.update {
                val withLinks = it.copy(loaded = true, otherMainLabel = otherMain?.let { m -> m.label.ifBlank { m.slots.min().toString() } }, rooms = rooms, selectedRoomIds = linkedRooms, endpoints = endpoints, newEndpointRoomId = rooms.firstOrNull()?.id)
                if (found == null) withLinks
                else withLinks.copy(
                    isNew = false, slot = found.slots.min(), tandemIndex = found.tandemIndex,
                    label = found.label, amps = found.amps?.toString().orEmpty(), poles = found.poles,
                    type = found.type, confidence = found.confidence, isMain = found.isMain,
                )
            }
        }
    }

    fun onEvent(event: BreakerEvent) {
        when (event) {
            is BreakerEvent.LabelChanged -> _state.update { it.copy(label = event.value) }
            is BreakerEvent.AmpsChanged -> _state.update { it.copy(amps = event.value) }
            is BreakerEvent.PolesChanged -> _state.update { it.copy(poles = event.poles, problem = null) }
            is BreakerEvent.TypeChanged -> _state.update { it.copy(type = event.type) }
            is BreakerEvent.ConfidenceChanged -> _state.update { it.copy(confidence = event.confidence) }
            is BreakerEvent.RoomToggled -> _state.update {
                it.copy(selectedRoomIds = if (event.roomId in it.selectedRoomIds) it.selectedRoomIds - event.roomId else it.selectedRoomIds + event.roomId)
            }
            is BreakerEvent.EndpointKindChanged -> _state.update { it.copy(newEndpointKind = event.kind) }
            is BreakerEvent.EndpointLabelChanged -> _state.update { it.copy(newEndpointLabel = event.value) }
            is BreakerEvent.EndpointRoomChanged -> _state.update { it.copy(newEndpointRoomId = event.roomId) }
            BreakerEvent.AddEndpoint -> _state.update {
                val room = it.newEndpointRoomId
                if (!it.canAddEndpoint || room == null) it
                else it.copy(
                    endpoints = it.endpoints + EndpointDraft("", it.newEndpointKind, it.newEndpointLabel.trim(), room),
                    newEndpointLabel = "",
                )
            }
            is BreakerEvent.RemoveEndpoint -> _state.update { it.copy(endpoints = it.endpoints.filterIndexed { i, _ -> i != event.index }) }
            is BreakerEvent.MainChanged -> _state.update { it.copy(isMain = event.isMain) }
            BreakerEvent.Save -> save()
            BreakerEvent.DeleteClicked -> _state.update { it.copy(confirmingDelete = true) }
            BreakerEvent.DeleteDismissed -> _state.update { it.copy(confirmingDelete = false) }
            BreakerEvent.DeleteConfirmed -> delete()
        }
    }

    private fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val panel = panels.getPanel(panelId) ?: return@launch
            val slots = if (s.tandemIndex != null) {
                listOf(s.slot)
            } else {
                when (val placement = SlotOccupancy.place(s.slot, s.poles, panel.layout, panel.slotCount, panels.observeBreakers(panelId).first(), existing?.id)) {
                    is Placement.Ok -> placement.slots
                    is Placement.Occupied -> return@launch _state.update { it.copy(problem = SlotProblem.Occupied(placement.slot)) }
                    Placement.OutOfRange -> return@launch _state.update { it.copy(problem = SlotProblem.OutOfRange) }
                }
            }
            val base = existing ?: Breaker("", panelId, slots, label = "", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL)
            val saved = panels.saveBreaker(
                base.copy(
                    slots = slots,
                    poles = if (s.tandemIndex != null) 1 else s.poles,
                    amps = AmpsRule.parse(s.amps),
                    type = s.type,
                    label = s.label.trim(),
                    confidence = s.confidence,
                    tandemIndex = s.tandemIndex,
                    isMain = s.isMain && s.tandemIndex == null,
                ),
            )
            syncLinks(saved.id, s)
            _done.send(Unit)
        }
    }

    /** AC-13/AC-14: applies the chosen room chips and endpoint list to the stored breaker. */
    private suspend fun syncLinks(breakerId: String, s: BreakerUiState) {
        val current = panels.observeBreakerRooms(breakerId).first().map { it.roomId }.toSet()
        (s.selectedRoomIds - current).forEach { panels.linkRoom(breakerId, it) }
        (current - s.selectedRoomIds).forEach { panels.unlinkRoom(breakerId, it) }
        val keptIds = s.endpoints.map { it.id }.toSet()
        (originalEndpointIds - keptIds).forEach { panels.deleteEndpoint(it) }
        s.endpoints.filter { it.id.isBlank() }.forEach {
            panels.saveEndpoint(Endpoint("", breakerId, it.roomId, it.kind, it.label, createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL))
        }
    }

    private fun delete() {
        val id = existing?.id ?: return
        viewModelScope.launch {
            panels.deleteBreaker(id)
            _done.send(Unit)
        }
    }
}
