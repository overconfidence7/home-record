package com.kurtz.homerecord.coreui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.auth.AuthResult
import com.kurtz.homerecord.coreui.R
import com.kurtz.homerecord.coreui.components.LabeledField

@Composable
fun SignInScreen(state: SignInUiState, onEvent: (SignInEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.sign_in_intro), style = MaterialTheme.typography.bodyLarge)
        val error = state.error?.let { errorText(it) }
        val tag = LocalTagPrefix.current
        when (state.step) {
            SignInStep.Email -> {
                LabeledField(
                    label = stringResource(R.string.sign_in_email),
                    value = state.email,
                    onValueChange = { onEvent(SignInEvent.EmailChanged(it)) },
                    modifier = Modifier.testTag("${tag}_sign_in_email"),
                    error = error,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                )
                Button(
                    onClick = { onEvent(SignInEvent.SendCode) },
                    enabled = state.emailValid && !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("${tag}_sign_in_send"),
                ) { ButtonLabel(stringResource(R.string.sign_in_send_code), state.busy) }
                OutlinedButton(
                    onClick = { onEvent(SignInEvent.GoogleSignIn) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("${tag}_sign_in_google"),
                ) { Text(stringResource(R.string.sign_in_google)) }
            }
            SignInStep.Code -> {
                Text(stringResource(R.string.sign_in_code_sent, state.email), style = MaterialTheme.typography.bodyMedium)
                LabeledField(
                    label = stringResource(R.string.sign_in_code),
                    value = state.code,
                    onValueChange = { onEvent(SignInEvent.CodeChanged(it)) },
                    modifier = Modifier.testTag("${tag}_sign_in_code"),
                    helper = stringResource(R.string.sign_in_code_helper),
                    error = error,
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                )
                Button(
                    onClick = { onEvent(SignInEvent.VerifyCode) },
                    enabled = state.codeValid && !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("${tag}_sign_in_verify"),
                ) { ButtonLabel(stringResource(R.string.sign_in_verify), state.busy) }
                TextButton(
                    onClick = { onEvent(SignInEvent.ChangeEmail) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_sign_in_change_email"),
                ) { Text(stringResource(R.string.sign_in_change_email)) }
            }
        }
    }
}

@Composable
private fun ButtonLabel(text: String, busy: Boolean) {
    if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).heightIn(max = 20.dp), strokeWidth = 2.dp)
    Text(text)
}

@Composable
private fun errorText(result: AuthResult): String? = when (result) {
    AuthResult.InvalidCode -> stringResource(R.string.sign_in_err_invalid)
    AuthResult.CodeExpired -> stringResource(R.string.sign_in_err_expired)
    AuthResult.TooManyAttempts -> stringResource(R.string.sign_in_err_locked)
    AuthResult.RateLimited -> stringResource(R.string.sign_in_err_rate_limited)
    AuthResult.Offline -> stringResource(R.string.sign_in_err_offline)
    AuthResult.Failed -> stringResource(R.string.sign_in_err_failed)
    AuthResult.Success -> null
}
