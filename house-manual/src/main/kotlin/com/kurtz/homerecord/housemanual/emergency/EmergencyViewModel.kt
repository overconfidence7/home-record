package com.kurtz.homerecord.housemanual.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.Reloadable
import com.kurtz.homerecord.core.model.ShutoffSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** HM-Emergency (AC-C44): [sections] is `ItemRepository.observeShutoffs`, read from Room only, so it works offline. */
class EmergencyViewModel(sections: Flow<List<ShutoffSection>>) : ViewModel() {
    /** A failed read shows the error state with Retry instead of ending the ViewModel (AC-U4). */
    private val load = Reloadable("Emergency")

    val state: StateFlow<EmergencyUiState> = load.wrap(EmergencyUiState(loading = false, loadFailed = true)) {
        sections.map { EmergencyUiState(loading = false, sections = it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencyUiState())

    fun retry() = load.retry()
}
