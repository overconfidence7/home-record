package com.kurtz.homerecord.housemanual.house

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.defaultFloor
import com.kurtz.homerecord.housemodel.blueprint.LayerStore
import com.kurtz.homerecord.housemodel.plan.PlanEditor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** HM-House: the blueprint of one floor with layer toggles and the room long-press menu (AC-C13, AC-C14, AC-C15). */
@OptIn(ExperimentalCoroutinesApi::class)
class HouseViewModel(
    private val repo: HomeRepository,
    private val homeId: String,
    private val layerStore: LayerStore,
    private val saved: SavedStateHandle,
    homeName: String,
    summary: String,
    system: UnitSystem = UnitSystem.IMPERIAL,
) : ViewModel() {
    private val _state = MutableStateFlow(
        HouseUiState(homeName = homeName, summary = summary, floorId = saved[FLOOR], layers = layerStore.read(homeId), system = system),
    )
    val state: StateFlow<HouseUiState> = _state.asStateFlow()

    private val floorIds = MutableStateFlow<String?>(saved[FLOOR])

    init {
        viewModelScope.launch {
            repo.observeFloors(homeId).collect { floors ->
                val current = _state.value.floorId
                val chosen = floors.firstOrNull { it.id == current }
                    ?: floors.defaultFloor()
                _state.update { it.copy(floors = floors, floorId = chosen?.id, loading = false) }
                saved[FLOOR] = chosen?.id
                floorIds.value = chosen?.id
            }
        }
        viewModelScope.launch {
            floorIds.flatMapLatest { id -> if (id == null) emptyFlow() else repo.observePlan(id) }.collect { plan ->
                _state.update { it.copy(rooms = plan.placed) }
            }
        }
    }

    fun onEvent(event: HouseEvent) {
        when (event) {
            is HouseEvent.SelectFloor -> {
                saved[FLOOR] = event.floorId
                _state.update { it.copy(floorId = event.floorId, rooms = emptyList()) }
                floorIds.value = event.floorId
            }
            is HouseEvent.ToggleLayer -> {
                val next = _state.value.layers.toggled(event.layer)
                layerStore.write(homeId, next)
                _state.update { it.copy(layers = next) }
            }
            is HouseEvent.LongPressRoom -> _state.update { it.copy(dialog = HouseDialog.Menu(event.room)) }
            HouseEvent.DismissDialog -> _state.update { it.copy(dialog = null) }
            is HouseEvent.StartRename -> _state.update { it.copy(dialog = HouseDialog.Rename(event.room)) }
            is HouseEvent.StartDelete -> _state.update { it.copy(dialog = HouseDialog.ConfirmDelete(event.room)) }
            is HouseEvent.Rename -> edit { it.rename(event.roomId, event.name.trim()) }
            is HouseEvent.ConfirmDelete -> edit { it.delete(event.roomId) }
        }
    }

    private fun edit(change: (PlanEditor) -> Unit) {
        val floorId = _state.value.floorId ?: return
        _state.update { it.copy(dialog = null) }
        viewModelScope.launch {
            val editor = PlanEditor(repo.loadPlan(floorId).placed)
            change(editor)
            repo.savePlan(floorId, editor.rooms)
        }
    }

    private companion object {
        const val FLOOR = "floor"
    }
}
