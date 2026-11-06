package com.kurtz.homerecord.housemanual.panel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import kotlinx.coroutines.flow.map

@Composable
fun PanelRoute(repo: PanelRepository, items: ItemRepository, media: MediaFiles, homeId: String, itemId: String, onBack: () -> Unit, highlightSlot: Int? = null, onMap: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm = viewModel<PanelViewModel>(
        key = "panel:$itemId:${highlightSlot ?: ""}",
        factory = viewModelFactory {
            initializer { PanelViewModel(repo, itemId, items.observeItem(itemId).map { it?.name }, repo.observeChoices(homeId), createSavedStateHandle(), media, highlightSlot, legendExporter = com.kurtz.homerecord.housemodel.pdf.PanelLegendExporter(context.applicationContext)) }
        },
    )
    val state = vm.state.collectAsState().value
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.onEvent(PanelEvent.CameraResult(ok)) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> vm.onEvent(PanelEvent.GalleryResult(uri?.toString())) }
    LaunchedEffect(state.photoLaunch) {
        val launch = state.photoLaunch ?: return@LaunchedEffect
        vm.onEvent(PanelEvent.PhotoLaunchHandled)
        val target = launch.target
        if (target != null) camera.launch(Uri.parse(target.uri)) else gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val shareTitle = androidx.compose.ui.res.stringResource(com.kurtz.homerecord.housemanual.R.string.panel_legend_share_title)
    LaunchedEffect(state.legendUri) {
        val uri = state.legendUri ?: return@LaunchedEffect
        vm.onEvent(PanelEvent.LegendHandled)
        com.kurtz.homerecord.coreui.platform.Intents.shareUri(context, Uri.parse(uri), "application/pdf", shareTitle)
    }
    PanelScreen(state, onEvent = {
        when (it) {
            PanelEvent.Back -> onBack()
            PanelEvent.Map -> onMap()
            else -> vm.onEvent(it)
        }
    })
}
