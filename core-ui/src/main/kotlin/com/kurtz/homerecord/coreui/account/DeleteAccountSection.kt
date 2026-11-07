package com.kurtz.homerecord.coreui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.R
import com.kurtz.homerecord.coreui.components.windowTestTags

/**
 * Delete account (T081d) as a section under Sign out: a title, what it removes ([body], each app says what stays on the
 * phone), an outlined error button and, after a failed try, why. [tag] prefixes the testTags (`hm_account`, `fl_more`).
 */
@Composable
fun DeleteAccountSection(state: DeleteAccountUiState, onEvent: (DeleteAccountEvent) -> Unit, body: String, confirmBody: String, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.account_delete_title), style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        DeleteAccountButton(state, onEvent, tag, Modifier.fillMaxWidth())
        DeleteAccountError(state, tag)
    }
    DeleteAccountDialog(state, onEvent, confirmBody, tag)
}

@Composable
fun DeleteAccountButton(state: DeleteAccountUiState, onEvent: (DeleteAccountEvent) -> Unit, tag: String, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = { onEvent(DeleteAccountEvent.Delete) },
        enabled = !state.deleting,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = modifier.heightIn(min = 48.dp).testTag("${tag}_delete_account"),
    ) {
        Text(stringResource(if (state.deleting) R.string.account_deleting else R.string.account_delete))
    }
}

@Composable
fun DeleteAccountError(state: DeleteAccountUiState, tag: String, modifier: Modifier = Modifier) {
    if (!state.failed) return
    Text(
        stringResource(if (state.offline) R.string.account_delete_offline else R.string.account_delete_failed),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.testTag("${tag}_delete_account_error"),
    )
}

@Composable
fun DeleteAccountDialog(state: DeleteAccountUiState, onEvent: (DeleteAccountEvent) -> Unit, confirmBody: String, tag: String) {
    if (!state.confirmOpen) return
    AlertDialog(
        modifier = Modifier.windowTestTags(),
        onDismissRequest = { onEvent(DeleteAccountEvent.Dismiss) },
        title = { Text(stringResource(R.string.account_delete_confirm_title)) },
        text = { Text(confirmBody) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(DeleteAccountEvent.Confirm) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_delete_account_confirm"),
            ) { Text(stringResource(R.string.account_delete_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(DeleteAccountEvent.Dismiss) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_delete_account_cancel"),
            ) { Text(stringResource(R.string.cancel)) }
        },
    )
}
