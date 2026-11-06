package com.kurtz.homerecord.housemanual.importing

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.backup.SecretField

/** HM-Restore: the backup's passphrase, then Open; what went wrong shows under the field (AC-C48). */
@Composable
fun RestoreScreen(state: RestoreUiState, onEvent: (RestoreEvent) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.restore_title), onBack = { onEvent(RestoreEvent.Back) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).testTag("hm_restore_contents")) {
            Text(
                stringResource(R.string.restore_passphrase_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                SecretField(
                    label = stringResource(R.string.backup_passphrase),
                    value = state.passphrase,
                    onValueChange = { onEvent(RestoreEvent.PassphraseChanged(it)) },
                    helper = null,
                    error = state.error?.let { stringResource(if (it == ImportError.NewerVersion) R.string.restore_error_newer else errorText(it)) },
                    imeAction = ImeAction.Done,
                    tag = "hm_restore_passphrase",
                    enabled = !state.opening,
                    onDone = { onEvent(RestoreEvent.Open) },
                )
            }
            Button(
                onClick = { onEvent(RestoreEvent.Open) },
                enabled = !state.opening && state.passphrase.isNotEmpty(),
                modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 56.dp).testTag("hm_restore_open"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.opening) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.LockOpen, contentDescription = null)
                    Text(stringResource(if (state.opening) R.string.restore_opening else R.string.restore_open))
                }
            }
        }
    }
}
