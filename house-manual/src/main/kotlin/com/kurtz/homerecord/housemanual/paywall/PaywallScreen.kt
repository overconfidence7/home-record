package com.kurtz.homerecord.housemanual.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.billing.Offer
import com.kurtz.homerecord.core.billing.Plan
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun PaywallRoute(viewModel: PaywallViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsState()
    // The limits lift the moment the entitlement turns on (AC-94); the caller retries its save.
    LaunchedEffect(state.isPro) { if (state.isPro) onClose() }
    PaywallScreen(state) { event ->
        if (event == PaywallEvent.Close) onClose() else viewModel.onEvent(event)
    }
}

@Composable
fun PaywallScreen(state: PaywallUiState, onEvent: (PaywallEvent) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.paywall_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.paywall_body, 1, 10))
            when {
                state.loading -> CircularProgressIndicator()
                state.pricesUnavailable -> {
                    Text(stringResource(R.string.paywall_prices_unavailable), Modifier.testTag("paywall-unavailable"))
                    Button(
                        onClick = { onEvent(PaywallEvent.Retry) },
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("paywall-retry"),
                    ) { Text(stringResource(R.string.paywall_retry)) }
                }
                else -> state.offers.forEach { offer ->
                    Button(
                        onClick = { onEvent(PaywallEvent.Buy(offer.plan)) },
                        enabled = !state.purchasing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("paywall-${offer.plan.name.lowercase()}"),
                    ) { Text(stringResource(offer.plan.labelRes(), offer.price)) }
                }
            }
            if (state.purchaseFailed) Text(stringResource(R.string.paywall_purchase_failed), color = MaterialTheme.colorScheme.error)
            OutlinedButton(
                onClick = { onEvent(PaywallEvent.Close) },
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("paywall-close"),
            ) { Text(stringResource(R.string.paywall_not_now)) }
        }
    }
}

private fun Plan.labelRes() = when (this) {
    Plan.YEARLY -> R.string.paywall_yearly
    Plan.LIFETIME -> R.string.paywall_lifetime
}

@Preview
@Composable
private fun PaywallPreview() = HomeRecordTheme {
    PaywallScreen(PaywallUiState(loading = false, offers = listOf(Offer(Plan.YEARLY, "$19.99"), Offer(Plan.LIFETIME, "$49.99")))) {}
}
