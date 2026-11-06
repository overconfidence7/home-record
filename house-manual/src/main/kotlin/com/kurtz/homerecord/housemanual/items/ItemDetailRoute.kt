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
import kotlinx.coroutines.flow.map
import com.kurtz.homerecord.core.lookup.RecallApi
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.coreui.pages.rememberDocumentScanner
import com.kurtz.homerecord.housemodel.documents.DocumentRepository
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.overlays.OverlayLink
import com.kurtz.homerecord.housemodel.overlays.OverlayRepository
import com.kurtz.homerecord.housemodel.panel.FeedingBreaker
import com.kurtz.homerecord.housemodel.panel.PanelRepository
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
    overlays: OverlayRepository? = null,
    /** Opens the wall of a linked overlay with it picked; only overlays on a wall in a room come here. */
    onOpenOverlay: (OverlayLink) -> Unit = {},
    /** Opens Place on plan for this item (AC-C30 position). */
    onPlace: () -> Unit = {},
    /** Opens HM-Panel for a Breaker panel or Sub-panel item (AC-C40). */
    onOpenPanel: () -> Unit = {},
    panels: PanelRepository? = null,
    /** Opens the panel of a breaker that feeds this item, scrolled to its slot (AC-H7). */
    onOpenBreaker: (FeedingBreaker) -> Unit = {},
    /** Opens another item's page: the shutoff that isolates this one (AC-H7). */
    onOpenItem: (String) -> Unit = {},
    /** The rooms for "Move to room" (SPEC HM-Item). */
    roomChoices: suspend () -> List<com.kurtz.homerecord.housemodel.RoomChoice> = { emptyList() },
    /** The room chip under the hero opens the item's room (AC-H7). */
    onOpenRoom: (String) -> Unit = {},
    /** This item's reminders on the page (AC-C49). */
    reminders: com.kurtz.homerecord.housemodel.reminders.ReminderRepository? = null,
) {
    val pseudo = mapOf(
        PseudoRooms.EXTERIOR to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_exterior),
        PseudoRooms.WHOLE_HOUSE to stringResource(com.kurtz.homerecord.housemodel.R.string.item_room_whole_house),
    )
    val suffix = stringResource(R.string.item_copy_suffix)
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val vm = viewModel<ItemDetailViewModel>(
        key = itemId,
        factory = viewModelFactory {
            initializer {
                ItemDetailViewModel(repo, lookup, upkeep, documents, itemId, homeId, repo.observeDetail(itemId, homeId, pseudo), suffix, createSavedStateHandle(), recalls,
                    overlays?.observeLinks(itemId) ?: kotlinx.coroutines.flow.flowOf(emptyList()),
                    panels?.observeFeedingItem(itemId) ?: kotlinx.coroutines.flow.flowOf(emptyList()),
                    repo.observeShutoffFor(itemId), repo.observeShutoffs(homeId, pseudo).map { sections -> sections.flatMap { it.shutoffs } },
                    moveChoices = {
                        roomChoices().map { MoveChoice(it.id, it.name, it.floorName) } +
                            listOf(PseudoRooms.EXTERIOR, PseudoRooms.WHOLE_HOUSE).map { MoveChoice(it, pseudo.getValue(it)) }
                    },
                    reminderRepo = reminders)
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
            is ItemDetailEvent.OpenOverlay -> onOpenOverlay(it.link)
            ItemDetailEvent.Place -> onPlace()
            ItemDetailEvent.OpenPanel -> onOpenPanel()
            is ItemDetailEvent.OpenBreaker -> onOpenBreaker(it.breaker)
            is ItemDetailEvent.OpenShutoff -> onOpenItem(it.id)
            is ItemDetailEvent.OpenRoom -> onOpenRoom(it.roomId)
            ItemDetailEvent.Share -> state.detail?.let { d ->
                val text = ItemShareText.build(d, { id, args -> resources.getString(id, *args) })
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, d.item.name).putExtra(Intent.EXTRA_TEXT, text)
                runCatching { context.startActivity(Intent.createChooser(send, resources.getString(R.string.item_share_chooser))) }
            }
            ItemDetailEvent.UploadDocument -> picker.launch(arrayOf("application/pdf", "image/*"))
            ItemDetailEvent.ScanDocument -> scan()
            is ItemDetailEvent.OpenLink -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it.url))) }
            else -> vm.onEvent(it)
        }
    })
}
