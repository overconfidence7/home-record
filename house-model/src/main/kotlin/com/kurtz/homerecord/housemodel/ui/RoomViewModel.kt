package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.items.ItemRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * HM-Room for both apps (AC-C14, AC-H10). The room follows the plan, so a rename or a delete on another
 * device shows here; [itemRows] is the home's items in House Manual and null in Field Log, which keeps none.
 */
class RoomViewModel(
    private val repo: HomeRepository,
    private val roomId: String,
    itemRows: Flow<List<ItemRow>>? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(RoomUiState(canAddItem = itemRows != null))
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
    }
}
