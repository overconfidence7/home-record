package com.kurtz.homerecord.housemanual.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.SlotCountRule
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
    private data class Form(val slotCount: String = SlotCountRule.DEFAULT.toString(), val layout: PanelLayout = PanelLayout.NA_NUMBERED)

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
        PanelUiState(loaded, panel, breakers, f.slotCount, f.layout)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanelUiState())

    fun onEvent(event: PanelEvent) {
        when (event) {
            is PanelEvent.SlotCountChanged -> form.update { it.copy(slotCount = event.value) }
            is PanelEvent.LayoutChanged -> form.update { it.copy(layout = event.layout) }
            PanelEvent.SavePanel -> savePanel()
            PanelEvent.TakePhoto, is PanelEvent.SlotTapped -> Unit // handled by the route
        }
    }

    /** Stores a freshly captured photo (relative media path) on the current panel. */
    fun onPhotoCaptured(relativePath: String) {
        viewModelScope.launch {
            val panel = state.value.panel ?: return@launch
            panels.setPanelPhoto(panel.id, relativePath)
        }
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
