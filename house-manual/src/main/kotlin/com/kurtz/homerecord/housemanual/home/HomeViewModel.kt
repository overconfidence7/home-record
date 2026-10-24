package com.kurtz.homerecord.housemanual.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.home.HomeSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** HM-Home (AC-H1, AC-H2): a summary of the house with the three quick actions. */
class HomeViewModel(summary: Flow<HomeSummary>, private val homeName: String) : ViewModel() {
    private val wallNote = MutableStateFlow(false)

    val state: StateFlow<HomeUiState> = combine(summary, wallNote) { s, note ->
        HomeUiState(
            loading = false, homeName = homeName, itemCount = s.itemCount, shutoffCount = s.shutoffCount,
            attention = s.attention, recent = s.recent, wallNoteShown = note,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(homeName = homeName))

    fun onEvent(e: HomeEvent) {
        // Photographing a wall arrives with the blueprint tools; until then the screen says so.
        if (e == HomeEvent.PhotographWall) wallNote.value = true
    }
}
