package com.kurtz.homerecord.housemanual.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kurtz.homerecord.core.logic.EmergencyGroup
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.rooms.labelRes
import com.kurtz.homerecord.ui.PhotoImage
import java.io.File

/** Minimum instruction type size (AC-30). */
val EmergencyInstructionSize = 24.sp

@Composable
fun EmergencyRoute(viewModel: EmergencyViewModel, media: MediaFileStore, onManage: () -> Unit) {
    val state by viewModel.state.collectAsState()
    EmergencyScreen(state, photoFile = { media.resolve(it) }) { event ->
        when (event) {
            is EmergencyEvent.ZoomPhoto -> viewModel.zoom(event.path)
            EmergencyEvent.CloseZoom -> viewModel.zoom(null)
            EmergencyEvent.ManageShutoffs -> onManage()
        }
    }
}

private fun EmergencyGroup.labelRes(): Int = when (this) {
    EmergencyGroup.WATER -> R.string.emergency_water
    EmergencyGroup.GAS -> R.string.emergency_gas
    EmergencyGroup.ELECTRICAL -> R.string.emergency_electrical
    EmergencyGroup.OTHER -> R.string.emergency_other
}

@Composable
fun EmergencyScreen(state: EmergencyUiState, photoFile: (String) -> File? = { null }, onEvent: (EmergencyEvent) -> Unit) {
    if (!state.loaded) return
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.emergency_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        if (state.groups.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.emergency_empty),
                    fontSize = EmergencyInstructionSize,
                    modifier = Modifier.testTag("emergency_empty"),
                )
            }
        }
        state.groups.forEach { (group, list) ->
            item(key = "h-$group") {
                Text(
                    stringResource(group.labelRes()),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 8.dp).testTag("emergency_group"),
                )
            }
            items(list, key = { it.id }) { shutoff ->
                Column(Modifier.fillMaxWidth().testTag("emergency_item"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val room = shutoff.roomId?.let { state.roomNames[it] }
                    Text(
                        listOfNotNull(stringResource(shutoff.kind.labelRes()), room).joinToString(" · "),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val path = shutoff.photoPath
                    val file = path?.let(photoFile)
                    if (path != null && file != null) {
                        PhotoImage(
                            file,
                            contentDescription = stringResource(R.string.emergency_photo_description),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable { onEvent(EmergencyEvent.ZoomPhoto(path)) }.testTag("emergency_photo"),
                        )
                    }
                    Text(shutoff.instructions, fontSize = EmergencyInstructionSize, lineHeight = 32.sp, modifier = Modifier.testTag("emergency_instructions"))
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { onEvent(EmergencyEvent.ManageShutoffs) },
                modifier = Modifier.heightIn(min = 56.dp).padding(bottom = 16.dp).testTag("emergency_manage"),
            ) { Text(stringResource(R.string.emergency_manage)) }
        }
    }
    val zoomPath = state.zoomedPhotoPath
    val zoomFile = zoomPath?.let(photoFile)
    if (zoomFile != null) ZoomDialog(zoomFile) { onEvent(EmergencyEvent.CloseZoom) }
}

@Composable
private fun ZoomDialog(file: File, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("emergency_zoom"), contentAlignment = Alignment.Center) {
            PhotoImage(
                file,
                contentDescription = stringResource(R.string.emergency_photo_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).heightIn(min = 48.dp).testTag("emergency_zoom_close"),
            ) { Text(stringResource(R.string.emergency_close), color = Color.White) }
        }
    }
}
