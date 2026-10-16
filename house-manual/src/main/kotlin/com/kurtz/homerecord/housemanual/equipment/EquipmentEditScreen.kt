package com.kurtz.homerecord.housemanual.equipment

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
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.rooms.labelRes
import com.kurtz.homerecord.ui.PhotoTile
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EquipmentEditScreen(state: EquipmentEditUiState, photoFile: File?, onEvent: (EquipmentEditEvent) -> Unit) {
    if (!state.loaded) return
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("equipment_edit_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (state.isNew) R.string.equipment_edit_new else R.string.equipment_edit_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(stringResource(R.string.equipment_category), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EquipmentCategory.entries.forEach { category ->
                FilterChip(
                    selected = state.category == category,
                    onClick = { onEvent(EquipmentEditEvent.CategoryChanged(category)) },
                    label = { Text(stringResource(category.labelRes())) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("equipment_category_${category.name}"),
                )
            }
        }
        if (state.rooms.isNotEmpty()) {
            Text(stringResource(R.string.equipment_room), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.roomId == null,
                    onClick = { onEvent(EquipmentEditEvent.RoomChanged(null)) },
                    label = { Text(stringResource(R.string.shutoff_no_room)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                state.rooms.forEach { room ->
                    FilterChip(
                        selected = state.roomId == room.id,
                        onClick = { onEvent(EquipmentEditEvent.RoomChanged(room.id)) },
                        label = { Text(room.name) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("equipment_room_chip"),
                    )
                }
            }
        }
        TextField(state.make, R.string.equipment_make, "equipment_make") { onEvent(EquipmentEditEvent.MakeChanged(it)) }
        TextField(state.model, R.string.equipment_model, "equipment_model") { onEvent(EquipmentEditEvent.ModelChanged(it)) }
        TextField(state.serial, R.string.equipment_serial, "equipment_serial") { onEvent(EquipmentEditEvent.SerialChanged(it)) }
        TextField(state.installedOn, R.string.equipment_installed_on, "equipment_installed_on", !state.installedOnValid) {
            onEvent(EquipmentEditEvent.InstalledOnChanged(it))
        }
        TextField(state.manufacturedOn, R.string.equipment_manufactured_on, "equipment_manufactured_on", !state.manufacturedOnValid) {
            onEvent(EquipmentEditEvent.ManufacturedOnChanged(it))
        }
        TextField(state.warrantyEnds, R.string.equipment_warranty_ends, "equipment_warranty_ends", !state.warrantyEndsValid) {
            onEvent(EquipmentEditEvent.WarrantyEndsChanged(it))
        }
        TextField(state.notes, R.string.equipment_notes, "equipment_notes", minLines = 3) { onEvent(EquipmentEditEvent.NotesChanged(it)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.labelPhotoPath != null) {
                PhotoTile(photoFile, contentDescription = stringResource(R.string.equipment_label_photo), size = 96.dp, modifier = Modifier.testTag("equipment_label_photo"))
            }
            OutlinedButton(
                onClick = { onEvent(EquipmentEditEvent.TakeLabelPhoto) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("equipment_take_photo"),
            ) { Text(stringResource(if (state.labelPhotoPath == null) R.string.equipment_take_photo else R.string.equipment_retake_photo)) }
            if (state.labelPhotoPath != null) {
                TextButton(onClick = { onEvent(EquipmentEditEvent.RemoveLabelPhoto) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.shutoff_remove_photo))
                }
            }
        }
        Button(
            onClick = { onEvent(EquipmentEditEvent.Save) },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("equipment_save"),
        ) { Text(stringResource(R.string.save)) }
        if (!state.isNew) {
            TextButton(
                onClick = { onEvent(EquipmentEditEvent.AskDelete) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("equipment_delete"),
            ) { Text(stringResource(R.string.shutoff_delete)) }
        }
    }
    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(EquipmentEditEvent.CancelDelete) },
            title = { Text(stringResource(R.string.equipment_delete_confirm_title)) },
            text = { Text(stringResource(R.string.equipment_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(EquipmentEditEvent.ConfirmDelete) }, modifier = Modifier.testTag("equipment_delete_confirm")) {
                    Text(stringResource(R.string.shutoff_delete))
                }
            },
            dismissButton = { TextButton(onClick = { onEvent(EquipmentEditEvent.CancelDelete) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TextField(
    value: String,
    label: Int,
    tag: String,
    isError: Boolean = false,
    minLines: Int = 1,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = isError,
        supportingText = if (isError) ({ Text(stringResource(R.string.equipment_date_error)) }) else null,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Preview(showBackground = true)
@Composable
private fun EquipmentEditScreenPreview() {
    HomeRecordTheme { EquipmentEditScreen(EquipmentEditUiState(loaded = true, category = EquipmentCategory.FURNACE, make = "Carrier"), null) {} }
}
