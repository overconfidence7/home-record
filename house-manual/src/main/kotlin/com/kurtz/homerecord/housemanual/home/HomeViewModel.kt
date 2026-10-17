package com.kurtz.homerecord.housemanual.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.PanelSummary
import com.kurtz.homerecord.core.logic.SearchData
import com.kurtz.homerecord.core.logic.SearchEngine
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerRoom
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Shutoff
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
@Suppress("UNCHECKED_CAST")
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
            combine(panels.observeBreakerRoomsForHome(home.id), panels.observeEndpointsForHome(home.id)) { l, e -> l to e },
            search,
        ) { values ->
            val panelList = values[0] as List<Panel>
            val breakers = values[1] as List<Breaker>
            val shutoffList = values[2] as List<Shutoff>
            val equipmentList = values[3] as List<Equipment>
            val rooms = values[4] as List<Room>
            val (links, endpoints) = values[5] as Pair<List<BreakerRoom>, List<Endpoint>>
            val query = values[6] as String
            val slots = panelList.sumOf { it.slotCount }
            HomeUiState(
                homeName = home.name,
                panelSlotCount = slots,
                percentLabeled = PanelSummary.percentLabeled(slots, breakers),
                hasPanel = panelList.isNotEmpty(),
                shutoffCount = shutoffList.size,
                equipmentCount = equipmentList.size,
                roomCount = rooms.size,
                search = query,
                results = SearchEngine.search(query, SearchData(breakers, links, endpoints, rooms, equipmentList, shutoffList)),
                roomNames = rooms.associate { it.id to it.name },
                panelNames = if (panelList.size > 1) panelList.associate { it.id to it.name } else emptyMap(),
            )
        }
    }.combine(search) { s, q -> if (s.homeName.isEmpty()) s.copy(search = q) else s }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onSearchChanged(value: String) {
        search.value = value
    }
}
