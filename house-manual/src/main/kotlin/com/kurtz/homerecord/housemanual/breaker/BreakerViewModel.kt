package com.kurtz.homerecord.housemanual.breaker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.AmpsRule
import com.kurtz.homerecord.core.logic.Placement
import com.kurtz.homerecord.core.logic.SlotOccupancy
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Source
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
) : ViewModel() {
    private val _state = MutableStateFlow(BreakerUiState(slot = slot, tandemIndex = newTandemIndex))
    val state: StateFlow<BreakerUiState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    /** Emits after save or delete so the route can pop. */
    val done = _done.receiveAsFlow()

    private var existing: Breaker? = null

    init {
        viewModelScope.launch {
            val found = breakerId?.let { panels.getBreaker(it) }
            existing = found
            _state.update {
                if (found == null) it.copy(loaded = true)
                else it.copy(
                    loaded = true, isNew = false, slot = found.slots.min(), tandemIndex = found.tandemIndex,
                    label = found.label, amps = found.amps?.toString().orEmpty(), poles = found.poles,
                    type = found.type, confidence = found.confidence,
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
            panels.saveBreaker(
                base.copy(
                    slots = slots,
                    poles = if (s.tandemIndex != null) 1 else s.poles,
                    amps = AmpsRule.parse(s.amps),
                    type = s.type,
                    label = s.label.trim(),
                    confidence = s.confidence,
                    tandemIndex = s.tandemIndex,
                ),
            )
            _done.send(Unit)
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
