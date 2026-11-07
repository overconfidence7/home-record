package com.kurtz.homerecord.housemanual.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.SectionHeader
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

/**
 * HM-Backup (AC-C48, AC-H14): a passphrase typed twice and Make backup and share, which writes the whole home as one
 * locked file and opens the share sheet; below it, Restore from a backup opens the file picker.
 */
@Composable
fun BackupScreen(state: BackupUiState, onEvent: (BackupEvent) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.backup_title), subtitle = state.homeName.ifBlank { null }, onBack = { onEvent(BackupEvent.Back) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).testTag("hm_backup_contents")) {
            SectionHeader(stringResource(R.string.backup_make_header))
            Text(
                stringResource(R.string.backup_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecretField(
                    label = stringResource(R.string.backup_passphrase),
                    value = state.passphrase,
                    onValueChange = { onEvent(BackupEvent.PassphraseChanged(it)) },
                    helper = pluralStringResource(R.plurals.backup_passphrase_helper, BackupPassphrase.MIN_LENGTH, BackupPassphrase.MIN_LENGTH),
                    error = if (state.problem == PassphraseProblem.TooShort) pluralStringResource(R.plurals.backup_passphrase_short, BackupPassphrase.MIN_LENGTH, BackupPassphrase.MIN_LENGTH) else null,
                    imeAction = ImeAction.Next,
                    tag = "hm_backup_passphrase",
                    enabled = !state.busy,
                )
                SecretField(
                    label = stringResource(R.string.backup_confirm),
                    value = state.confirm,
                    onValueChange = { onEvent(BackupEvent.ConfirmChanged(it)) },
                    helper = null,
                    error = if (state.problem == PassphraseProblem.Mismatch) stringResource(R.string.backup_confirm_mismatch) else null,
                    imeAction = ImeAction.Done,
                    tag = "hm_backup_confirm",
                    enabled = !state.busy,
                )
            }
            Button(
                onClick = { onEvent(BackupEvent.Make) },
                enabled = !state.busy,
                modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 56.dp).testTag("hm_backup_make"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Lock, contentDescription = null)
                    Text(stringResource(if (state.busy) R.string.backup_making else R.string.backup_make))
                }
            }
            val made = state.made
            when {
                state.failed -> Text(
                    stringResource(R.string.backup_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("hm_backup_failed"),
                )
                made != null -> Text(
                    stringResource(R.string.backup_made, made),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("hm_backup_made"),
                )
            }
            SectionHeader(stringResource(R.string.backup_restore_header))
            Text(
                stringResource(if (state.viewOnly) R.string.backup_restore_body_view_only else R.string.backup_restore_body),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("hm_backup_restore_body"),
            )
            if (!state.viewOnly) OutlinedButton(
                onClick = { onEvent(BackupEvent.Restore) },
                enabled = !state.busy,
                modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 56.dp).testTag("hm_backup_restore"),
            ) {
                Icon(Icons.Rounded.Restore, contentDescription = null)
                Text(stringResource(R.string.backup_restore), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** A passphrase field: dots by default, with a 48 dp Show/Hide toggle. Also used by HM-Restore. */
@Composable
internal fun SecretField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    helper: String?,
    error: String?,
    imeAction: ImeAction,
    tag: String,
    enabled: Boolean = true,
    onDone: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        label = { Text(label) },
        supportingText = (error ?: helper)?.let { { Text(it) } },
        isError = error != null,
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.testTag("${tag}_show")) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(if (visible) R.string.backup_hide_passphrase else R.string.backup_show_passphrase),
                )
            }
        },
    )
}
