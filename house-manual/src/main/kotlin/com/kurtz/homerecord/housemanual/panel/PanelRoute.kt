package com.kurtz.homerecord.housemanual.panel

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.kurtz.homerecord.core.media.MediaFileStore

/** Wires [PanelViewModel] and the system camera (TakePicture) to [PanelScreen]. */
@Composable
fun PanelRoute(viewModel: PanelViewModel, media: MediaFileStore, onOpenBreaker: (panelId: String, event: PanelEvent.OpenBreaker) -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    // Survives process death while the camera app is in front.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pending
        pending = null
        if (path != null) {
            if (ok) viewModel.onPhotoCaptured(path) else media.delete(path)
        }
    }

    val photoFile = state.panel?.photoPath?.let { media.resolve(it) }
    PanelScreen(state, photoFile = photoFile, onEvent = { event ->
        when (event) {
            PanelEvent.TakePhoto -> {
                val path = media.newPhotoPath()
                val file = media.resolveForWrite(path) ?: return@PanelScreen
                pending = path
                launcher.launch(FileProvider.getUriForFile(context, authority(context), file))
            }
            is PanelEvent.OpenBreaker -> state.panel?.let { onOpenBreaker(it.id, event) } ?: Unit
            else -> viewModel.onEvent(event)
        }
    })
}

private fun authority(context: Context) = "${context.packageName}.fileprovider"
