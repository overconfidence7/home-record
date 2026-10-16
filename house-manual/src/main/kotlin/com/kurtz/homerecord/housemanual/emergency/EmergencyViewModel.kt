package com.kurtz.homerecord.housemanual.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.EmergencyGrouper
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.ShutoffRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyViewModel(homes: HomeRepository, shutoffs: ShutoffRepository) : ViewModel() {
    private val zoomed = MutableStateFlow<String?>(null)

    private val data = homes.observeHome().flatMapLatest { home ->
        if (home == null) flowOf(EmergencyUiState(loaded = true))
        else combine(shutoffs.observeShutoffs(home.id), homes.observeRooms(home.id)) { list, rooms ->
            EmergencyUiState(true, EmergencyGrouper.group(list), rooms.associate { it.id to it.name })
        }
    }

    val state: StateFlow<EmergencyUiState> = combine(data, zoomed) { s, z -> s.copy(zoomedPhotoPath = z) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencyUiState())

    fun zoom(path: String?) { zoomed.value = path }
}
