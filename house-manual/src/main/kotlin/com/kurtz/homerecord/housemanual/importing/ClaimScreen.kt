package com.kurtz.homerecord.housemanual.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.bundle.HandoffClaimFailure
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

/**
 * HM-Claim: opening the contractor's handoff link (AC-H17, AC-B40). Signed out, it explains the link and shows the
 * sign-in form ([signIn]); signed in, it claims and downloads, then the import preview takes over.
 */
@Composable
fun ClaimScreen(state: ClaimUiState, onEvent: (ClaimEvent) -> Unit, signIn: @Composable (Modifier) -> Unit = {}) {
    ScreenScaffold(
        title = stringResource(R.string.claim_title),
        subtitle = state.homeName.ifBlank { null },
        onBack = { onEvent(ClaimEvent.Close) },
    ) { padding ->
        val fill = Modifier.fillMaxSize().padding(padding)
        when (state.phase) {
            ClaimPhase.Checking, ClaimPhase.Ready -> LoadingState(fill)
            ClaimPhase.SignIn -> Column(fill.testTag("hm_claim_sign_in")) {
                Text(
                    stringResource(R.string.claim_sign_in_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
                signIn(Modifier.weight(1f))
            }
            ClaimPhase.Claiming, ClaimPhase.Downloading -> Working(state, fill)
            ClaimPhase.Failed -> {
                val failure = state.failure ?: HandoffClaimFailure.Failed
                EmptyState(
                    icon = failureIcon(failure),
                    headline = stringResource(failureTitle(failure)),
                    body = stringResource(failureBody(failure)),
                    actionLabel = stringResource(if (state.canRetry) R.string.claim_retry else R.string.import_close),
                    onAction = { onEvent(if (state.canRetry) ClaimEvent.Retry else ClaimEvent.Close) },
                    modifier = fill.testTag("hm_claim_error"),
                    actionModifier = Modifier.testTag(if (state.canRetry) "hm_claim_retry" else "hm_claim_close"),
                    secondaryAction = if (state.canRetry) {
                        {
                            TextButton(onClick = { onEvent(ClaimEvent.Close) }, modifier = Modifier.testTag("hm_claim_close")) {
                                Text(stringResource(R.string.import_close))
                            }
                        }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun Working(state: ClaimUiState, modifier: Modifier) {
    Column(
        modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("hm_claim_working"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(if (state.phase == ClaimPhase.Claiming) R.string.claim_claiming else R.string.claim_downloading),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (state.companyName.isNotBlank()) {
            Text(
                stringResource(R.string.claim_from, state.companyName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun failureIcon(f: HandoffClaimFailure) = when (f) {
    HandoffClaimFailure.Offline -> Icons.Rounded.CloudOff
    HandoffClaimFailure.NotFound -> Icons.Rounded.LinkOff
    HandoffClaimFailure.Expired -> Icons.Rounded.Schedule
    HandoffClaimFailure.ClaimedByOther -> Icons.Rounded.Lock
    HandoffClaimFailure.SignedOut -> Icons.Rounded.Lock
    HandoffClaimFailure.Failed -> Icons.Rounded.ErrorOutline
}

private fun failureTitle(f: HandoffClaimFailure): Int = when (f) {
    HandoffClaimFailure.Offline -> R.string.claim_offline_title
    HandoffClaimFailure.NotFound -> R.string.claim_not_found_title
    HandoffClaimFailure.Expired -> R.string.claim_expired_title
    HandoffClaimFailure.ClaimedByOther -> R.string.claim_taken_title
    HandoffClaimFailure.SignedOut, HandoffClaimFailure.Failed -> R.string.claim_failed_title
}

private fun failureBody(f: HandoffClaimFailure): Int = when (f) {
    HandoffClaimFailure.Offline -> R.string.claim_offline_body
    HandoffClaimFailure.NotFound -> R.string.claim_not_found_body
    HandoffClaimFailure.Expired -> R.string.claim_expired_body
    HandoffClaimFailure.ClaimedByOther -> R.string.claim_taken_body
    HandoffClaimFailure.SignedOut, HandoffClaimFailure.Failed -> R.string.claim_failed_body
}
