package com.kurtz.homerecord.housemanual.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.billing.LimitedFeature
import com.kurtz.homerecord.core.billing.ProPlan
import com.kurtz.homerecord.core.billing.ProPlanKind
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

/** HM-Paywall (AC-H18): what Pro adds, the two plans with store prices, and Restore. */
@Composable
fun PaywallScreen(state: PaywallUiState, onEvent: (PaywallEvent) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.paywall_title), onBack = { onEvent(PaywallEvent.Back) }) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(limitReason(state.feature)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("hm_paywall_reason"))
            Text(stringResource(R.string.paywall_adds), style = MaterialTheme.typography.bodyLarge)
            if (state.loadFailed) {
                Text(stringResource(R.string.paywall_load_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("hm_paywall_error"))
                Button(onClick = { onEvent(PaywallEvent.Retry) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_paywall_retry")) {
                    Text(stringResource(R.string.paywall_retry))
                }
            } else {
                state.plans.forEach { PlanCard(it, state.busy, onEvent) }
            }
            state.actionFailed?.let {
                Text(
                    stringResource(if (it == PaywallFailure.Purchase) R.string.paywall_purchase_failed else R.string.paywall_nothing_to_restore),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("hm_paywall_action_error"),
                )
            }
            TextButton(
                onClick = { onEvent(PaywallEvent.Restore) },
                enabled = !state.busy,
                modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterHorizontally).testTag("hm_paywall_restore"),
            ) { Text(stringResource(R.string.paywall_restore)) }
        }
    }
}

@Composable
private fun PlanCard(plan: ProPlan, busy: Boolean, onEvent: (PaywallEvent) -> Unit) {
    val annual = plan.kind == ProPlanKind.Annual
    val tag = if (annual) "hm_paywall_annual" else "hm_paywall_lifetime"
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (annual) R.string.paywall_annual else R.string.paywall_lifetime), style = MaterialTheme.typography.titleMedium)
                Text(plan.price, style = MaterialTheme.typography.bodyLarge)
            }
            val buy = { onEvent(PaywallEvent.Buy(plan.id)) }
            val modifier = Modifier.heightIn(min = 48.dp).testTag(tag)
            if (annual) Button(onClick = buy, enabled = !busy, modifier = modifier) { Text(stringResource(R.string.paywall_choose)) }
            else OutlinedButton(onClick = buy, enabled = !busy, modifier = modifier) { Text(stringResource(R.string.paywall_choose)) }
        }
    }
}

private fun limitReason(feature: LimitedFeature?): Int = when (feature) {
    LimitedFeature.SixteenthItem -> R.string.paywall_reason_items
    LimitedFeature.SecondPanel -> R.string.paywall_reason_panel
    LimitedFeature.SecondHome -> R.string.paywall_reason_home
    LimitedFeature.CloudSync -> R.string.paywall_reason_sync
    null -> R.string.paywall_reason_general
}
