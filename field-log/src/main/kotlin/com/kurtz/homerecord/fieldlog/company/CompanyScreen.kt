package com.kurtz.homerecord.fieldlog.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.company.OvertimeRule
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.FormScaffold
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.fieldlog.R

/** FL-Company: name and contact details plus the punch and overtime rules the time clock uses. */
@Composable
fun CompanyScreen(state: CompanyUiState, onEvent: (CompanyEvent) -> Unit, onClose: () -> Unit) {
    when (state.status) {
        TeamStatus.Loading -> Scaffold { LoadingState(Modifier.padding(it)) }
        TeamStatus.Failed -> Scaffold {
            ErrorState(state.failure?.let { f -> failureText(f) }.orEmpty(), { onEvent(CompanyEvent.Retry) }, Modifier.padding(it))
        }
        TeamStatus.Ready -> FormScaffold(
            title = stringResource(R.string.company_title),
            dirty = state.dirty,
            saveEnabled = state.canSave,
            onClose = onClose,
            onSave = { onEvent(CompanyEvent.Save) },
        ) {
            state.saveFailure?.let { Text(failureText(it), color = MaterialTheme.colorScheme.error) }
            LabeledField(stringResource(R.string.company_name), state.name, { onEvent(CompanyEvent.Name(it)) }, Modifier.testTag("fl_company_name"), required = true)
            LabeledField(stringResource(R.string.company_trade), state.trade, { onEvent(CompanyEvent.Trade(it)) }, Modifier.testTag("fl_company_trade"))
            LabeledField(stringResource(R.string.company_phone), state.phone, { onEvent(CompanyEvent.Phone(it)) }, Modifier.testTag("fl_company_phone"), keyboardType = KeyboardType.Phone)
            LabeledField(stringResource(R.string.company_email), state.email, { onEvent(CompanyEvent.Email(it)) }, Modifier.testTag("fl_company_email"), keyboardType = KeyboardType.Email)
            LabeledField(stringResource(R.string.company_address), state.address, { onEvent(CompanyEvent.Address(it)) }, Modifier.testTag("fl_company_address"))

            Text(stringResource(R.string.company_ot_title), style = MaterialTheme.typography.titleSmall)
            Column {
                OvertimeRule.entries.forEach { rule ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("fl_company_ot_${rule.name.lowercase()}")
                            .selectable(state.overtimeRule == rule, onClick = { onEvent(CompanyEvent.Overtime(rule)) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.overtimeRule == rule, onClick = null)
                        Text(overtimeLabel(rule), Modifier.padding(start = 12.dp))
                    }
                }
            }

            SwitchRow(stringResource(R.string.company_break_rule), stringResource(R.string.company_break_rule_helper), state.breakRule) { onEvent(CompanyEvent.BreakRule(it)) }
            SwitchRow(stringResource(R.string.company_require_gps), stringResource(R.string.company_require_gps_helper), state.requireGps) { onEvent(CompanyEvent.RequireGps(it)) }
            LabeledField(
                label = stringResource(R.string.company_distance),
                value = state.punchDistanceText,
                onValueChange = { onEvent(CompanyEvent.PunchDistance(it)) },
                modifier = Modifier.testTag("fl_company_distance"),
                helper = stringResource(R.string.company_distance_helper),
                error = if (state.distanceInvalid) {
                    stringResource(R.string.company_distance_error, CompanySettings.MIN_PUNCH_DISTANCE_FT, CompanySettings.MAX_PUNCH_DISTANCE_FT)
                } else {
                    null
                },
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            )
            SwitchRow(stringResource(R.string.company_photo_on_punch), stringResource(R.string.company_photo_on_punch_helper), state.photoOnPunch) { onEvent(CompanyEvent.PhotoOnPunch(it)) }
        }
    }
}

@Composable
private fun overtimeLabel(rule: OvertimeRule): String = stringResource(
    when (rule) {
        OvertimeRule.None -> R.string.ot_none
        OvertimeRule.Daily8 -> R.string.ot_daily8
        OvertimeRule.Weekly40 -> R.string.ot_weekly40
        OvertimeRule.Daily8Weekly40 -> R.string.ot_both
    },
)

@Composable
private fun SwitchRow(title: String, helper: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
