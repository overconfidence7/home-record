package com.kurtz.homerecord.fieldlog.jobedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun JobEditRoute(viewModel: JobEditViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    JobEditScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobEditScreen(state: JobEditUiState, onEvent: (JobEditEvent) -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (state.isEdit) R.string.jobedit_title_edit else R.string.jobedit_title_new),
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = { onEvent(JobEditEvent.NameChanged(it)) },
            label = { Text(stringResource(R.string.jobedit_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.address,
            onValueChange = { onEvent(JobEditEvent.AddressChanged(it)) },
            label = { Text(stringResource(R.string.jobedit_address)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.clientName,
            onValueChange = { onEvent(JobEditEvent.ClientNameChanged(it)) },
            label = { Text(stringResource(R.string.jobedit_client_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.clientContact,
            onValueChange = { onEvent(JobEditEvent.ClientContactChanged(it)) },
            label = { Text(stringResource(R.string.jobedit_client_contact)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.isEdit) {
            Text(stringResource(R.string.jobedit_rooms_heading), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickRoom.entries.forEach { room ->
                    FilterChip(
                        selected = room in state.selected,
                        onClick = { onEvent(JobEditEvent.ToggleRoom(room)) },
                        label = { Text(stringResource(room.label)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                state.customRooms.forEach { name ->
                    InputChip(
                        selected = true,
                        onClick = { onEvent(JobEditEvent.RemoveCustomRoom(name)) },
                        label = { Text(name) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.customInput,
                    onValueChange = { onEvent(JobEditEvent.CustomInputChanged(it)) },
                    label = { Text(stringResource(R.string.jobedit_custom_room)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { onEvent(JobEditEvent.AddCustomRoom) },
                    enabled = state.canAddCustom,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.jobedit_add)) }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.jobedit_archived))
                Switch(
                    checked = state.archived,
                    onCheckedChange = { onEvent(JobEditEvent.ArchivedChanged(it)) },
                    modifier = Modifier.testTag("jobedit_archived"),
                )
            }
        }
        Button(
            onClick = { onEvent(JobEditEvent.Save) },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.jobedit_save)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun JobEditScreenPreview() {
    HomeRecordTheme {
        JobEditScreen(JobEditUiState(name = "Elm St", selected = setOf(QuickRoom.KITCHEN), customRooms = listOf("Sunroom")), onEvent = {})
    }
}
