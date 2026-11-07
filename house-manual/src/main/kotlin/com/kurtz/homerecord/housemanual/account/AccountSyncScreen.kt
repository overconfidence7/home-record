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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.core.billing.LimitGate
import com.kurtz.homerecord.core.billing.LimitedFeature
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

sealed interface AccountSyncEvent {
    data object Back : AccountSyncEvent
    /** Sign in on the free tier: opens HM-Paywall for cloud sync (AC-H18). */
    data object OpenPro : AccountSyncEvent
}

/**
 * AC-H18: on the free tier, Sign in from HM-Account opens HM-Paywall instead of the sign-in form. Only a signed-out
 * phone is held back; a signed-in account (from a claim link or a second phone) keeps its summary and Sign out.
 */
fun accountSignInNeedsPro(pro: Boolean, auth: AuthState): Boolean =
    auth is AuthState.SignedOut && LimitGate.blockedBy(pro, LimitedFeature.CloudSync)

/**
 * HM-Account (AC-H15): More → Account and sync. [account] is the shared sign-in form when signed out (it scrolls
 * itself) or the signed-in summary with the sync chip, what sync does, Sign out and Delete cloud copy. After a
 * delete, [cloudDeleted] puts one line above the sign-in form saying the home is still on this phone. With
 * [needsPro] (free tier, signed out) the form is replaced by what signing in does and a Sign in button that opens
 * HM-Paywall (AC-H18).
 */
@Composable
fun AccountSyncScreen(
    onEvent: (AccountSyncEvent) -> Unit,
    cloudDeleted: Boolean = false,
    needsPro: Boolean = false,
    account: @Composable (Modifier) -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.account_sync_title), onBack = { onEvent(AccountSyncEvent.Back) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (cloudDeleted) {
                Text(
                    stringResource(R.string.account_cloud_deleted),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).testTag("hm_account_cloud_deleted"),
                )
            }
            if (needsPro) SignInNeedsPro(onEvent, Modifier.weight(1f)) else account(Modifier.weight(1f))
        }
    }
}

/** Free tier, signed out: what signing in does, that it is part of Pro, and Sign in (opens HM-Paywall). */
@Composable
private fun SignInNeedsPro(onEvent: (AccountSyncEvent) -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.account_pro_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.account_pro_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("hm_account_pro_body"))
        Button(
            onClick = { onEvent(AccountSyncEvent.OpenPro) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("hm_account_sign_in_pro"),
        ) { Text(stringResource(R.string.account_pro_sign_in)) }
    }
}

/**
 * AC-H18 "no cloud sync" on the free tier (T032b2e): a signed-in phone syncs only when it has Pro or the server says
 * the home is Pro (a family member's free phone on a Pro home). Otherwise HM-Account shows [SyncNeedsPro].
 */
fun accountSyncNeedsPro(phonePro: Boolean, homePro: Boolean): Boolean = !LimitGate.canSyncHome(phonePro, homePro)

/** Signed in on the free tier: in place of the sync chip, that this home stays on this phone and a button to Pro. */
@Composable
fun SyncNeedsPro(onOpenPro: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.account_sync_pro_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.account_sync_pro_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("hm_account_sync_pro_body"),
        )
        Button(
            onClick = onOpenPro,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("hm_account_sync_pro"),
        ) { Text(stringResource(R.string.account_sync_pro_button)) }
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
