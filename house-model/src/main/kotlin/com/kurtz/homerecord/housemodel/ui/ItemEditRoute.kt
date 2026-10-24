package com.kurtz.homerecord.housemodel.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.core.ocr.TextRecognizer
import com.kurtz.homerecord.core.lookup.DataPlateVisionApi
import com.kurtz.homerecord.housemodel.items.PhotoKind
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
    media: MediaFiles,
    recognizer: TextRecognizer,
    vision: DataPlateVisionApi?,
    homeId: String,
    itemId: String?,
    roomId: String?,
    onClose: () -> Unit,
    afterCreate: (String) -> Unit = {},
) {
    val vm = viewModel<ItemEditViewModel>(
        factory = viewModelFactory { initializer { ItemEditViewModel(items, homes, homeId, itemId, createSavedStateHandle(), media, recognizer, vision, roomId, afterCreate) } },
    )
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.savedItemId) { if (state.savedItemId != null) onClose() }
    var pending by remember { mutableStateOf<Pair<PhotoKind, String>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pending?.let { (kind, path) -> if (ok) vm.onEvent(ItemEditEvent.PhotoCaptured(kind, path)) else media.discard(path) }
        pending = null
    }
    var galleryKind by remember { mutableStateOf(PhotoKind.ITEM) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) vm.onEvent(ItemEditEvent.PhotoPicked(galleryKind, uri.toString()))
    }
    LaunchedEffect(state.photoLaunch) {
        val launch = state.photoLaunch ?: return@LaunchedEffect
        vm.onEvent(ItemEditEvent.PhotoLaunchHandled)
        val target = launch.target
        if (target != null) {
            pending = launch.kind to target.path
            camera.launch(Uri.parse(target.uri))
        } else {
            galleryKind = launch.kind
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    ItemEditScreen(state, vm::onEvent, onClose)
}
