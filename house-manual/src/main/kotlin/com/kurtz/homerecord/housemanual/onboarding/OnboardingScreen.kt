package com.kurtz.homerecord.housemanual.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.OptionsSheet
import com.kurtz.homerecord.coreui.components.SheetOption
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HouseType

@Composable
fun houseTypeLabel(type: HouseType): String = stringResource(
    when (type) {
        HouseType.RANCHER -> R.string.type_rancher
        HouseType.TWO_STORY -> R.string.type_two_story
        HouseType.SPLIT_LEVEL -> R.string.type_split_level
        HouseType.RAISED_RANCH -> R.string.type_raised_ranch
        HouseType.CAPE_COD -> R.string.type_cape_cod
        HouseType.COLONIAL -> R.string.type_colonial
        HouseType.TOWNHOUSE -> R.string.type_townhouse
        HouseType.BUNGALOW -> R.string.type_bungalow
        HouseType.OTHER -> R.string.type_other
    },
)

@Composable
fun foundationLabel(foundation: Foundation): String = stringResource(
    when (foundation) {
        Foundation.BASEMENT -> R.string.foundation_basement
        Foundation.WALKOUT_BASEMENT -> R.string.foundation_walkout
        Foundation.CRAWLSPACE -> R.string.foundation_crawlspace
        Foundation.SLAB -> R.string.foundation_slab
    },
)

/** HM-Onboarding: name the home, pick the style and foundation, then draw the plan or skip (AC-C1). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, modifier: Modifier = Modifier) {
    var pickingType by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyLarge)
        LabeledField(
            label = stringResource(R.string.home_name),
            value = state.name,
            onValueChange = { onEvent(OnboardingEvent.NameChanged(it)) },
            modifier = Modifier.testTag("hm_onboarding_name"),
            required = true,
            helper = stringResource(R.string.home_name_helper),
            error = if (state.showErrors && state.name.isBlank()) stringResource(R.string.field_required) else null,
        )
        LabeledField(
            label = stringResource(R.string.home_address),
            value = state.address,
            onValueChange = { onEvent(OnboardingEvent.AddressChanged(it)) },
            modifier = Modifier.testTag("hm_onboarding_address"),
            imeAction = ImeAction.Done,
        )
        Column {
            OutlinedTextField(
                value = state.type?.let { houseTypeLabel(it) } ?: stringResource(R.string.home_type_choose),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.required_marker, stringResource(R.string.home_type))) },
                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                isError = state.showErrors && state.type == null,
                supportingText = if (state.showErrors && state.type == null) { { Text(stringResource(R.string.field_required)) } } else null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("hm_onboarding_type").clickable(role = Role.DropdownList) { pickingType = true },
                enabled = false,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = if (state.showErrors && state.type == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledSupportingTextColor = MaterialTheme.colorScheme.error,
                ),
            )
        }
        Text(stringResource(R.string.foundation), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Foundation.entries.forEach { option ->
                FilterChip(
                    selected = option == state.foundation,
                    onClick = { onEvent(OnboardingEvent.FoundationChanged(option)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hm_onboarding_foundation_${option.name.lowercase()}"),
                    label = { Text(foundationLabel(option)) },
                )
            }
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.attached_garage)) },
            supportingContent = { Text(stringResource(R.string.attached_garage_helper)) },
            trailingContent = { Switch(checked = state.attachedGarage, onCheckedChange = null) },
            modifier = Modifier.testTag("hm_onboarding_garage").clickable(role = Role.Switch) { onEvent(OnboardingEvent.GarageChanged(!state.attachedGarage)) },
        )
        Button(onClick = { onEvent(OnboardingEvent.DrawPlan) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("hm_onboarding_draw")) {
            Text(stringResource(R.string.draw_floor_plan))
        }
        TextButton(onClick = { onEvent(OnboardingEvent.Skip) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("hm_onboarding_skip")) {
            Text(stringResource(R.string.skip_for_now))
        }
    }
    if (pickingType) {
        OptionsSheet(
            title = stringResource(R.string.home_type),
            options = HouseType.entries.map { SheetOption(it.name, houseTypeLabel(it)) },
            onDismiss = { pickingType = false },
            onPick = { pickingType = false; onEvent(OnboardingEvent.TypeChanged(HouseType.parse(it.id))) },
        )
    }
}
