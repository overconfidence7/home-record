package com.kurtz.homerecord.housemanual.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.RoomBreakerResolver
import com.kurtz.homerecord.core.repo.EquipmentRepository
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import com.kurtz.homerecord.core.repo.ShutoffRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** HM-Room: AC-20 (breakers by direct link or endpoint) and AC-21 (equipment and shutoffs in the room). */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomViewModel(
    private val roomId: String,
    private val homes: HomeRepository,
    private val panels: PanelRepository,
    shutoffs: ShutoffRepository,
    equipment: EquipmentRepository,
) : ViewModel() {
    private data class Ui(val renaming: Boolean = false, val input: String = "", val confirmingDelete: Boolean = false)

    private val ui = MutableStateFlow(Ui())
    private val _deleted = Channel<Unit>(Channel.BUFFERED)
    /** Emits after the room is deleted so the route can pop. */
    val deleted = _deleted.receiveAsFlow()

    private val content = homes.observeHome().flatMapLatest { home ->
        if (home == null) flowOf(RoomUiState(loaded = true))
        else combine(
            homes.observeRooms(home.id),
            panels.observeBreakersForHome(home.id),
            panels.observeBreakerRoomsForRoom(roomId),
            panels.observeEndpointsForRoom(roomId),
            combine(shutoffs.observeShutoffs(home.id), equipment.observeEquipment(home.id), homes.observePhotos(home.id)) { s, e, p -> Triple(s, e, p) },
        ) { rooms, breakers, links, endpoints, (shutoffList, equipmentList, photoList) ->
            RoomUiState(
                loaded = true,
                room = rooms.firstOrNull { it.id == roomId },
                breakers = RoomBreakerResolver.resolve(roomId, breakers, links, endpoints),
                endpoints = endpoints,
                equipment = equipmentList.filter { it.roomId == roomId },
                shutoffs = shutoffList.filter { it.roomId == roomId },
                photos = photoList.filter { it.roomId == roomId },
            )
        }
    }

    val state: StateFlow<RoomUiState> = combine(content, ui) { c, u ->
        c.copy(renaming = u.renaming, renameInput = u.input, confirmingDelete = u.confirmingDelete)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomUiState())

    fun onEvent(event: RoomEvent) {
        when (event) {
            RoomEvent.StartRename -> ui.update { it.copy(renaming = true, input = state.value.room?.name.orEmpty()) }
            is RoomEvent.RenameChanged -> ui.update { it.copy(input = event.value) }
            RoomEvent.CancelRename -> ui.update { it.copy(renaming = false) }
            RoomEvent.SaveRename -> {
                val name = ui.value.input.trim()
                if (name.isEmpty()) return
                ui.update { it.copy(renaming = false) }
                viewModelScope.launch { homes.renameRoom(roomId, name) }
            }
            RoomEvent.AskDelete -> ui.update { it.copy(confirmingDelete = true) }
            RoomEvent.CancelDelete -> ui.update { it.copy(confirmingDelete = false) }
            RoomEvent.ConfirmDelete -> {
                ui.update { it.copy(confirmingDelete = false) }
                viewModelScope.launch {
                    homes.deleteRoom(roomId)
                    _deleted.send(Unit)
                }
            }
        }
    }
}
