package com.kurtz.homerecord.housemanual.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.logic.DedicatedRow
import com.kurtz.homerecord.core.logic.HazardReason
import com.kurtz.homerecord.core.logic.Insight
import com.kurtz.homerecord.core.logic.InsightSeverity
import com.kurtz.homerecord.core.logic.PanelInsightsResult
import com.kurtz.homerecord.housemanual.R

/** AC-106: bottom sheet wrapper; the content is separate so it can be tested without a window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelInsightsSheet(result: PanelInsightsResult, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PanelInsightsContent(result, onDismiss)
    }
}

@Composable
fun PanelInsightsContent(result: PanelInsightsResult, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).testTag("insights_content"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.headlineSmall)
        CapacityCard(result)
        result.insights.forEachIndexed { index, insight ->
            InsightCard(insight, Modifier.testTag("insight_$index"))
        }
        Text(
            stringResource(R.string.insights_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("insights_disclaimer"),
        )
        OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.insights_close))
        }
    }
}

@Composable
private fun CapacityCard(result: PanelInsightsResult) {
    val c = result.capacity
    Card(Modifier.fillMaxWidth().testTag("insight_capacity"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.insights_capacity_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.insights_capacity_slots, c.slotsUsed, c.slotsFree))
            Text(stringResource(R.string.insights_capacity_circuits, c.circuits, c.twoPoleCircuits))
            Text(stringResource(R.string.insights_capacity_labeled, c.percentLabeled, c.percentConfirmed))
        }
    }
}

@Composable
private fun InsightCard(insight: Insight, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val container = when (insight.severity) {
        InsightSeverity.WARNING -> colors.errorContainer
        InsightSeverity.CHECK -> colors.tertiaryContainer
        InsightSeverity.INFO -> colors.surfaceVariant
    }
    val severity = when (insight.severity) {
        InsightSeverity.WARNING -> R.string.insights_sev_warning
        InsightSeverity.CHECK -> R.string.insights_sev_check
        InsightSeverity.INFO -> R.string.insights_sev_info
    }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(severity), style = MaterialTheme.typography.labelLarge)
            Text(insightText(insight))
            if (insight is Insight.Hazard) {
                insight.reasons.forEach { Text("• " + stringResource(hazardReason(it)), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun insightText(insight: Insight): String = when (insight) {
    Insight.TandemNotAllowed -> stringResource(R.string.insights_tandem_not_allowed)
    Insight.TandemUnknown -> stringResource(R.string.insights_tandem_unknown)
    is Insight.OverMaxCircuits -> stringResource(R.string.insights_over_max, insight.circuits, insight.max)
    is Insight.EraCircuitLimit -> stringResource(R.string.insights_era_limit, insight.circuits)
    is Insight.Hazard -> stringResource(R.string.insights_hazard)
    is Insight.Gfci -> stringResource(R.string.insights_gfci, insight.breakerLabel.ifBlank { stringResource(R.string.insights_unlabeled_breaker) }, insight.slot, insight.rooms.joinToString())
    is Insight.Afci -> stringResource(R.string.insights_afci, insight.breakerLabel.ifBlank { stringResource(R.string.insights_unlabeled_breaker) }, insight.slot, insight.rooms.joinToString())
    is Insight.Dedicated -> stringResource(
        if (insight.found) R.string.insights_dedicated_found else R.string.insights_dedicated_missing,
        stringResource(rowName(insight.row)),
    )
}

private fun hazardReason(r: HazardReason) = when (r) {
    HazardReason.FPE -> R.string.insights_hazard_fpe
    HazardReason.ZINSCO -> R.string.insights_hazard_zinsco
    HazardReason.CHALLENGER_BULLDOG -> R.string.insights_hazard_challenger
    HazardReason.PUSHMATIC -> R.string.insights_hazard_pushmatic
    HazardReason.NO_MAIN -> R.string.insights_hazard_no_main
}

private fun rowName(r: DedicatedRow) = when (r) {
    DedicatedRow.KITCHEN_SMALL_APPLIANCE -> R.string.insights_row_kitchen
    DedicatedRow.BATHROOM -> R.string.insights_row_bathroom
    DedicatedRow.LAUNDRY -> R.string.insights_row_laundry
    DedicatedRow.GARAGE -> R.string.insights_row_garage
    DedicatedRow.DISHWASHER -> R.string.insights_row_dishwasher
    DedicatedRow.DISPOSAL -> R.string.insights_row_disposal
    DedicatedRow.MICROWAVE -> R.string.insights_row_microwave
    DedicatedRow.RANGE -> R.string.insights_row_range
    DedicatedRow.DRYER -> R.string.insights_row_dryer
    DedicatedRow.WATER_HEATER -> R.string.insights_row_water_heater
    DedicatedRow.HVAC -> R.string.insights_row_hvac
}
