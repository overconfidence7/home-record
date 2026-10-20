package com.kurtz.homerecord.fieldlog.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.EnumPicker
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.fieldlog.R

/** FL-Welcome after sign-in: create a company or join one with a code. */
@Composable
fun WelcomeScreen(state: WelcomeUiState, onEvent: (WelcomeEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.welcome_intro), style = MaterialTheme.typography.bodyLarge)
        EnumPicker(
            options = WelcomeMode.entries,
            selected = state.mode,
            onSelect = { onEvent(WelcomeEvent.ModeChanged(it)) },
            labelOf = {
                stringResource(if (it == WelcomeMode.Create) R.string.welcome_mode_create else R.string.welcome_mode_join)
            },
        )
        val error = state.error?.let { failureText(it) }
        when (state.mode) {
            WelcomeMode.Create -> {
                LabeledField(
                    label = stringResource(R.string.company_name),
                    value = state.name,
                    onValueChange = { onEvent(WelcomeEvent.NameChanged(it)) },
                    modifier = Modifier.testTag("fl_welcome_name"),
                    required = true,
                    error = error,
                )
                LabeledField(
                    label = stringResource(R.string.company_trade),
                    value = state.trade,
                    onValueChange = { onEvent(WelcomeEvent.TradeChanged(it)) },
                    modifier = Modifier.testTag("fl_welcome_trade"),
                    helper = stringResource(R.string.company_trade_helper),
                )
                LabeledField(
                    label = stringResource(R.string.company_phone),
                    value = state.phone,
                    onValueChange = { onEvent(WelcomeEvent.PhoneChanged(it)) },
                    modifier = Modifier.testTag("fl_welcome_phone"),
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                )
            }
            WelcomeMode.Join -> LabeledField(
                label = stringResource(R.string.invite_code),
                value = state.code,
                onValueChange = { onEvent(WelcomeEvent.CodeChanged(it)) },
                modifier = Modifier.testTag("fl_welcome_code"),
                helper = stringResource(R.string.invite_code_helper),
                error = error,
                imeAction = ImeAction.Done,
            )
        }
        Button(
            onClick = { onEvent(WelcomeEvent.Submit) },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("fl_welcome_submit"),
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(if (state.mode == WelcomeMode.Create) R.string.welcome_create else R.string.welcome_join))
            }
        }
        TextButton(onClick = { onEvent(WelcomeEvent.SignOut) }, modifier = Modifier.heightIn(min = 48.dp).testTag("fl_welcome_sign_out")) {
            Text(stringResource(R.string.welcome_wrong_account))
        }
    }
}
