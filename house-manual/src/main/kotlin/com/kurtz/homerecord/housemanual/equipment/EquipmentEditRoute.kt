package com.kurtz.homerecord.housemanual.equipment

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.kurtz.homerecord.core.media.MediaFileStore

/** Wires [EquipmentEditViewModel] and the system camera (AC-48) to [EquipmentEditScreen]. */
@Composable
fun EquipmentEditRoute(viewModel: EquipmentEditViewModel, media: MediaFileStore, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingIsScan by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pending
        val scan = pendingIsScan
        pending = null
        if (path != null) {
            when {
                !ok -> media.delete(path)
                scan -> viewModel.onDataPlateCaptured(path)
                else -> viewModel.onPhotoCaptured(path)
            }
        }
    }

    EquipmentEditScreen(state, photoFile = state.labelPhotoPath?.let { media.resolve(it) }) { event ->
        if (event == EquipmentEditEvent.TakeLabelPhoto || event == EquipmentEditEvent.ScanDataPlate) {
            val path = media.newPhotoPath()
            val file = media.resolveForWrite(path) ?: return@EquipmentEditScreen
            pending = path
            pendingIsScan = event == EquipmentEditEvent.ScanDataPlate
            launcher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } else {
            viewModel.onEvent(event)
        }
    }
}
