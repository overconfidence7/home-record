package com.kurtz.homerecord.housemanual.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.repo.HomeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class RoomsViewModel(private val homes: HomeRepository) : ViewModel() {
    private val newName = MutableStateFlow("")

    val state: StateFlow<RoomsUiState> = homes.observeHome()
        .flatMapLatest { home -> if (home == null) flowOf(emptyList()) else homes.observeRooms(home.id) }
        .combine(newName) { rooms, name -> RoomsUiState(rooms.sortedBy { it.name.lowercase() }, name) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomsUiState())

    fun onEvent(event: RoomsEvent) {
        when (event) {
            is RoomsEvent.NewNameChanged -> newName.value = event.value
            RoomsEvent.Add -> add()
            is RoomsEvent.Open -> Unit // handled by the route
        }
    }

    private fun add() {
        val name = newName.value.trim()
        if (name.isEmpty()) return
        newName.update { "" }
        viewModelScope.launch {
            val home = homes.getHome() ?: return@launch
            val exists = homes.getRooms(home.id).any { it.name.equals(name, ignoreCase = true) }
            if (!exists) homes.addRoom(home.id, name)
        }
    }
}
