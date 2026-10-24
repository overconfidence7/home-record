package com.kurtz.homerecord.housemanual.items

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.upkeep.UpkeepRepository

@Composable
fun ItemDetailRoute(
    repo: ItemRepository,
    lookup: WarrantyLookupRepository,
    upkeep: UpkeepRepository,
    homeId: String,
    itemId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenCopy: (String) -> Unit,
) {
    val pseudo = mapOf(
        PseudoRooms.EXTERIOR to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_exterior),
        PseudoRooms.WHOLE_HOUSE to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_whole_house),
    )
    val suffix = stringResource(R.string.item_copy_suffix)
    val context = LocalContext.current
    val vm = viewModel<ItemDetailViewModel>(
        key = itemId,
        factory = viewModelFactory {
            initializer {
                ItemDetailViewModel(repo, lookup, upkeep, itemId, homeId, repo.observeDetail(itemId, homeId, pseudo), suffix, createSavedStateHandle(), onBack, onOpenCopy)
            }
        },
    )
    ItemDetailScreen(vm.state.collectAsState().value, onEvent = {
        when (it) {
            ItemDetailEvent.Back -> onBack()
            ItemDetailEvent.Edit -> onEdit()
            is ItemDetailEvent.OpenLink -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it.url))) }
            else -> vm.onEvent(it)
        }
    })
}
