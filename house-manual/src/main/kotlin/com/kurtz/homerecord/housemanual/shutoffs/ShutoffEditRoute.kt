package com.kurtz.homerecord.housemanual.shutoffs

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

/** Wires [ShutoffEditViewModel] and the system camera (AC-27) to [ShutoffEditScreen]. */
@Composable
fun ShutoffEditRoute(viewModel: ShutoffEditViewModel, media: MediaFileStore, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pending
        pending = null
        if (path != null) {
            if (ok) viewModel.onPhotoCaptured(path) else media.delete(path)
        }
    }

    ShutoffEditScreen(state, photoFile = state.photoPath?.let { media.resolve(it) }) { event ->
        if (event == ShutoffEditEvent.TakePhoto) {
            val path = media.newPhotoPath()
            val file = media.resolveForWrite(path) ?: return@ShutoffEditScreen
            pending = path
            launcher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } else {
            viewModel.onEvent(event)
        }
    }
}
