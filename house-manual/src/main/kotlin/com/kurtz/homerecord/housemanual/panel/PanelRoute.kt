package com.kurtz.homerecord.housemanual.panel

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import android.content.Intent
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.pdf.LegendHeadings
import com.kurtz.homerecord.core.pdf.LegendPdfWriter
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.breaker.typeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Wires [PanelViewModel] and the system camera (TakePicture) to [PanelScreen]. */
@Composable
fun PanelRoute(viewModel: PanelViewModel, media: MediaFileStore, highlightSlot: Int? = null, onOpenBreaker: (panelId: String, event: PanelEvent.OpenBreaker) -> Unit) {
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

    val scope = rememberCoroutineScope()
    val photoFile = state.panel?.photoPath?.let { media.resolve(it) }
    PanelScreen(state, photoFile = photoFile, highlightSlot = highlightSlot, onEvent = { event ->
        when (event) {
            PanelEvent.TakePhoto -> {
                val path = media.newPhotoPath()
                val file = media.resolveForWrite(path) ?: return@PanelScreen
                pending = path
                launcher.launch(FileProvider.getUriForFile(context, authority(context), file))
            }
            PanelEvent.ShareLegend -> scope.launch { shareLegend(context, viewModel) }
            is PanelEvent.OpenBreaker -> state.panel?.let { onOpenBreaker(it.id, event) } ?: Unit
            else -> viewModel.onEvent(event)
        }
    })
}

private fun authority(context: Context) = "${context.packageName}.fileprovider"

/** AC-22: writes the legend to cache/shared and opens the system share sheet. */
private suspend fun shareLegend(context: Context, viewModel: PanelViewModel) {
    val rows = viewModel.legendRows { context.getString(typeLabel(it.type)) }
    val headings = LegendHeadings(
        title = context.getString(R.string.legend_title),
        slot = context.getString(R.string.legend_col_slot),
        label = context.getString(R.string.legend_col_label),
        amps = context.getString(R.string.legend_col_amps),
        poles = context.getString(R.string.legend_col_poles),
        type = context.getString(R.string.legend_col_type),
        rooms = context.getString(R.string.legend_col_rooms),
    )
    val file = File(context.cacheDir, "shared/legend.pdf")
    withContext(Dispatchers.IO) { LegendPdfWriter.write(file, headings, rows) }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, authority(context), file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.panel_legend_pdf)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
