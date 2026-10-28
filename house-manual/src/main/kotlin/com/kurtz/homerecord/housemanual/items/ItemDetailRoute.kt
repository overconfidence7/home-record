package com.kurtz.homerecord.housemanual.items

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.lookup.RecallApi
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.coreui.pages.rememberDocumentScanner
import com.kurtz.homerecord.housemodel.documents.DocumentRepository
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.upkeep.UpkeepRepository

@Composable
fun ItemDetailRoute(
    repo: ItemRepository,
    lookup: WarrantyLookupRepository,
    upkeep: UpkeepRepository,
    documents: DocumentRepository,
    homeId: String,
    itemId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenCopy: (String) -> Unit,
    recalls: RecallApi? = null,
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
                ItemDetailViewModel(repo, lookup, upkeep, documents, itemId, homeId, repo.observeDetail(itemId, homeId, pseudo), suffix, createSavedStateHandle(), recalls)
            }
        },
    )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onEvent(ItemDetailEvent.DocumentPicked(uri.toString()))
    }
    val scan = rememberDocumentScanner(
        onPdf = { vm.onEvent(ItemDetailEvent.DocumentPicked(it)) },
        onFailed = { vm.onEvent(ItemDetailEvent.DocumentScanFailed) },
    )
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.nav) {
        when (val nav = state.nav ?: return@LaunchedEffect) {
            ItemDetailNav.Closed -> onBack()
            is ItemDetailNav.OpenCopy -> onOpenCopy(nav.id)
        }
        vm.onEvent(ItemDetailEvent.NavHandled)
    }
    ItemDetailScreen(state, onEvent = {
        when (it) {
            ItemDetailEvent.Back -> onBack()
            ItemDetailEvent.Edit -> onEdit()
            is ItemDetailEvent.OpenDocument -> onOpenDocument(it.id)
            ItemDetailEvent.UploadDocument -> picker.launch(arrayOf("application/pdf", "image/*"))
            ItemDetailEvent.ScanDocument -> scan()
            is ItemDetailEvent.OpenLink -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it.url))) }
            else -> vm.onEvent(it)
        }
    })
}
