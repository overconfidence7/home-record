package com.kurtz.homerecord.housemanual.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import com.kurtz.homerecord.housemodel.panel.PanelState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * HM-Panel (AC-C40). Opening the screen creates the panel with its defaults the first time ([PanelRepository.ensurePanel]),
 * then follows it. [itemName] is the panel item's name, null once the item is gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelViewModel(
    private val repo: PanelRepository,
    private val itemId: String,
    itemName: Flow<String?>,
) : ViewModel() {
    // A panel row is only made for an item that still exists.
    private val panel: Flow<PanelState?> = itemName.map { it != null }.distinctUntilChanged().flatMapLatest { exists ->
        if (!exists) flowOf(null) else flow { emit(repo.ensurePanel(itemId)) }.flatMapLatest { repo.observe(itemId) }
    }

    val state: StateFlow<PanelUiState> = combine(itemName, panel) { name, p ->
        when {
            name == null -> PanelUiState(loading = false, missing = true)
            p == null -> PanelUiState(loading = true, name = name)
            else -> PanelUiState(loading = false, name = name, panel = p.panel, breakers = p.breakers, grid = p.grid)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanelUiState())

    fun onEvent(e: PanelEvent) {
        when (e) {
            PanelEvent.Back, is PanelEvent.OpenSlot -> Unit // the route's job
        }
    }
}
