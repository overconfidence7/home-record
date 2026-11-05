package com.kurtz.homerecord.fieldlog.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.billing.CompanyPlanOffer
import com.kurtz.homerecord.core.billing.FieldLogPlan
import com.kurtz.homerecord.core.billing.FieldLogPlanKind
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R

/**
 * FL-Paywall (AC-F56), Owner only: the plan in force (trial days left, Solo or Crew), Solo and Crew with the store's
 * monthly prices, Restore. Opened from More while the plan runs; it is the whole app once the plan has ended.
 */
@Composable
fun PlanScreen(state: PlanUiState, onEvent: (PlanEvent) -> Unit) {
    if (state.lapsed) {
        Scaffold { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.plan_lapsed_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
                PlanBody(state, onEvent)
                TextButton(
                    onClick = { onEvent(PlanEvent.SignOut) },
                    modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterHorizontally).testTag("fl_plan_sign_out"),
                ) { Text(stringResource(R.string.more_sign_out)) }
            }
        }
    } else {
        ScreenScaffold(title = stringResource(R.string.plan_title), onBack = { onEvent(PlanEvent.Back) }) { padding ->
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { PlanBody(state, onEvent) }
        }
    }
}

@Composable
private fun ColumnScope.PlanBody(state: PlanUiState, onEvent: (PlanEvent) -> Unit) {
    Text(statusText(state.plan), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("fl_plan_status"))
    Text(stringResource(R.string.plan_explain), style = MaterialTheme.typography.bodyLarge)
    when {
        state.loading -> Row(Modifier.fillMaxWidth().testTag("fl_plan_loading"), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
        state.loadFailed -> {
            Text(stringResource(R.string.plan_load_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("fl_plan_error"))
            Button(onClick = { onEvent(PlanEvent.Retry) }, modifier = Modifier.heightIn(min = 48.dp).testTag("fl_plan_retry")) {
                Text(stringResource(R.string.plan_retry))
            }
        }
        else -> state.offers.forEach { OfferCard(it, current = it.kind == state.plan.kind, busy = state.busy, onEvent) }
    }
    state.bought?.let {
        Text(
            stringResource(if (it == FieldLogPlanKind.Solo) R.string.plan_bought_solo else R.string.plan_bought_crew),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("fl_plan_bought"),
        )
    }
    state.actionFailed?.let {
        Text(
            stringResource(if (it == PlanFailure.Purchase) R.string.plan_purchase_failed else R.string.plan_nothing_to_restore),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("fl_plan_action_error"),
        )
    }
    TextButton(
        onClick = { onEvent(PlanEvent.Restore) },
        enabled = !state.busy,
        modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterHorizontally).testTag("fl_plan_restore"),
    ) { Text(stringResource(R.string.plan_restore)) }
}

@Composable
private fun OfferCard(offer: CompanyPlanOffer, current: Boolean, busy: Boolean, onEvent: (PlanEvent) -> Unit) {
    val crew = offer.kind == FieldLogPlanKind.Crew
    Card(Modifier.fillMaxWidth().testTag(if (crew) "fl_plan_card_crew" else "fl_plan_card_solo")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(if (crew) R.string.plan_crew else R.string.plan_solo), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (crew) R.string.plan_crew_detail else R.string.plan_solo_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.plan_price_month, offer.price), style = MaterialTheme.typography.bodyLarge)
            if (current) {
                Text(
                    stringResource(R.string.plan_current),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp).testTag(if (crew) "fl_plan_current_crew" else "fl_plan_current_solo"),
                )
            } else {
                val buy = { onEvent(PlanEvent.Buy(offer.id)) }
                val modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).testTag(if (crew) "fl_plan_crew" else "fl_plan_solo")
                val label = stringResource(if (crew) R.string.plan_choose_crew else R.string.plan_choose_solo)
                if (crew) Button(onClick = buy, enabled = !busy, modifier = modifier) { Text(label) }
                else OutlinedButton(onClick = buy, enabled = !busy, modifier = modifier) { Text(label) }
            }
        }
    }
}

@Composable
private fun statusText(plan: FieldLogPlan): String = when (plan.kind) {
    FieldLogPlanKind.Trial -> {
        val days = plan.trialDaysLeft ?: 0
        pluralStringResource(R.plurals.plan_status_trial, days, days)
    }
    FieldLogPlanKind.Solo -> stringResource(R.string.plan_status_solo)
    FieldLogPlanKind.Crew -> stringResource(R.string.plan_status_crew)
    FieldLogPlanKind.Expired -> stringResource(R.string.plan_status_expired)
}
