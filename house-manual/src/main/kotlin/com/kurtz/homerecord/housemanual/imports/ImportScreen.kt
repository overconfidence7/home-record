package com.kurtz.homerecord.housemanual.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.imports.ConflictChoice
import com.kurtz.homerecord.core.imports.ImportConflict
import com.kurtz.homerecord.core.imports.ImportEntityType
import com.kurtz.homerecord.core.imports.ImportResult
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun ImportRoute(viewModel: ImportViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onEvent(ImportEvent.FileChosen(uri.toString()))
    }
    ImportScreen(
        state,
        onPick = { picker.launch(arrayOf("*/*")) },
        onEvent = { if (it === ImportEvent.Reset && state.phase == ImportPhase.DONE) onDone() else viewModel.onEvent(it) },
    )
}

@Composable
fun ImportScreen(state: ImportUiState, onPick: () -> Unit, onEvent: (ImportEvent) -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.import_title), style = MaterialTheme.typography.headlineMedium)
        when (state.phase) {
            ImportPhase.PICK -> PickButton(onPick)
            ImportPhase.READING -> {
                CircularProgressIndicator(Modifier.testTag("import_progress"))
                Text(stringResource(R.string.import_reading))
            }
            ImportPhase.PREVIEW -> Preview(state, onEvent)
            ImportPhase.DONE -> Done(state.result, onEvent)
            ImportPhase.ERROR -> {
                Text(stringResource(R.string.import_error_unreadable), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_error"))
                PickButton(onPick)
            }
            ImportPhase.NEEDS_PASSPHRASE -> PassphrasePrompt(state.wrongPassphrase, onEvent)
        }
    }
}

@Composable
private fun PassphrasePrompt(wrong: Boolean, onEvent: (ImportEvent) -> Unit) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.import_encrypted_prompt), modifier = Modifier.testTag("import_encrypted"))
    OutlinedTextField(
        value = passphrase,
        onValueChange = { passphrase = it },
        label = { Text(stringResource(R.string.backup_passphrase)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = wrong,
        supportingText = if (wrong) ({ Text(stringResource(R.string.import_wrong_passphrase)) }) else null,
        modifier = Modifier.fillMaxWidth().testTag("import_passphrase"),
    )
    Button(
        onClick = { onEvent(ImportEvent.PassphraseEntered(passphrase)) },
        enabled = passphrase.isNotEmpty(),
        modifier = Modifier.heightIn(min = 48.dp).testTag("import_unlock"),
    ) { Text(stringResource(R.string.import_unlock)) }
}

@Composable
private fun PickButton(onPick: () -> Unit) {
    Button(onClick = onPick, modifier = Modifier.heightIn(min = 48.dp).testTag("import_pick")) {
        Text(stringResource(R.string.import_pick))
    }
}

@Composable
private fun Preview(state: ImportUiState, onEvent: (ImportEvent) -> Unit) {
    if (state.restoresHome) Text(stringResource(R.string.import_restore_note), modifier = Modifier.testTag("import_restore_note"))
    if (state.nothingToImport) {
        Text(stringResource(R.string.import_nothing_new), modifier = Modifier.testTag("import_nothing_new"))
    } else {
        if (state.counts.isNotEmpty()) {
            Text(stringResource(R.string.import_preview_title), style = MaterialTheme.typography.titleMedium)
            state.counts.forEach { (type, count) ->
                Text("${stringResource(type.labelRes())}: $count", modifier = Modifier.testTag("import_count_${type.name}"))
            }
        }
        if (state.conflicts.isNotEmpty()) {
            Text(stringResource(R.string.import_conflicts_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.import_conflicts_hint), style = MaterialTheme.typography.bodyMedium)
            state.conflicts.forEach { ConflictRow(it, state.choiceFor(it.id), onEvent) }
        }
    }
    if (state.alreadyPresent > 0) {
        Text(pluralStringResource(R.plurals.import_already_present, state.alreadyPresent, state.alreadyPresent), style = MaterialTheme.typography.bodyMedium)
    }
    Button(
        onClick = { onEvent(ImportEvent.Confirm) },
        enabled = !state.nothingToImport && !state.applying,
        modifier = Modifier.heightIn(min = 48.dp).testTag("import_confirm"),
    ) { Text(stringResource(R.string.import_button)) }
}

@Composable
private fun ConflictRow(conflict: ImportConflict, choice: ConflictChoice, onEvent: (ImportEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("import_conflict_${conflict.id}")) {
        Text("${stringResource(conflict.type.labelRes())}: ${conflict.label}", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = choice == ConflictChoice.KEEP_MINE,
                onClick = { onEvent(ImportEvent.Choose(conflict.id, ConflictChoice.KEEP_MINE)) },
                label = { Text(stringResource(R.string.import_keep_mine)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            FilterChip(
                selected = choice == ConflictChoice.USE_IMPORTED,
                onClick = { onEvent(ImportEvent.Choose(conflict.id, ConflictChoice.USE_IMPORTED)) },
                label = { Text(stringResource(R.string.import_use_imported)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("import_use_${conflict.id}"),
            )
        }
    }
}

@Composable
private fun Done(result: ImportResult?, onEvent: (ImportEvent) -> Unit) {
    Text(pluralStringResource(R.plurals.import_done, result?.inserted ?: 0, result?.inserted ?: 0), modifier = Modifier.testTag("import_done"))
    if ((result?.replaced ?: 0) > 0) Text(stringResource(R.string.import_done_replaced, result?.replaced ?: 0))
    if ((result?.keptMine ?: 0) > 0) Text(stringResource(R.string.import_done_kept, result?.keptMine ?: 0))
    Button(onClick = { onEvent(ImportEvent.Reset) }, modifier = Modifier.heightIn(min = 48.dp).testTag("import_back")) {
        Text(stringResource(R.string.import_done_open_home))
    }
}

private fun ImportEntityType.labelRes(): Int = when (this) {
    ImportEntityType.ROOM -> R.string.import_type_room
    ImportEntityType.WALL -> R.string.import_type_wall
    ImportEntityType.PANEL -> R.string.import_type_panel
    ImportEntityType.BREAKER -> R.string.import_type_breaker
    ImportEntityType.BREAKER_ROOM -> R.string.import_type_breaker_room
    ImportEntityType.ENDPOINT -> R.string.import_type_endpoint
    ImportEntityType.SHUTOFF -> R.string.import_type_shutoff
    ImportEntityType.EQUIPMENT -> R.string.import_type_equipment
    ImportEntityType.PHOTO -> R.string.import_type_photo
}

@Preview(showBackground = true)
@Composable
private fun ImportScreenPreview() {
    HomeRecordTheme {
        ImportScreen(
            ImportUiState(
                phase = ImportPhase.PREVIEW,
                counts = listOf(ImportEntityType.ROOM to 3, ImportEntityType.PHOTO to 12),
                conflicts = listOf(ImportConflict(ImportEntityType.ROOM, "r1", "Kitchen")),
            ),
            onPick = {},
            onEvent = {},
        )
    }
}
