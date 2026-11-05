package com.kurtz.homerecord.housemodel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.scene.ItemSpot
import com.kurtz.homerecord.housemodel.walls.RoomWall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Place on plan for item [itemId] (AC-C30 position). [onEditPlan] opens the plan editor on a floor with no rooms drawn. */
@Composable
fun ItemPlaceRoute(
    homes: HomeRepository,
    items: ItemRepository,
    homeId: String,
    itemId: String,
    onBack: () -> Unit,
    onEditPlan: (floorId: String?) -> Unit,
    system: UnitSystem = LocalUnitSystem.current,
    roomWalls: (roomId: String) -> Flow<List<RoomWall>> = { flowOf(emptyList()) },
    homeItems: Flow<List<ItemSpot>> = flowOf(emptyList()),
) {
    val vm = viewModel<ItemPlaceViewModel>(
        key = "item-place:$itemId",
        factory = viewModelFactory { initializer { ItemPlaceViewModel(homes, items, homeId, itemId, createSavedStateHandle(), system, roomWalls, homeItems) } },
    )
    val state = vm.state.collectAsState().value.copy(system = system)
    ItemPlaceScreen(state, onEvent = {
        when (it) {
            ItemPlaceEvent.Done -> onBack()
            ItemPlaceEvent.EditPlan -> onEditPlan(state.floorId)
            else -> vm.onEvent(it)
        }
    })
}
