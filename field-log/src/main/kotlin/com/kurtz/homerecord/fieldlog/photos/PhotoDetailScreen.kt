package com.kurtz.homerecord.fieldlog.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** FL-Photo: one job photo full width with caption, room and phase tags, share and delete (AC-F25). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoDetailScreen(
    state: PhotoDetailUiState,
    onEvent: (PhotoDetailEvent) -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    ScreenScaffold(
        title = stringResource(R.string.photo_title),
        subtitle = if (state.takenAt > 0) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(Instant.ofEpochMilli(state.takenAt).atZone(zone)) else null,
        onBack = { onEvent(PhotoDetailEvent.Back) },
        actions = {
            if (!state.loading && !state.missing && state.image == PhotoImage.Ready) {
                IconButton({ onEvent(PhotoDetailEvent.Annotate) }, Modifier.testTag("fl_photo_annotate")) { Icon(Icons.Rounded.Edit, stringResource(R.string.photo_annotate)) }
                IconButton({ onEvent(PhotoDetailEvent.Share) }, Modifier.testTag("fl_photo_share")) { Icon(Icons.Rounded.Share, stringResource(R.string.photo_share)) }
            }
            if (!state.loading && !state.missing) {
                if (state.canDelete) {
                    IconButton({ onEvent(PhotoDetailEvent.AskDelete) }, Modifier.testTag("fl_photo_delete")) { Icon(Icons.Rounded.Delete, stringResource(R.string.photo_delete)) }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.missing -> Text(
                stringResource(R.string.photo_missing), Modifier.padding(padding).padding(24.dp).testTag("fl_photo_missing"),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("fl_photo_body"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PhotoFrame(state, onEvent)
                OutlinedTextField(
                    state.caption, { onEvent(PhotoDetailEvent.Caption(it)) },
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("fl_photo_caption").onFocusChanged { if (!it.isFocused) onEvent(PhotoDetailEvent.SaveCaption) },
                    label = { Text(stringResource(R.string.photo_caption)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onEvent(PhotoDetailEvent.SaveCaption) }),
                )
                if (state.rooms.isNotEmpty()) {
                    Text(stringResource(R.string.photo_room), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.rooms.forEach { r ->
                            FilterChip(
                                r.id == state.roomId, { onEvent(PhotoDetailEvent.Room(if (r.id == state.roomId) null else r.id)) },
                                { Text(r.name) }, Modifier.heightIn(min = 48.dp).testTag("fl_photo_room_${r.id}"),
                            )
                        }
                    }
                }
                if (state.phases.isNotEmpty()) {
                    Text(stringResource(R.string.photo_phase), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.phases.forEach { p ->
                            FilterChip(
                                p == state.phase, { onEvent(PhotoDetailEvent.Phase(if (p == state.phase) null else p)) },
                                { Text(p) }, Modifier.heightIn(min = 48.dp).testTag("fl_photo_phase_$p"),
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(state.isBefore, role = Role.Switch) { onEvent(PhotoDetailEvent.ToggleBefore) }
                        .testTag("fl_photo_mark_before"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.photo_mark_before), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.photo_before_hint), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.isBefore, onCheckedChange = null)
                }
            }
        }
    }
    if (state.confirmDelete) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(PhotoDetailEvent.CancelDelete) },
            title = { Text(stringResource(R.string.photo_delete_title)) },
            text = { Text(stringResource(R.string.photo_delete_body)) },
            confirmButton = { TextButton({ onEvent(PhotoDetailEvent.ConfirmDelete) }, Modifier.heightIn(min = 48.dp).testTag("fl_photo_delete_confirm")) { Text(stringResource(R.string.photo_delete)) } },
            dismissButton = { TextButton({ onEvent(PhotoDetailEvent.CancelDelete) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * The photo in a fixed 4:3 frame with a placeholder while it loads and a broken-image fallback (ui-standards rule 27),
 * "Marked up" as a badge on it, and, for a photo pulled from another phone, the download's progress or what to do.
 */
@Composable
private fun PhotoFrame(state: PhotoDetailUiState, onEvent: (PhotoDetailEvent) -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).testTag("fl_photo_image"),
        contentAlignment = Alignment.Center,
    ) {
        if (state.image == PhotoImage.Ready) {
            SubcomposeAsyncImage(
                model = File(state.imagePath), contentDescription = state.caption.ifBlank { null }, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                error = { FrameMessage(Icons.Rounded.BrokenImage, stringResource(R.string.photo_unreadable)) },
            )
        } else {
            val text = when (state.image) {
                PhotoImage.NoNetwork -> R.string.photo_offline
                PhotoImage.NotUploaded -> R.string.photo_not_uploaded
                PhotoImage.Failed -> R.string.photo_download_failed
                else -> R.string.photo_downloading
            }
            FrameMessage(
                if (state.image == PhotoImage.NoNetwork) Icons.Rounded.CloudOff else Icons.Rounded.CloudDownload,
                stringResource(text),
                busy = state.image == PhotoImage.Downloading,
            ) {
                if (state.image != PhotoImage.Downloading) {
                    TextButton({ onEvent(PhotoDetailEvent.RetryDownload) }, Modifier.heightIn(min = 48.dp).testTag("fl_photo_retry")) {
                        Text(stringResource(com.kurtz.homerecord.coreui.R.string.retry))
                    }
                }
            }
        }
        if (state.annotated) {
            Text(
                stringResource(R.string.photos_marked),
                Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("fl_photo_marked"),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun FrameMessage(icon: ImageVector, text: String, busy: Boolean = false, action: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("fl_photo_image_message"),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (busy) CircularProgressIndicator() else Icon(icon, contentDescription = null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action()
    }
}
