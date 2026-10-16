package com.kurtz.homerecord.fieldlog.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

/** Wires [SettingsViewModel] and the system camera (logo photo) to [SettingsScreen]. */
@Composable
fun SettingsRoute(viewModel: SettingsViewModel, media: MediaFileStore, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pending
        pending = null
        if (path != null) {
            if (ok) viewModel.onLogoCaptured(path) else media.delete(path)
        }
    }
    SettingsScreen(state) { event ->
        if (event == SettingsEvent.TakeLogo) {
            val path = media.newPhotoPath()
            val file = media.resolveForWrite(path) ?: return@SettingsScreen
            pending = path
            launcher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } else {
            viewModel.onEvent(event)
        }
    }
}

@Composable
fun SettingsScreen(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.settings_hint), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.name, onValueChange = { onEvent(SettingsEvent.NameChanged(it)) },
            label = { Text(stringResource(R.string.settings_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.phone, onValueChange = { onEvent(SettingsEvent.PhoneChanged(it)) },
            label = { Text(stringResource(R.string.settings_phone)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.email, onValueChange = { onEvent(SettingsEvent.EmailChanged(it)) },
            label = { Text(stringResource(R.string.settings_email)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(if (state.logoPath == null) R.string.settings_no_logo else R.string.settings_has_logo),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onEvent(SettingsEvent.TakeLogo) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(if (state.logoPath == null) R.string.settings_take_logo else R.string.settings_retake_logo))
            }
            if (state.logoPath != null) {
                OutlinedButton(onClick = { onEvent(SettingsEvent.RemoveLogo) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.settings_remove_logo))
                }
            }
        }
        Button(
            onClick = { onEvent(SettingsEvent.Save) }, enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.jobedit_save)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    HomeRecordTheme { SettingsScreen(SettingsUiState(loaded = true, name = "Acme Builders"), {}) }
}
