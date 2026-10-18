package com.kurtz.homerecord.housemanual.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.logic.PanelProfileError
import com.kurtz.homerecord.core.logic.PanelProfileInput
import com.kurtz.homerecord.core.logic.PanelProfileRule
import com.kurtz.homerecord.core.model.PanelKind
import com.kurtz.homerecord.core.model.TandemPolicy
import com.kurtz.homerecord.housemanual.R

/** A breaker in another panel that could feed a sub-panel (AC-102). */
data class FeedOption(val breakerId: String, val panelName: String, val slot: Int, val label: String)

/** AC-101 / AC-102: the panel profile fields, shared by first-time setup and the details editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanelProfileForm(
    input: PanelProfileInput,
    errors: Set<PanelProfileError>,
    feedOptions: List<FeedOption>,
    onChange: (PanelProfileInput) -> Unit,
) {
    val chip = Modifier.heightIn(min = 48.dp)
    Column(Modifier.fillMaxWidth().testTag("panel_profile_form"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.panel_kind), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(input.kind == PanelKind.MAIN, { onChange(input.copy(kind = PanelKind.MAIN)) }, { Text(stringResource(R.string.panel_kind_main)) }, chip.testTag("kind_MAIN"))
            FilterChip(input.kind == PanelKind.SUB, { onChange(input.copy(kind = PanelKind.SUB)) }, { Text(stringResource(R.string.panel_kind_sub)) }, chip.testTag("kind_SUB"))
        }
        if (input.kind == PanelKind.SUB) {
            Text(stringResource(R.string.panel_feed_question), style = MaterialTheme.typography.titleMedium)
            if (feedOptions.isEmpty()) {
                Text(stringResource(R.string.panel_feed_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    feedOptions.forEach { o ->
                        FilterChip(
                            selected = input.feedBreakerId == o.breakerId,
                            onClick = { onChange(input.copy(feedBreakerId = if (input.feedBreakerId == o.breakerId) null else o.breakerId)) },
                            label = { Text(stringResource(R.string.panel_feed_option, o.panelName, o.slot, o.label)) },
                            modifier = chip.testTag("feed_${o.breakerId}"),
                        )
                    }
                }
            }
        }
        Text(stringResource(R.string.panel_rating), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelProfileRule.RATING_CHOICES.forEach { amps ->
                FilterChip(input.ratingAmps == amps.toString(), { onChange(input.copy(ratingAmps = amps.toString())) }, { Text(stringResource(R.string.panel_rating_amps_chip, amps)) }, chip.testTag("rating_$amps"))
            }
        }
        OutlinedTextField(
            value = input.ratingAmps,
            onValueChange = { onChange(input.copy(ratingAmps = it)) },
            label = { Text(stringResource(R.string.panel_rating_custom)) },
            isError = PanelProfileError.RATING in errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("panel_rating_input"),
        )
        OutlinedTextField(
            value = input.brand,
            onValueChange = { onChange(input.copy(brand = it)) },
            label = { Text(stringResource(R.string.panel_brand)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("panel_brand"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelProfileRule.BRAND_SUGGESTIONS.forEach { brand ->
                FilterChip(input.brand == brand, { onChange(input.copy(brand = brand)) }, { Text(brand) }, chip.testTag("brand_$brand"))
            }
        }
        OutlinedTextField(
            value = input.model,
            onValueChange = { onChange(input.copy(model = it)) },
            label = { Text(stringResource(R.string.panel_model)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("panel_model"),
        )
        OutlinedTextField(
            value = input.maxCircuits,
            onValueChange = { onChange(input.copy(maxCircuits = it)) },
            label = { Text(stringResource(R.string.panel_max_circuits)) },
            supportingText = { Text(stringResource(R.string.panel_max_circuits_hint)) },
            isError = PanelProfileError.MAX_CIRCUITS in errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("panel_max_circuits"),
        )
        Text(stringResource(R.string.panel_tandems), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TandemPolicy.ALLOWED to R.string.yes,
                TandemPolicy.NOT_ALLOWED to R.string.no,
                TandemPolicy.UNKNOWN to R.string.unknown,
            ).forEach { (policy, label) ->
                FilterChip(input.tandemPolicy == policy, { onChange(input.copy(tandemPolicy = policy)) }, { Text(stringResource(label)) }, chip.testTag("tandem_${policy.name}"))
            }
        }
        Text(stringResource(R.string.panel_main_breaker_present), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(input.hasMainBreaker, { onChange(input.copy(hasMainBreaker = true)) }, { Text(stringResource(R.string.yes)) }, chip.testTag("has_main_yes"))
            FilterChip(!input.hasMainBreaker, { onChange(input.copy(hasMainBreaker = false)) }, { Text(stringResource(R.string.no)) }, chip.testTag("has_main_no"))
        }
        OutlinedTextField(
            value = input.yearInstalled,
            onValueChange = { onChange(input.copy(yearInstalled = it)) },
            label = { Text(stringResource(R.string.panel_year_installed)) },
            isError = PanelProfileError.YEAR in errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("panel_year"),
        )
    }
}
