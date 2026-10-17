package com.kurtz.homerecord.housemanual.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import java.io.File

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, onImport: () -> Unit, onReminders: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.share.collect { shareBackup(context, it) } }
    SettingsScreen(state, versionName(context), onImport, onReminders, viewModel::onEvent)
}

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

private fun shareBackup(context: Context, relativePath: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(context.cacheDir, relativePath))
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.settings_export_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    version: String,
    onImport: () -> Unit,
    onReminders: () -> Unit,
    onEvent: (SettingsEvent) -> Unit,
) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        Text(stringResource(R.string.settings_export_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_export_hint), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.passphrase,
            onValueChange = { onEvent(SettingsEvent.PassphraseChanged(it)) },
            label = { Text(stringResource(R.string.backup_passphrase)) },
            supportingText = { Text(stringResource(R.string.settings_passphrase_rule)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("settings_passphrase"),
        )
        OutlinedTextField(
            value = state.confirm,
            onValueChange = { onEvent(SettingsEvent.ConfirmChanged(it)) },
            label = { Text(stringResource(R.string.settings_passphrase_confirm)) },
            isError = state.passphraseMismatch,
            supportingText = if (state.passphraseMismatch) ({ Text(stringResource(R.string.settings_passphrase_mismatch)) }) else null,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("settings_confirm"),
        )
        if (state.exportFailed) {
            Text(stringResource(R.string.settings_export_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("settings_export_failed"))
        }
        Button(
            onClick = { onEvent(SettingsEvent.Export) },
            enabled = state.canExport,
            modifier = Modifier.heightIn(min = 48.dp).testTag("settings_export"),
        ) { Text(stringResource(R.string.settings_export_button)) }

        OutlinedButton(onClick = onImport, modifier = Modifier.heightIn(min = 48.dp).testTag("settings_import")) {
            Text(stringResource(R.string.settings_import))
        }
        OutlinedButton(onClick = onReminders, modifier = Modifier.heightIn(min = 48.dp).testTag("settings_climate")) {
            Text(stringResource(R.string.settings_climate))
        }

        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_version, version), modifier = Modifier.testTag("settings_version"))
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    HomeRecordTheme { SettingsScreen(SettingsUiState(hasHome = true, passphrase = "hunter22!", confirm = "hunter22!"), "0.1.0", {}, {}, {}) }
}
