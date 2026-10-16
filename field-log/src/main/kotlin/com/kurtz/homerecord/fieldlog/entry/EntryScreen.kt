package com.kurtz.homerecord.fieldlog.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.logic.EntryGrouper
import com.kurtz.homerecord.core.model.EntryKind
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.ui.PhotoImage
import java.io.File

@Composable
fun EntryRoute(viewModel: EntryViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    EntryScreen(state, viewModel.file(), viewModel::onEvent)
}

/** FL-Entry: preview, caption/note text, room tag, delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(state: EntryUiState, file: File?, onEvent: (EntryEvent) -> Unit) {
    val entry = state.entry
    if (entry == null) {
        if (state.loaded) Text(stringResource(R.string.entry_missing), Modifier.padding(16.dp))
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (entry.kind) {
            EntryKind.PHOTO -> PhotoImage(file, stringResource(R.string.entry_photo), Modifier.fillMaxWidth())
            EntryKind.VOICE -> {
                Text(
                    stringResource(R.string.entry_voice_duration, EntryGrouper.formatDuration(entry.durationMs ?: 0L)),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(onClick = { onEvent(EntryEvent.TogglePlay) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(if (state.playing) R.string.entry_stop else R.string.entry_play))
                }
            }
            EntryKind.NOTE -> Unit
        }
        OutlinedTextField(
            value = state.text,
            onValueChange = { onEvent(EntryEvent.TextChanged(it)) },
            label = { Text(stringResource(if (entry.kind == EntryKind.NOTE) R.string.entry_note_label else R.string.entry_caption_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = if (entry.kind == EntryKind.NOTE) 4 else 1,
        )
        if (state.rooms.isNotEmpty()) {
            Text(stringResource(R.string.entry_room), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.roomId == null,
                    onClick = { onEvent(EntryEvent.RoomChosen(null)) },
                    label = { Text(stringResource(R.string.entry_room_none)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                state.rooms.forEach { room ->
                    FilterChip(
                        selected = state.roomId == room.id,
                        onClick = { onEvent(EntryEvent.RoomChosen(room.id)) },
                        label = { Text(room.name) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onEvent(EntryEvent.Save) }, enabled = state.canSave, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.entry_save))
            }
            OutlinedButton(onClick = { onEvent(EntryEvent.Delete) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.entry_delete))
            }
        }
    }
}
