package com.kurtz.homerecord.fieldlog.capture

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.kurtz.homerecord.coreui.components.windowTestTags
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.photo.LensChoice
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import androidx.compose.ui.draw.alpha
import com.kurtz.homerecord.fieldlog.R

/**
 * FL-Capture: camera, room and phase chips, 80 dp shutter, flash, flip, gallery (AC-F24, AC-F29).
 * In landscape the viewfinder takes the start side at full height and the controls an end column.
 * [camera] draws the live preview; previews and tests pass a plain box.
 */
@Composable
fun CaptureScreen(
    state: CaptureUiState,
    onEvent: (CaptureEvent) -> Unit,
    onBack: () -> Unit,
    onPickGallery: () -> Unit,
    camera: @Composable (Modifier) -> Unit,
) {
    if (state.noteOpen) NoteDialog(state, onEvent)
    ScreenScaffold(title = stringResource(R.string.cap_title), subtitle = state.jobName.ifBlank { null }, onBack = onBack) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.access == CameraAccess.Denied || state.lens == LensChoice.None ->
                NoCamera(state, onEvent, onPickGallery, Modifier.padding(padding))
            else -> {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                val keys = Modifier.focusRequester(focus).focusTarget()
                    .onPreviewKeyEvent { e ->
                        val volume = e.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_VOLUME_UP || e.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_VOLUME_DOWN
                        if (volume && e.type == KeyEventType.KeyUp) onEvent(CaptureEvent.Shutter)
                        volume
                    }
                BoxWithConstraints(Modifier.padding(padding).fillMaxSize().then(keys)) {
                    if (maxWidth > maxHeight) {
                        // Landscape: the viewfinder keeps the full height on the start side, as the system camera does;
                        // chips and Note/Voice scroll in an end column with the shutter pinned at its foot.
                        val side = (maxWidth * 0.4f).coerceIn(280.dp, 360.dp)
                        Row(Modifier.fillMaxSize()) {
                            Viewfinder(state, onEvent, camera, Modifier.weight(1f).fillMaxHeight())
                            Column(Modifier.width(side).fillMaxHeight().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Controls(state, onEvent)
                                }
                                ShutterRow(state, onEvent, onPickGallery)
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Viewfinder(state, onEvent, camera, Modifier.weight(1f).fillMaxWidth())
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Controls(state, onEvent)
                                ShutterRow(state, onEvent, onPickGallery)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Viewfinder(state: CaptureUiState, onEvent: (CaptureEvent) -> Unit, camera: @Composable (Modifier) -> Unit, modifier: Modifier) {
    Box(modifier.background(Color.Black).testTag("fl_capture_viewfinder")) {
        camera(Modifier.fillMaxSize())
        state.ghost?.takeIf { state.ghostOn }?.let {
            if (it.path.isEmpty()) {
                Text(
                    stringResource(R.string.cap_ghost_downloading),
                    Modifier.align(Alignment.Center).padding(16.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("fl_capture_ghost_waiting"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                coil3.compose.AsyncImage(
                    model = java.io.File(it.path), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.3f).testTag("fl_capture_ghost"),
                )
            }
        }
        state.ghost?.let {
            FilterChip(
                selected = state.ghostOn, onClick = { onEvent(CaptureEvent.ToggleGhost) },
                label = { Text(stringResource(R.string.cap_ghost)) },
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).heightIn(min = 56.dp).testTag("fl_capture_ghost_toggle"),
            )
        }
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            IconButton(onClick = { onEvent(CaptureEvent.CycleFlash) }, Modifier.size(56.dp).testTag("fl_capture_flash")) {
                Icon(
                    when (state.flash) { FlashMode.Off -> Icons.Rounded.FlashOff; FlashMode.Auto -> Icons.Rounded.FlashAuto; FlashMode.On -> Icons.Rounded.FlashOn },
                    stringResource(
                        when (state.flash) { FlashMode.Off -> R.string.cap_flash_off; FlashMode.Auto -> R.string.cap_flash_auto; FlashMode.On -> R.string.cap_flash_on },
                    ),
                    tint = Color.White,
                )
            }
            if (state.canFlip) {
                IconButton(onClick = { onEvent(CaptureEvent.Flip) }, Modifier.size(56.dp).testTag("fl_capture_flip")) {
                    Icon(Icons.Rounded.Cameraswitch, stringResource(R.string.cap_flip), tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(state: CaptureUiState, onEvent: (CaptureEvent) -> Unit) {
    if (state.rooms.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.rooms.forEach { r ->
                FilterChip(
                    selected = state.roomId == r.id,
                    onClick = { onEvent(CaptureEvent.SelectRoom(if (state.roomId == r.id) null else r.id)) },
                    label = { Text(r.name) }, modifier = Modifier.testTag("fl_capture_room_${r.id}"),
                )
            }
        }
    }
    if (state.phases.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.phases.forEach { p ->
                FilterChip(
                    selected = state.phase == p,
                    onClick = { onEvent(CaptureEvent.SelectPhase(if (state.phase == p) null else p)) },
                    label = { Text(p) }, modifier = Modifier.testTag("fl_capture_phase_$p"),
                )
            }
        }
    }
    state.message?.let {
        Text(stringResource(messageText(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("fl_capture_message"))
    }
    NoteVoiceRow(state, onEvent)
}

@Composable
private fun ShutterRow(state: CaptureUiState, onEvent: (CaptureEvent) -> Unit, onPickGallery: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = onPickGallery, Modifier.size(56.dp).testTag("fl_capture_gallery")) {
            Icon(Icons.Rounded.PhotoLibrary, stringResource(R.string.cap_gallery))
        }
        Shutter(state.saving) { onEvent(CaptureEvent.Shutter) }
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (state.savedCount > 0) {
                Text(
                    state.savedCount.toString(), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("fl_capture_count"),
                )
            }
        }
    }
}

@Composable
private fun NoteVoiceRow(state: CaptureUiState, onEvent: (CaptureEvent) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { onEvent(CaptureEvent.OpenNote) }, enabled = !state.recording,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("fl_capture_note"),
        ) {
            Icon(Icons.Rounded.EditNote, null)
            Text(stringResource(R.string.cap_note), Modifier.padding(start = 8.dp))
        }
        OutlinedButton(
            onClick = { onEvent(CaptureEvent.VoiceTap) },
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("fl_capture_voice"),
        ) {
            Icon(if (state.recording) Icons.Rounded.Stop else Icons.Rounded.Mic, null)
            Text(
                if (state.recording) stringResource(R.string.cap_recording, "%d:%02d".format(state.recordedSeconds / 60, state.recordedSeconds % 60))
                else stringResource(R.string.cap_voice),
                Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun Shutter(busy: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.primary
    val label = stringResource(R.string.cap_shutter)
    IconButton(
        onClick = onClick, enabled = !busy,
        modifier = Modifier.size(80.dp).border(4.dp, ring, CircleShape).testTag("fl_capture_shutter").semantics { contentDescription = label },
    ) {
        Box(Modifier.size(60.dp).background(ring, CircleShape))
    }
}

private fun messageText(m: CaptureMessage): Int = when (m) {
    CaptureMessage.SaveFailed -> R.string.cap_save_failed
    CaptureMessage.JobGone -> R.string.cap_job_gone
    CaptureMessage.NoCamera -> R.string.cap_no_camera
    CaptureMessage.NoteSaved -> R.string.cap_note_saved
    CaptureMessage.MemoSaved -> R.string.cap_memo_saved
    CaptureMessage.MemoFailed -> R.string.cap_memo_failed
    CaptureMessage.MicDenied -> R.string.cap_mic_denied
}

@Composable
private fun NoteDialog(state: CaptureUiState, onEvent: (CaptureEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(CaptureEvent.CloseNote) },
        title = { Text(stringResource(R.string.cap_note_title)) },
        text = {
            OutlinedTextField(
                value = state.noteText, onValueChange = { onEvent(CaptureEvent.NoteText(it)) },
                placeholder = { Text(stringResource(R.string.cap_note_hint)) }, minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("fl_capture_note_text"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(CaptureEvent.SaveNote) }, Modifier.heightIn(min = 56.dp).testTag("fl_capture_note_save")) {
                Text(stringResource(R.string.cap_note_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(CaptureEvent.CloseNote) }, Modifier.heightIn(min = 56.dp).testTag("fl_capture_note_cancel")) {
                Text(stringResource(R.string.cap_note_cancel))
            }
        },
        modifier = Modifier.windowTestTags(),
    )
}

/** AC-F29: say why, link to Settings, and still offer the gallery so the job can get photos. */
@Composable
private fun NoCamera(state: CaptureUiState, onEvent: (CaptureEvent) -> Unit, onPickGallery: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text(
            stringResource(if (state.access == CameraAccess.Denied) R.string.cap_denied else R.string.cap_no_camera),
            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("fl_capture_denied"),
        )
        if (state.access == CameraAccess.Denied) {
            Button(onClick = { onEvent(CaptureEvent.OpenSettings) }, Modifier.fillMaxWidth().padding(0.dp).testTag("fl_capture_settings")) {
                Text(stringResource(R.string.cap_open_settings))
            }
        }
        OutlinedButton(onClick = onPickGallery, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("fl_capture_gallery")) {
            Text(stringResource(R.string.cap_from_gallery))
        }
        state.message?.let {
            Text(stringResource(messageText(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("fl_capture_message"))
        }
        NoteVoiceRow(state, onEvent)
    }
}
