package com.kurtz.homerecord.housemanual.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

sealed interface AccountSyncEvent {
    data object Back : AccountSyncEvent
}

/**
 * HM-Account (AC-H15): More → Account and sync. [account] is the shared sign-in form when signed out (it scrolls
 * itself) or the signed-in summary with the sync chip, what sync does, Sign out and Delete cloud copy. After a
 * delete, [cloudDeleted] puts one line above the sign-in form saying the home is still on this phone.
 */
@Composable
fun AccountSyncScreen(onEvent: (AccountSyncEvent) -> Unit, cloudDeleted: Boolean = false, account: @Composable (Modifier) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.account_sync_title), onBack = { onEvent(AccountSyncEvent.Back) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (cloudDeleted) {
                Text(
                    stringResource(R.string.account_cloud_deleted),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).testTag("hm_account_cloud_deleted"),
                )
            }
            account(Modifier.weight(1f))
        }
    }
}

/** What sync does for a signed-in home; shown under the sync chip on HM-Account. */
@Composable
fun SyncExplainer() {
    Text(
        stringResource(R.string.account_sync_explainer),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("hm_account_sync_explainer"),
    )
}

/** Under Sign out: what deleting the cloud copy does, the button (asks first), and why the last try failed. */
@Composable
fun CloudCopySection(state: CloudCopyUiState, onEvent: (CloudCopyEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.account_cloud_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.account_cloud_body), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = { onEvent(CloudCopyEvent.Delete) },
            enabled = !state.deleting,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("hm_account_delete_cloud"),
        ) {
            Text(stringResource(if (state.deleting) R.string.account_cloud_deleting else R.string.account_cloud_delete))
        }
        if (state.failed) {
            Text(
                stringResource(if (state.offline) R.string.account_cloud_offline else R.string.account_cloud_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("hm_account_delete_cloud_error"),
            )
        }
    }
    if (state.confirmOpen) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(CloudCopyEvent.Dismiss) },
            title = { Text(stringResource(R.string.account_cloud_confirm_title)) },
            text = { Text(stringResource(R.string.account_cloud_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(CloudCopyEvent.Confirm) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hm_account_delete_cloud_confirm"),
                ) { Text(stringResource(R.string.account_cloud_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(CloudCopyEvent.Dismiss) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hm_account_delete_cloud_cancel"),
                ) { Text(stringResource(com.kurtz.homerecord.coreui.R.string.cancel)) }
            },
        )
    }
}
