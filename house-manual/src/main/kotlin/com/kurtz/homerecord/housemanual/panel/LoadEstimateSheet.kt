package com.kurtz.homerecord.housemanual.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.logic.HvacKind
import com.kurtz.homerecord.core.logic.LoadAppliance
import com.kurtz.homerecord.core.logic.LoadForm
import com.kurtz.homerecord.core.logic.LoadResult
import com.kurtz.homerecord.core.logic.LoadVerdict
import com.kurtz.homerecord.housemanual.R

/** AC-114..AC-118: bottom sheet wrapper; content is separate so it can be tested without a window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadEstimateSheet(form: LoadForm, result: LoadResult?, ratingAmps: Int?, onEvent: (PanelEvent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(PanelEvent.CloseLoadEstimate) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LoadEstimateContent(form, result, ratingAmps, onEvent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadEstimateContent(form: LoadForm, result: LoadResult?, ratingAmps: Int?, onEvent: (PanelEvent) -> Unit) {
    fun change(f: LoadForm) = onEvent(PanelEvent.LoadFormChanged(f))
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).testTag("load_content"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.load_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            form.squareFeet, { change(form.copy(squareFeet = it.filter(Char::isDigit))) },
            label = { Text(stringResource(R.string.load_sqft)) }, keyboardOptions = numeric, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("load_sqft"),
        )
        Text(stringResource(R.string.load_appliances), style = MaterialTheme.typography.titleSmall)
        LoadAppliance.entries.forEach { appliance ->
            val on = appliance in form.appliances
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(on, { change(form.toggled(appliance)) }, Modifier.testTag("load_app_${appliance.name}"))
                Text(stringResource(applianceName(appliance)), Modifier.weight(1f))
                if (on) {
                    OutlinedTextField(
                        form.appliances[appliance].orEmpty(),
                        { change(form.copy(appliances = form.appliances + (appliance to it.filter(Char::isDigit)))) },
                        label = { Text(stringResource(R.string.load_va_label)) }, keyboardOptions = numeric, singleLine = true,
                        modifier = Modifier.width(120.dp).testTag("load_va_${appliance.name}"),
                    )
                }
            }
        }
        Text(stringResource(R.string.load_hvac), style = MaterialTheme.typography.titleSmall)
        HvacKind.entries.forEach { kind ->
            FilterChip(
                selected = form.hvac == kind, onClick = { change(form.withHvac(kind)) },
                label = { Text(stringResource(hvacName(kind))) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("load_hvac_${kind.name}"),
            )
        }
        if (form.hvac != HvacKind.NONE) {
            OutlinedTextField(
                form.hvacVa, { change(form.copy(hvacVa = it.filter(Char::isDigit))) },
                label = { Text(stringResource(R.string.load_hvac_va)) }, keyboardOptions = numeric, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("load_hvac_va"),
            )
        }
        if (form.hvac == HvacKind.HEAT_PUMP_STRIP) {
            OutlinedTextField(
                form.stripVa, { change(form.copy(stripVa = it.filter(Char::isDigit))) },
                label = { Text(stringResource(R.string.load_strip_va)) }, keyboardOptions = numeric, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("load_strip_va"),
            )
        }
        Button(
            onClick = { onEvent(PanelEvent.CalculateLoad) },
            enabled = form.toInput(ratingAmps) != null,
            modifier = Modifier.heightIn(min = 48.dp).testTag("load_calculate"),
        ) { Text(stringResource(R.string.load_calculate)) }
        result?.let { LoadResultCard(it) }
        OutlinedButton(
            onClick = { onEvent(PanelEvent.CloseLoadEstimate) },
            modifier = Modifier.heightIn(min = 48.dp).padding(bottom = 16.dp),
        ) { Text(stringResource(R.string.load_close)) }
    }
}

@Composable
private fun LoadResultCard(result: LoadResult) {
    val colors = MaterialTheme.colorScheme
    val container = when (result.verdict) {
        LoadVerdict.EXCEEDS -> colors.errorContainer
        LoadVerdict.NEAR_CAPACITY -> colors.tertiaryContainer
        else -> colors.surfaceVariant
    }
    Card(Modifier.fillMaxWidth().testTag("load_result"), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val rating = result.ratingAmps
            if (rating != null) {
                Text(stringResource(R.string.load_result_vs_rating, result.amps, rating), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { result.fraction.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().testTag("load_bar"))
            } else {
                Text(stringResource(R.string.load_result_amps_only, result.amps), style = MaterialTheme.typography.titleMedium)
            }
            when (result.verdict) {
                LoadVerdict.ROOM_TO_GROW -> Text(stringResource(R.string.load_verdict_grow))
                LoadVerdict.NEAR_CAPACITY -> Text(stringResource(R.string.load_verdict_near))
                LoadVerdict.EXCEEDS -> Text(stringResource(R.string.load_verdict_exceeds))
                LoadVerdict.UNKNOWN_RATING -> Text(stringResource(R.string.load_result_set_rating))
            }
            Text(stringResource(R.string.load_explain), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun applianceName(a: LoadAppliance) = when (a) {
    LoadAppliance.RANGE -> R.string.load_app_range
    LoadAppliance.WALL_OVEN -> R.string.load_app_wall_oven
    LoadAppliance.COOKTOP -> R.string.load_app_cooktop
    LoadAppliance.DRYER -> R.string.load_app_dryer
    LoadAppliance.WATER_HEATER -> R.string.load_app_water_heater
    LoadAppliance.DISHWASHER -> R.string.load_app_dishwasher
    LoadAppliance.DISPOSAL -> R.string.load_app_disposal
    LoadAppliance.MICROWAVE -> R.string.load_app_microwave
    LoadAppliance.EV_CHARGER -> R.string.load_app_ev_charger
    LoadAppliance.HOT_TUB -> R.string.load_app_hot_tub
    LoadAppliance.WELL_PUMP -> R.string.load_app_well_pump
    LoadAppliance.POOL_PUMP -> R.string.load_app_pool_pump
    LoadAppliance.OTHER -> R.string.load_app_other
}

private fun hvacName(k: HvacKind) = when (k) {
    HvacKind.NONE -> R.string.load_hvac_none
    HvacKind.AC_ONLY -> R.string.load_hvac_ac_only
    HvacKind.HEAT_PUMP -> R.string.load_hvac_heat_pump
    HvacKind.HEAT_PUMP_STRIP -> R.string.load_hvac_heat_pump_strip
    HvacKind.ELECTRIC_HEAT_UNDER_4 -> R.string.load_hvac_electric_under_4
    HvacKind.ELECTRIC_HEAT_4_PLUS -> R.string.load_hvac_electric_4_plus
}
