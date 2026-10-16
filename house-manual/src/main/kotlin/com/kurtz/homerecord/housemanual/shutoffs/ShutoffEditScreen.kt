package com.kurtz.homerecord.housemanual.shutoffs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.ShutoffKind
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.rooms.labelRes
import com.kurtz.homerecord.ui.PhotoTile
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShutoffEditScreen(state: ShutoffEditUiState, photoFile: File?, onEvent: (ShutoffEditEvent) -> Unit) {
    if (!state.loaded) return
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("shutoff_edit_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (state.isNew) R.string.shutoff_edit_new else R.string.shutoff_edit_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(stringResource(R.string.shutoff_kind), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShutoffKind.entries.forEach { kind ->
                FilterChip(
                    selected = state.kind == kind,
                    onClick = { onEvent(ShutoffEditEvent.KindChanged(kind)) },
                    label = { Text(stringResource(kind.labelRes())) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("shutoff_kind_${kind.name}"),
                )
            }
        }
        if (state.rooms.isNotEmpty()) {
            Text(stringResource(R.string.shutoff_room), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.roomId == null,
                    onClick = { onEvent(ShutoffEditEvent.RoomChanged(null)) },
                    label = { Text(stringResource(R.string.shutoff_no_room)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                state.rooms.forEach { room ->
                    FilterChip(
                        selected = state.roomId == room.id,
                        onClick = { onEvent(ShutoffEditEvent.RoomChanged(room.id)) },
                        label = { Text(room.name) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("shutoff_room_chip"),
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.instructions,
            onValueChange = { onEvent(ShutoffEditEvent.InstructionsChanged(it)) },
            label = { Text(stringResource(R.string.shutoff_instructions)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("shutoff_instructions"),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.photoPath != null) PhotoTile(photoFile, contentDescription = stringResource(R.string.shutoff_photo), size = 96.dp)
            OutlinedButton(
                onClick = { onEvent(ShutoffEditEvent.TakePhoto) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("shutoff_take_photo"),
            ) { Text(stringResource(if (state.photoPath == null) R.string.shutoff_take_photo else R.string.shutoff_retake_photo)) }
            if (state.photoPath != null) {
                TextButton(onClick = { onEvent(ShutoffEditEvent.RemovePhoto) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.shutoff_remove_photo))
                }
            }
        }
        Button(
            onClick = { onEvent(ShutoffEditEvent.Save) },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("shutoff_save"),
        ) { Text(stringResource(R.string.save)) }
        if (!state.isNew) {
            TextButton(
                onClick = { onEvent(ShutoffEditEvent.AskDelete) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("shutoff_delete"),
            ) { Text(stringResource(R.string.shutoff_delete)) }
        }
    }
    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(ShutoffEditEvent.CancelDelete) },
            title = { Text(stringResource(R.string.shutoff_delete_confirm_title)) },
            text = { Text(stringResource(R.string.shutoff_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(ShutoffEditEvent.ConfirmDelete) }, modifier = Modifier.testTag("shutoff_delete_confirm")) {
                    Text(stringResource(R.string.shutoff_delete))
                }
            },
            dismissButton = { TextButton(onClick = { onEvent(ShutoffEditEvent.CancelDelete) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ShutoffEditScreenPreview() {
    HomeRecordTheme { ShutoffEditScreen(ShutoffEditUiState(loaded = true, kind = ShutoffKind.GAS_MAIN, instructions = "Quarter turn"), null, {}) }
}
