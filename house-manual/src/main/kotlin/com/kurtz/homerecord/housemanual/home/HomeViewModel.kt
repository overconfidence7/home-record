package com.kurtz.homerecord.housemanual.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.model.SearchDocument
import com.kurtz.homerecord.core.model.SearchEngine
import com.kurtz.homerecord.housemodel.home.HomeSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** HM-Home (AC-H1, AC-H2, AC-H3): a summary of the house, the three quick actions and search. */
class HomeViewModel(
    summary: Flow<HomeSummary>,
    private val homeName: String,
    index: Flow<List<SearchDocument>> = flowOf(emptyList()),
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val wallNote = MutableStateFlow(false)
    private val query = saved.getStateFlow(QUERY, "")

    val state: StateFlow<HomeUiState> = combine(summary, wallNote, query, index) { s, note, q, docs ->
        HomeUiState(
            loading = false, homeName = homeName, itemCount = s.itemCount, shutoffCount = s.shutoffCount,
            attention = s.attention, recent = s.recent, wallNoteShown = note, query = q,
            results = SearchEngine.search(docs, q).map { SearchResultRow(it.document.kind, it.document.id, it.document.title, it.document.subtitle) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(homeName = homeName))

    fun onEvent(e: HomeEvent) {
        when (e) {
            // Photographing a wall arrives with the blueprint tools; until then the screen says so.
            HomeEvent.PhotographWall -> wallNote.value = true
            is HomeEvent.QueryChanged -> saved[QUERY] = e.query
            else -> Unit
        }
    }

    private companion object {
        const val QUERY = "query"
    }
}
