package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.defaultFloor
import com.kurtz.homerecord.housemodel.items.ItemPlace
import com.kurtz.homerecord.housemodel.items.ItemPlacement
import com.kurtz.homerecord.housemodel.items.ItemRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Place on plan (AC-C30 position): shows the floor the item's room is on, and a tap on the plan puts the item there
 * ([ItemPlacement]), saved right away the way the plan editor saves each edit. The floor the user picked survives
 * rotation and process death through [saved].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemPlaceViewModel(
    private val homes: HomeRepository,
    private val items: ItemRepository,
    private val homeId: String,
    private val itemId: String,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val system: UnitSystem = UnitSystem.IMPERIAL,
) : ViewModel() {
    private val picked = MutableStateFlow<String?>(saved[FLOOR])

    /** The floor of the item's room as first seen, so the screen opens where the item is. */
    private val itemFloor = MutableStateFlow<String?>(null)
    private val itemFloorKnown = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val roomId = items.get(itemId)?.roomId
            itemFloor.value = roomId?.let { homes.roomLocation(it)?.floorId }
            itemFloorKnown.value = true
        }
    }

    private val floorChoice = combine(homes.observeFloors(homeId), picked, itemFloor, itemFloorKnown) { floors, p, f, known ->
        val chosen = floors.firstOrNull { it.id == p } ?: floors.firstOrNull { it.id == f } ?: floors.defaultFloor()
        Triple(floors, chosen?.id, known)
    }

    /** The chosen floor with its rooms, so a floor switch never shows the new floor's name over the old floor's plan. */
    private val floorWithRooms = floorChoice.flatMapLatest { choice ->
        val id = choice.second
        if (id == null) flowOf(choice to emptyList()) else homes.observePlan(id).map { choice to it.placed }
    }

    val state: StateFlow<ItemPlaceUiState> = combine(floorWithRooms, items.observeItem(itemId)) { (choice, rooms), item ->
        val (floors, floorId, known) = choice
        build(floors, floorId, rooms, item, loading = !known)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemPlaceUiState(itemId = itemId, system = system))

    private fun build(floors: List<FloorSummary>, floorId: String?, rooms: List<com.kurtz.homerecord.housemodel.plan.PlanRoom>, item: ItemEntity?, loading: Boolean): ItemPlaceUiState {
        if (loading) return ItemPlaceUiState(itemId = itemId, system = system)
        if (item == null) return ItemPlaceUiState(loading = false, missing = true, itemId = itemId, system = system)
        val spot = item.roomId?.let { r -> item.planX?.let { x -> item.planY?.let { y -> ItemPlace(r, x, y) } } }
        return ItemPlaceUiState(
            loading = false, itemId = itemId, itemName = item.name,
            iconName = ItemCategory.subtype(item.subtype)?.iconName ?: ItemCategory.fromId(item.category)?.icon ?: "Category",
            floors = floors, floorId = floorId, rooms = rooms, spot = spot, system = system,
        )
    }

    fun onEvent(e: ItemPlaceEvent) {
        when (e) {
            is ItemPlaceEvent.PickFloor -> {
                picked.value = e.floorId
                saved[FLOOR] = e.floorId
            }
            is ItemPlaceEvent.Place -> viewModelScope.launch {
                val place = ItemPlacement.place(state.value.rooms, e.xIn, e.yIn) ?: return@launch
                items.setPlanSpot(itemId, place)
            }
            ItemPlaceEvent.Remove -> viewModelScope.launch { items.setPlanSpot(itemId, null) }
            ItemPlaceEvent.Done, ItemPlaceEvent.EditPlan -> Unit
        }
    }

    private companion object {
        const val FLOOR = "item_place_floor"
    }
}
