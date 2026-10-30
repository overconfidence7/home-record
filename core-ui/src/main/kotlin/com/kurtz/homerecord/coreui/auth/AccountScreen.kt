package com.kurtz.homerecord.coreui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix

/**
 * The signed-in account summary with Sign out (asks first), or a spinner while the token is read. [syncStatus] shows
 * under the summary (House Manual's sync chip, refused changes included). Signed out, [AccountRoute] shows sign-in instead.
 */
@Composable
fun AccountScreen(state: AccountUiState, onEvent: (AccountEvent) -> Unit, modifier: Modifier = Modifier, syncStatus: @Composable () -> Unit = {}) {
    val prefix = LocalTagPrefix.current
    if (state.phase == AccountPhase.Restoring) {
        LoadingState(modifier)
        return
    }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.account_signed_in), style = MaterialTheme.typography.titleMedium)
        syncStatus()
        Text(stringResource(R.string.account_sign_out_note), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = { onEvent(AccountEvent.SignOut) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("${prefix}_account_sign_out"),
        ) {
            Text(stringResource(R.string.account_sign_out))
        }
    }
    if (state.confirmSignOut) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(AccountEvent.DismissSignOut) },
            title = { Text(stringResource(R.string.account_sign_out_title)) },
            text = { Text(stringResource(R.string.account_sign_out_body)) },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(AccountEvent.ConfirmSignOut) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_account_sign_out_confirm"),
                ) { Text(stringResource(R.string.account_sign_out)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountEvent.DismissSignOut) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_account_sign_out_cancel"),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
