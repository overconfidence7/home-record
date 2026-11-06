package com.kurtz.homerecord.housemanual.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

sealed interface AccountSyncEvent {
    data object Back : AccountSyncEvent
}

/**
 * HM-Account (AC-H15): More → Account and sync. [account] is the shared sign-in form when signed out (it scrolls
 * itself) or the signed-in summary with the sync chip, what sync does, and Sign out.
 */
@Composable
fun AccountSyncScreen(onEvent: (AccountSyncEvent) -> Unit, account: @Composable (Modifier) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.account_sync_title), onBack = { onEvent(AccountSyncEvent.Back) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
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
