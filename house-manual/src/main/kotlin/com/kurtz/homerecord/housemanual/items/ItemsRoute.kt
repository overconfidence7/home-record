package com.kurtz.homerecord.housemanual.items

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.items.labelRes

@Composable
fun ItemsRoute(repo: ItemRepository, homeId: String, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    val pseudo = mapOf(
        PseudoRooms.EXTERIOR to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_exterior),
        PseudoRooms.WHOLE_HOUSE to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_whole_house),
    )
    // Application resources: the ViewModel outlives the activity, and search matches the category names on screen.
    val resources = LocalContext.current.applicationContext.resources
    val vm = viewModel<ItemsViewModel>(
        factory = viewModelFactory {
            initializer { ItemsViewModel(repo, homeId, pseudo, createSavedStateHandle()) { resources.getString(it.labelRes) } }
        },
    )
    ItemsScreen(vm.state.collectAsState().value, onEvent = {
        when (it) {
            ItemsEvent.Add -> onAdd()
            is ItemsEvent.Open -> onOpen(it.itemId)
            else -> vm.onEvent(it)
        }
    })
}
