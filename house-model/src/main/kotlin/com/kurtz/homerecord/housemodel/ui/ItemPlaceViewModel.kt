package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.Reloadable
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.defaultFloor
import com.kurtz.homerecord.housemodel.items.ItemPlace
import com.kurtz.homerecord.housemodel.items.ItemPlacement
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.scene.ItemSpot
import com.kurtz.homerecord.housemodel.walls.RoomWall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
    /** The walls of a room with their pictures ([com.kurtz.homerecord.housemodel.walls.WallPhotoRepository.observeRoomWalls]). */
    private val roomWalls: (roomId: String) -> Flow<List<RoomWall>> = { flowOf(emptyList()) },
    /** The home's placed items ([com.kurtz.homerecord.housemodel.overlays.OverlayRepository.observeItemSpots]), drawn faintly. */
    private val homeItems: Flow<List<ItemSpot>> = flowOf(emptyList()),
) : ViewModel() {
    private val picked = MutableStateFlow<String?>(saved[FLOOR])
    private val wallMode = MutableStateFlow(saved[WALL_MODE] ?: false)
    private val pickedWall = MutableStateFlow<String?>(saved[WALL])
    /** The marker under the finger during a drag; kept until the drop is saved so it never jumps back first. */
    private val dragAt = MutableStateFlow<OverlayPoint?>(null)

    /** The floor of the item's room as first seen, so the screen opens where the item is. */
    private val itemFloor = MutableStateFlow<String?>(null)
    private val itemFloorKnown = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            try {
                val roomId = items.get(itemId)?.roomId
                itemFloor.value = roomId?.let { homes.roomLocation(it)?.floorId }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The plan read below fails the same way and shows the error state; without the item's floor the screen opens on the default one.
            } finally {
                itemFloorKnown.value = true
            }
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

    private val base = combine(floorWithRooms, items.observeItem(itemId)) { (choice, rooms), item ->
        val (floors, floorId, known) = choice
        build(floors, floorId, rooms, item, loading = !known) to item
    }

    /** The plan state with the walls of the item's room when that room is on the shown floor. */
    private val withWalls = base.flatMapLatest { (s, item) ->
        val roomId = item?.roomId?.takeIf { r -> s.rooms.any { it.id == r } }
        if (roomId == null) flowOf(s) else roomWalls(roomId).map { walls -> s.copy(walls = walls) }
    }

    /** A failed read shows the error state with Retry instead of ending the ViewModel (AC-U4). */
    private val load = Reloadable("Item place")

    private val loaded = load.wrap(ItemPlaceUiState(loading = false, loadFailed = true, itemId = itemId, system = system)) { withWalls }

    val state: StateFlow<ItemPlaceUiState> = combine(loaded, wallMode, pickedWall, homeItems, dragAt) { s, mode, wall, others, drag ->
        val on = mode && s.canHang
        s.copy(
            wallMode = on,
            wallId = wall?.takeIf { w -> s.walls.any { it.id == w } } ?: s.hungOn?.wallId?.takeIf { w -> s.walls.any { it.id == w } } ?: s.walls.firstOrNull()?.id,
            others = if (s.loading || s.missing || s.loadFailed) emptyList() else others,
            dragAt = drag,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemPlaceUiState(itemId = itemId, system = system))

    private fun build(floors: List<FloorSummary>, floorId: String?, rooms: List<com.kurtz.homerecord.housemodel.plan.PlanRoom>, item: ItemEntity?, loading: Boolean): ItemPlaceUiState {
        if (loading) return ItemPlaceUiState(itemId = itemId, system = system)
        if (item == null) return ItemPlaceUiState(loading = false, missing = true, itemId = itemId, system = system)
        val spot = item.roomId?.let { r -> item.planX?.let { x -> item.planY?.let { y -> ItemPlace(r, x, y) } } }
        val hung = item.wallId?.let { w -> item.wallX?.let { x -> item.wallY?.let { y -> WallSpot(w, x, y) } } }
        return ItemPlaceUiState(
            loading = false, itemId = itemId, itemName = item.name,
            iconName = ItemCategory.subtype(item.subtype)?.iconName ?: ItemCategory.fromId(item.category)?.icon ?: "Category",
            floors = floors, floorId = floorId, rooms = rooms, spot = spot, system = system, hungOn = hung,
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
            is ItemPlaceEvent.Drag -> dragAt.value = OverlayPoint(e.xIn.toFloat(), e.yIn.toFloat())
            is ItemPlaceEvent.Drop -> viewModelScope.launch {
                try {
                    ItemPlacement.place(state.value.rooms, e.xIn, e.yIn)?.let { items.setPlanSpot(itemId, it) }
                } finally {
                    dragAt.value = null
                }
            }
            ItemPlaceEvent.Remove -> viewModelScope.launch { items.setPlanSpot(itemId, null) }
            is ItemPlaceEvent.WallMode -> {
                wallMode.value = e.on
                saved[WALL_MODE] = e.on
            }
            is ItemPlaceEvent.PickWall -> {
                pickedWall.value = e.wallId
                saved[WALL] = e.wallId
            }
            is ItemPlaceEvent.HangOnWall -> viewModelScope.launch { items.setWallSpot(itemId, e.wallId, e.x, e.y) }
            ItemPlaceEvent.Retry -> load.retry()
            ItemPlaceEvent.Done, ItemPlaceEvent.EditPlan -> Unit
        }
    }

    private companion object {
        const val FLOOR = "item_place_floor"
        const val WALL_MODE = "item_place_wall_mode"
        const val WALL = "item_place_wall"
    }
}
