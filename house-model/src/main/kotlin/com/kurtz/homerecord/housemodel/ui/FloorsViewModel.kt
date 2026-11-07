package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.Reloadable
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** HM-Floors (AC-C3): add, rename, reorder and delete floors. */
class FloorsViewModel(private val repo: HomeRepository, private val homeId: String) : ViewModel() {
    private val _state = MutableStateFlow(FloorsUiState())

    /** A failed read shows the error state with Retry instead of ending the ViewModel (AC-U4). */
    private val load = Reloadable("Floors")
    val state: StateFlow<FloorsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            load.wrap<List<FloorSummary>?>(null) { repo.observeFloors(homeId) }.collect { floors ->
                _state.update { if (floors == null) it.copy(loading = false, loadFailed = true) else it.copy(loading = false, loadFailed = false, floors = floors) }
            }
        }
    }

    fun onEvent(event: FloorsEvent) {
        when (event) {
            FloorsEvent.Retry -> load.retry()
            FloorsEvent.AddClicked -> showDialog(FloorsDialog.Add)
            is FloorsEvent.EditClicked -> showDialog(FloorsDialog.Edit(event.floor))
            is FloorsEvent.DeleteClicked -> showDialog(FloorsDialog.Delete(event.floor))
            FloorsEvent.DismissDialog -> showDialog(null)
            is FloorsEvent.MoveUp -> launch { repo.moveFloor(homeId, event.floor.id, 1) }
            is FloorsEvent.MoveDown -> launch { repo.moveFloor(homeId, event.floor.id, -1) }
            is FloorsEvent.ConfirmAdd -> {
                showDialog(null)
                if (event.name.isNotBlank()) launch { repo.addFloor(homeId, event.name) }
            }
            is FloorsEvent.ConfirmEdit -> {
                showDialog(null)
                if (event.name.isNotBlank()) launch { repo.updateFloor(event.floorId, event.name, event.ceilingHeightIn) }
            }
            is FloorsEvent.ConfirmDelete -> {
                showDialog(null)
                launch { repo.deleteFloor(event.floorId) }
            }
        }
    }

    private fun showDialog(dialog: FloorsDialog?) = _state.update { it.copy(dialog = dialog) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
