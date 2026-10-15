package com.kurtz.homerecord.housemanual.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.PanelSummary
import com.kurtz.homerecord.core.repo.EquipmentRepository
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.PanelRepository
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
class HomeViewModel(
    homes: HomeRepository,
    panels: PanelRepository,
    shutoffs: ShutoffRepository,
    equipment: EquipmentRepository,
) : ViewModel() {
    private val search = MutableStateFlow("")

    val state: StateFlow<HomeUiState> = homes.observeHome().flatMapLatest { home ->
        if (home == null) flowOf(HomeUiState())
        else combine(
            panels.observePanels(home.id),
            panels.observeBreakersForHome(home.id),
            shutoffs.observeShutoffs(home.id),
            equipment.observeEquipment(home.id),
            homes.observeRooms(home.id),
        ) { panelList, breakers, shutoffList, equipmentList, rooms ->
            val slots = panelList.sumOf { it.slotCount }
            HomeUiState(
                homeName = home.name,
                panelSlotCount = slots,
                percentLabeled = PanelSummary.percentLabeled(slots, breakers),
                hasPanel = panelList.isNotEmpty(),
                shutoffCount = shutoffList.size,
                equipmentCount = equipmentList.size,
                roomCount = rooms.size,
            )
        }
    }.combine(search) { s, q -> s.copy(search = q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onSearchChanged(value: String) {
        search.value = value
    }
}
