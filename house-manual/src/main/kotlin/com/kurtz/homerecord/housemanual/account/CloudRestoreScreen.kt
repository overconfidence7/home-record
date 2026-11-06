package com.kurtz.homerecord.housemanual.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.initializer
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

/**
 * Onboarding → "Sign in to get your home" (AC-H15 second phone). Signed out, one line on what signing in does over the
 * shared sign-in form ([signIn]); signed in, a spinner while the home comes down, or what to do when there is none.
 */
@Composable
fun CloudRestoreScreen(state: CloudRestoreUiState, onEvent: (CloudRestoreEvent) -> Unit, signIn: @Composable (Modifier) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.cloud_restore_title), onBack = { onEvent(CloudRestoreEvent.Back) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (state.phase) {
                CloudRestorePhase.SignedOut -> {
                    Text(
                        stringResource(R.string.cloud_restore_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).testTag("hm_cloud_restore_intro"),
                    )
                    signIn(Modifier.weight(1f))
                }
                CloudRestorePhase.Working -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("hm_cloud_restore_working"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.cloud_restore_working), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                CloudRestorePhase.NoHome -> EmptyState(
                    icon = Icons.Rounded.Home,
                    headline = stringResource(R.string.cloud_restore_none_title),
                    body = stringResource(R.string.cloud_restore_none_body),
                    actionLabel = stringResource(R.string.cloud_restore_set_up),
                    onAction = { onEvent(CloudRestoreEvent.SetUp) },
                    modifier = Modifier.testTag("hm_cloud_restore_none"),
                    actionModifier = Modifier.heightIn(min = 48.dp).testTag("hm_cloud_restore_setup"),
                )
                CloudRestorePhase.Failed -> EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    headline = stringResource(R.string.cloud_restore_failed_title),
                    body = stringResource(if (state.offline) R.string.cloud_restore_offline else R.string.cloud_restore_failed),
                    actionLabel = stringResource(com.kurtz.homerecord.coreui.R.string.retry),
                    onAction = { onEvent(CloudRestoreEvent.Retry) },
                    modifier = Modifier.testTag("hm_cloud_restore_error"),
                    actionModifier = Modifier.heightIn(min = 48.dp).testTag("hm_cloud_restore_retry"),
                    secondaryAction = {
                        TextButton(
                            onClick = { onEvent(CloudRestoreEvent.SetUp) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("hm_cloud_restore_setup"),
                        ) { Text(stringResource(R.string.cloud_restore_set_up_instead)) }
                    },
                )
            }
        }
    }
}

/** Wires [CloudRestoreScreen] to the account and [com.kurtz.homerecord.core.sync.CloudHomeRestore]. */
@Composable
fun CloudRestoreRoute(container: com.kurtz.homerecord.housemanual.AppContainer, onClose: () -> Unit) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel<CloudRestoreViewModel>(
        factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                CloudRestoreViewModel(
                    container.cloudRestore::restore,
                    container.auth.state.map { it is com.kurtz.homerecord.core.auth.AuthState.SignedIn },
                )
            }
        },
    )
    val state by vm.state.collectAsState()
    androidx.activity.compose.BackHandler(onBack = onClose)
    CloudRestoreScreen(
        state,
        onEvent = { event ->
            when (event) {
                CloudRestoreEvent.Back, CloudRestoreEvent.SetUp -> onClose()
                CloudRestoreEvent.Retry -> vm.onEvent(event)
            }
        },
    ) { modifier -> com.kurtz.homerecord.coreui.auth.AccountRoute(container.auth, modifier) }
}
