package com.kurtz.homerecord.housemodel.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
    subtypeId: String? = null,
    scanPlate: Boolean = false,
    onClose: () -> Unit,
    afterCreate: (String) -> Unit = {},
) {
    val vm = viewModel<ItemEditViewModel>(
        factory = viewModelFactory { initializer { ItemEditViewModel(items, homes, homeId, itemId, createSavedStateHandle(), media, recognizer, vision, roomId, afterCreate, subtypeId) } },
    )
    val state = vm.state.collectAsState().value
    var scanOffered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scanPlate) {
        if (scanPlate && !scanOffered) {
            scanOffered = true
            vm.onEvent(ItemEditEvent.OpenPhotoSheet(PhotoKind.DATA_PLATE))
        }
    }
    LaunchedEffect(state.savedItemId) { if (state.savedItemId != null) onClose() }
    // The pending kind and file live in the view model's SavedStateHandle, so a result after rotation or process death still lands.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.onEvent(ItemEditEvent.CameraResult(ok)) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        vm.onEvent(ItemEditEvent.GalleryResult(uri?.toString()))
    }
    LaunchedEffect(state.photoLaunch) {
        val launch = state.photoLaunch ?: return@LaunchedEffect
        vm.onEvent(ItemEditEvent.PhotoLaunchHandled)
        val target = launch.target
        if (target != null) {
            camera.launch(Uri.parse(target.uri))
        } else {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    ItemEditScreen(state, vm::onEvent, onClose)
}
