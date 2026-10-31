package com.kurtz.homerecord.fieldlog.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.FormScaffold
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.fieldlog.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Full-screen weather form for a day's log (rule 15: four fields): conditions as a list (ten, so not segments,
 * rule 22), high, low, and rain or snow. Close asks before dropping typed changes (rule 3).
 */
@Composable
fun LogWeatherScreen(state: LogWeatherUiState, onEvent: (LogWeatherEvent) -> Unit, modifier: Modifier = Modifier) {
    val check = state.check
    FormScaffold(
        title = stringResource(R.string.log_weather_form_title),
        dirty = state.dirty && !state.done,
        saveEnabled = !state.working && state.draft.conditions != null,
        onClose = { onEvent(LogWeatherEvent.Close) },
        onSave = { onEvent(LogWeatherEvent.Save) },
        modifier = modifier.testTag("fl_log_weather_form"),
        placeholder = when {
            state.loading -> { m -> LoadingState(m.fillMaxSize()) }
            state.failed -> { m -> ErrorState(stringResource(R.string.log_weather_form_failed), { onEvent(LogWeatherEvent.Retry) }, m.fillMaxSize()) }
            else -> null
        },
    ) {
        Text(
            state.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            Text(
                stringResource(com.kurtz.homerecord.coreui.R.string.required_label, stringResource(R.string.log_weather_conditions)),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            if (state.showErrors && check.conditionsMissing) {
                Text(
                    stringResource(R.string.log_weather_conditions_error), Modifier.padding(top = 4.dp).testTag("fl_log_weather_conditions_error"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
            }
            Column(Modifier.selectableGroup().padding(top = 8.dp)) {
                LogWeatherForm.CONDITIONS.forEach { key ->
                    val selected = state.draft.conditions == key
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = selected, onClick = { onEvent(LogWeatherEvent.Conditions(key)) }, role = Role.RadioButton)
                            .testTag("fl_log_weather_cond_$key"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Icon(conditionsIcon(key), contentDescription = null, Modifier.padding(start = 16.dp))
                        Text(stringResource(conditionsLabel(key)), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        LabeledField(
            label = stringResource(R.string.log_weather_high),
            value = state.draft.high,
            onValueChange = { onEvent(LogWeatherEvent.High(it)) },
            modifier = Modifier.testTag("fl_log_weather_high"),
            error = check.high?.let { tempError(it) },
            keyboardType = KeyboardType.Number,
        )
        LabeledField(
            label = stringResource(R.string.log_weather_low),
            value = state.draft.low,
            onValueChange = { onEvent(LogWeatherEvent.Low(it)) },
            modifier = Modifier.testTag("fl_log_weather_low"),
            error = check.low?.let { tempError(it) },
            keyboardType = KeyboardType.Number,
        )
        LabeledField(
            label = stringResource(R.string.log_weather_precip),
            value = state.draft.precip,
            onValueChange = { onEvent(LogWeatherEvent.Precip(it)) },
            modifier = Modifier.testTag("fl_log_weather_precip"),
            helper = stringResource(R.string.log_weather_precip_help),
            error = check.precip?.let {
                stringResource(if (it == WeatherFieldError.NOT_A_NUMBER) R.string.log_weather_precip_not_number else R.string.log_weather_precip_range)
            },
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        )
        if (state.handEntered) {
            HorizontalDivider()
            Text(stringResource(R.string.log_weather_hand_entered), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { onEvent(LogWeatherEvent.LookUpAgain) },
                enabled = !state.working,
                modifier = Modifier.heightIn(min = 48.dp).testTag("fl_log_weather_lookup"),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.log_weather_look_up))
            }
        }
    }
}

@Composable
private fun tempError(e: WeatherFieldError): String = stringResource(
    when (e) {
        WeatherFieldError.NOT_A_NUMBER -> R.string.log_weather_temp_not_number
        WeatherFieldError.OUT_OF_RANGE -> R.string.log_weather_temp_range
        WeatherFieldError.LOW_ABOVE_HIGH -> R.string.log_weather_low_above_high
    },
)
