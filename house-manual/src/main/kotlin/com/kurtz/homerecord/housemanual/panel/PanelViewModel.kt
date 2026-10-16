package com.kurtz.homerecord.housemanual.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.LegendRow
import com.kurtz.homerecord.core.logic.LegendRowBuilder
import com.kurtz.homerecord.core.logic.ShrinkCheck
import com.kurtz.homerecord.core.logic.SlotCountRule
import com.kurtz.homerecord.core.logic.SlotOccupancy
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** HM-Panel: first panel setup (AC-5), photo (AC-6) and the slot grid data. */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelViewModel(
    private val homes: HomeRepository,
    private val panels: PanelRepository,
    private val defaultPanelName: String,
) : ViewModel() {
    private data class Form(
        val slotCount: String = SlotCountRule.DEFAULT.toString(),
        val layout: PanelLayout = PanelLayout.NA_NUMBERED,
        val slotEdit: String? = null,
        val blockedAt: Int? = null,
    )

    private val form = MutableStateFlow(Form())

    private val panelAndBreakers = homes.observeHome().flatMapLatest { home ->
        if (home == null) {
            flowOf(Triple<Boolean, Panel?, List<Breaker>>(false, null, emptyList()))
        } else {
            panels.observePanels(home.id).flatMapLatest { list ->
                val panel = list.minByOrNull { it.createdAt }
                if (panel == null) flowOf(Triple<Boolean, Panel?, List<Breaker>>(true, null, emptyList()))
                else panels.observeBreakers(panel.id).map { Triple(true, panel, it) }
            }
        }
    }

    val state: StateFlow<PanelUiState> = combine(panelAndBreakers, form) { (loaded, panel, breakers), f ->
        PanelUiState(loaded, panel, breakers, f.slotCount, f.layout, f.slotEdit, f.blockedAt)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanelUiState())

    fun onEvent(event: PanelEvent) {
        when (event) {
            is PanelEvent.SlotCountChanged -> form.update { it.copy(slotCount = event.value) }
            is PanelEvent.LayoutChanged -> form.update { it.copy(layout = event.layout) }
            PanelEvent.SavePanel -> savePanel()
            is PanelEvent.SlotEditChanged -> form.update { it.copy(slotEdit = event.value, blockedAt = null) }
            PanelEvent.ApplySlotCount -> applySlotCount()
            is PanelEvent.SlotLongPressed -> makeTandem(event.slot)
            PanelEvent.TakePhoto, PanelEvent.ShareLegend, is PanelEvent.OpenBreaker -> Unit // handled by the route
        }
    }

    /** Stores a freshly captured photo (relative media path) on the current panel. */
    fun onPhotoCaptured(relativePath: String) {
        viewModelScope.launch {
            val panel = state.value.panel ?: return@launch
            panels.setPanelPhoto(panel.id, relativePath)
        }
    }

    /** AC-23: legend rows for the current panel; [typeLabel] supplies the localized breaker type. */
    suspend fun legendRows(typeLabel: (Breaker) -> String): List<LegendRow> {
        val panel = state.value.panel ?: return emptyList()
        val home = homes.getHome() ?: return emptyList()
        val roomNames = homes.getRooms(home.id).associate { it.id to it.name }
        val roomsByBreaker = panels.observeBreakerRoomsForHome(home.id).first().groupBy({ it.breakerId }, { it.roomId })
        return LegendRowBuilder.build(
            panel.slotCount,
            panels.observeBreakers(panel.id).first(),
            { b -> roomsByBreaker[b.id].orEmpty().mapNotNull { roomNames[it] } },
            typeLabel,
        )
    }

    private fun applySlotCount() {
        val panel = state.value.panel ?: return
        val count = SlotCountRule.parse(state.value.slotEditInput ?: return) ?: return
        viewModelScope.launch {
            when (val result = panels.setSlotCount(panel.id, count)) {
                ShrinkCheck.Ok -> form.update { it.copy(slotEdit = null, blockedAt = null) }
                is ShrinkCheck.Blocked -> form.update { it.copy(blockedAt = result.highestOccupied) }
            }
        }
    }

    /** AC-10: long-press on a lone one-pole breaker splits its slot into a tandem pair. */
    private fun makeTandem(slot: Int) {
        val candidate = SlotOccupancy.tandemCandidate(slot, state.value.breakers) ?: return
        viewModelScope.launch { panels.makeTandem(candidate.id) }
    }

    private fun savePanel() {
        val count = SlotCountRule.parse(form.value.slotCount) ?: return
        val layout = form.value.layout
        viewModelScope.launch {
            val home = homes.getHome() ?: return@launch
            if (panels.observePanels(home.id).first().isNotEmpty()) return@launch
            panels.createPanel(home.id, defaultPanelName, count, layout)
        }
    }
}
