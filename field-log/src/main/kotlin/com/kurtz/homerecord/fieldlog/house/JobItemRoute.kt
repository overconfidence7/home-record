package com.kurtz.homerecord.fieldlog.house

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
import kotlinx.coroutines.flow.Flow

/** Wires FL-Item to the shared item and warranty repositories (AC-F50). */
@Composable
fun JobItemRoute(
    items: ItemRepository,
    lookup: WarrantyLookupRepository,
    homeId: String,
    itemId: String,
    canEdit: Flow<Boolean>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPlace: () -> Unit,
) {
    val pseudo = mapOf(
        PseudoRooms.EXTERIOR to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_exterior),
        PseudoRooms.WHOLE_HOUSE to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_whole_house),
    )
    val context = LocalContext.current
    val vm = viewModel<JobItemViewModel>(
        key = "job-item:$itemId",
        factory = viewModelFactory {
            initializer { JobItemViewModel(items, lookup, itemId, items.observeDetail(itemId, homeId, pseudo), canEdit, createSavedStateHandle()) }
        },
    )
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.closed) {
        if (state.closed) {
            vm.onEvent(JobItemEvent.ClosedHandled)
            onBack()
        }
    }
    JobItemScreen(state, onEvent = { e ->
        when (e) {
            JobItemEvent.Back -> onBack()
            JobItemEvent.Edit -> onEdit()
            JobItemEvent.Place -> onPlace()
            is JobItemEvent.OpenLink -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, e.url.toUri())) }
            else -> vm.onEvent(e)
        }
    })
}
