package com.kurtz.homerecord.fieldlog.changes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.SignaturePad
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R

/**
 * Sign on phone (FL-ChangeOrder, AC-F38): the order's title and total, who is signing (the client's name to start),
 * a full-width signature pad, Clear and Sign. Sign turns on once there is a name and a real signature.
 */
@Composable
fun ChangeOrderSignScreen(state: ChangeOrderSignUiState, onEvent: (ChangeOrderSignEvent) -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(
        title = stringResource(R.string.co_sign_title),
        subtitle = state.title.takeIf { it.isNotBlank() },
        onBack = { onEvent(ChangeOrderSignEvent.Back) },
    ) { padding ->
        val inner = modifier.fillMaxSize().padding(padding).testTag("fl_co_sign")
        when {
            state.loading -> LoadingState(inner)
            state.failed -> ErrorState(stringResource(R.string.co_sign_failed), { onEvent(ChangeOrderSignEvent.Retry) }, inner)
            else -> BoxWithConstraints(inner) {
                val padHeight = (maxHeight * 0.45f).coerceAtLeast(200.dp)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(
                            R.string.co_sign_summary,
                            dollars(state.totalCents),
                            pluralStringResource(R.plurals.co_sign_lines, state.lineCount, state.lineCount),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("fl_co_sign_summary"),
                    )
                    LabeledField(
                        label = stringResource(R.string.co_sign_name), value = state.signerName,
                        onValueChange = { onEvent(ChangeOrderSignEvent.SignerName(it)) },
                        modifier = Modifier.testTag("fl_co_sign_name"), required = true,
                        helper = stringResource(R.string.co_sign_name_help),
                        error = if (state.nameMissing) stringResource(R.string.co_sign_name_error) else null,
                        imeAction = ImeAction.Done,
                    )
                    SignaturePad(
                        strokes = state.strokes,
                        onStroke = { onEvent(ChangeOrderSignEvent.Stroke(it)) },
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth().height(padHeight).testTag("fl_co_sign_pad"),
                    )
                    Text(
                        stringResource(R.string.co_sign_agree, state.signerName.trim().ifBlank { stringResource(R.string.co_sign_agree_nobody) }, dollars(state.totalCents)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.saveFailed) {
                        Text(
                            stringResource(R.string.co_sign_save_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("fl_co_sign_error"),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onEvent(ChangeOrderSignEvent.Clear) },
                            enabled = state.strokes.isNotEmpty() && !state.working,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("fl_co_sign_clear"),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.co_sign_clear))
                        }
                        Button(
                            onClick = { onEvent(ChangeOrderSignEvent.Sign) },
                            enabled = state.canSign,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("fl_co_sign_sign"),
                        ) {
                            Icon(Icons.Rounded.Draw, contentDescription = null, Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.co_sign_sign))
                        }
                    }
                }
            }
        }
    }
}
