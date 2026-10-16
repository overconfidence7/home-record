package com.kurtz.homerecord.housemanual.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.repo.EquipmentRepository
import com.kurtz.homerecord.core.repo.HomeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentViewModel(
    homes: HomeRepository,
    equipment: EquipmentRepository,
    today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    val state: StateFlow<EquipmentUiState> = homes.observeHome().flatMapLatest { home ->
        if (home == null) flowOf(EquipmentUiState(loaded = true))
        else combine(equipment.observeEquipment(home.id), homes.observeRooms(home.id)) { list, rooms ->
            val now = today()
            EquipmentUiState(
                loaded = true,
                items = list.sortedBy { it.category.ordinal }.map { it.toItem(now) },
                roomNames = rooms.associate { it.id to it.name },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EquipmentUiState())
}
