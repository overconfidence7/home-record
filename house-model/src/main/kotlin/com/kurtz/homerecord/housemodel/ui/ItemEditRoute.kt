package com.kurtz.homerecord.housemodel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.items.ItemRepository

@Composable
fun ItemEditRoute(
    items: ItemRepository,
    homes: HomeRepository,
    homeId: String,
    itemId: String?,
    roomId: String?,
    onClose: () -> Unit,
) {
    val vm = viewModel<ItemEditViewModel>(
        factory = viewModelFactory { initializer { ItemEditViewModel(items, homes, homeId, itemId, createSavedStateHandle(), roomId) } },
    )
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.savedItemId) { if (state.savedItemId != null) onClose() }
    ItemEditScreen(state, vm::onEvent, onClose)
}
