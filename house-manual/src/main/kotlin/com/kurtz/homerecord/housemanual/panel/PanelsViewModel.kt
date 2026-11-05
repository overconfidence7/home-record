package com.kurtz.homerecord.housemanual.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.items.ItemRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** More → Breaker panels (AC-C40); [rows] is every item of the home. */
class PanelsViewModel(rows: Flow<List<ItemRow>>) : ViewModel() {
    val state: StateFlow<PanelsUiState> = rows.map { PanelsUiState(loading = false, panels = PanelsEntry.panels(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanelsUiState())
}
