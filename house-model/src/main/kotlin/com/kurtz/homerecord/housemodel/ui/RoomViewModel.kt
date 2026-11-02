package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * HM-Room for both apps (AC-C14, AC-H10, AC-C17). The room follows the plan, so a rename or a delete on another
 * device shows here; [itemRows] is the home's items in House Manual and null in Field Log, which keeps none.
 * [walls] is the room's walls with their photos; the chosen stage survives process death through [savedState].
 */
class RoomViewModel(
    private val repo: HomeRepository,
    private val roomId: String,
    itemRows: Flow<List<ItemRow>>? = null,
    walls: Flow<List<RoomWall>>? = null,
    defaultStage: WallStage = WallStage.HouseManualDefault,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        RoomUiState(
            canAddItem = itemRows != null,
            wallStage = WallStage.entries.firstOrNull { it.name == savedState.get<String>(KEY_STAGE) } ?: defaultStage,
        ),
    )
    val state: StateFlow<RoomUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val where = repo.roomLocation(roomId)
            _state.update { it.copy(floorName = where?.floorName, homeName = where?.homeName) }
            repo.observeRoom(roomId).collect { room -> _state.update { it.copy(loading = false, room = room) } }
        }
        if (itemRows != null) {
            viewModelScope.launch {
                itemRows.collect { rows -> _state.update { it.copy(items = roomItemsFor(rows, roomId)) } }
            }
        }
        if (walls != null) {
            viewModelScope.launch { walls.collect { list -> _state.update { it.copy(walls = list) } } }
        }
    }

    fun selectStage(stage: WallStage) {
        savedState[KEY_STAGE] = stage.name
        _state.update { it.copy(wallStage = stage) }
    }

    private companion object {
        const val KEY_STAGE = "wallStage"
    }
}
