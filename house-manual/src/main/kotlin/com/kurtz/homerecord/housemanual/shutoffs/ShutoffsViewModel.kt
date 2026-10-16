package com.kurtz.homerecord.housemanual.shutoffs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.ShutoffRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class ShutoffsViewModel(homes: HomeRepository, shutoffs: ShutoffRepository) : ViewModel() {
    val state: StateFlow<ShutoffsUiState> = homes.observeHome().flatMapLatest { home ->
        if (home == null) flowOf(ShutoffsUiState(loaded = true))
        else combine(shutoffs.observeShutoffs(home.id), homes.observeRooms(home.id)) { list, rooms ->
            ShutoffsUiState(true, list, rooms.associate { it.id to it.name })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShutoffsUiState())
}
