package com.kurtz.homerecord.fieldlog.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.kurtz.homerecord.core.bundle.HandoffSendFailure
import com.kurtz.homerecord.coreui.components.LabeledField
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.bundle.HandoffSummary
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SectionHeader
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R

/**
 * FL-Handoff: the summary of what the homeowner gets (rooms, walls with photos, overlays, items, documents, signed
 * change orders), Send to homeowner by email (AC-B40) and Export `.homerecord` through the share sheet (AC-F51).
 */
@Composable
fun HandoffScreen(state: HandoffUiState, onEvent: (HandoffEvent) -> Unit) {
    ScreenScaffold(
        title = stringResource(R.string.handoff_title),
        subtitle = state.jobName.ifBlank { null },
        onBack = { onEvent(HandoffEvent.Back) },
    ) { padding ->
        val summary = state.summary
        when {
            state.loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            state.missing || summary == null -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                headline = stringResource(R.string.handoff_missing_title),
                body = stringResource(R.string.handoff_missing_body),
                actionLabel = stringResource(R.string.handoff_back),
                onAction = { onEvent(HandoffEvent.Back) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> HandoffContent(summary, state, onEvent, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun HandoffContent(summary: HandoffSummary, state: HandoffUiState, onEvent: (HandoffEvent) -> Unit, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Text(
            stringResource(R.string.handoff_intro),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        SectionHeader(stringResource(R.string.handoff_included))
        if (summary.isEmpty) {
            RecordListItem(
                headline = stringResource(R.string.handoff_empty_title),
                supporting = stringResource(R.string.handoff_empty_body),
                leading = { Icon(Icons.Rounded.HomeWork, contentDescription = null) },
                trailing = null,
                modifier = Modifier.testTag("fl_handoff_empty"),
            )
        } else {
            CountRow("rooms", Icons.Rounded.MeetingRoom, R.string.handoff_rooms, summary.rooms.toString())
            CountRow(
                "walls", Icons.Rounded.PhotoCamera, R.string.handoff_walls, summary.wallsWithPhotos.toString(),
                supporting = pluralStringResource(R.plurals.handoff_wall_photos, summary.wallPhotos, summary.wallPhotos),
            )
            CountRow("overlays", Icons.Rounded.Polyline, R.string.handoff_overlays, summary.overlays.toString())
            CountRow("items", Icons.Rounded.Inventory2, R.string.handoff_items, summary.items.toString())
            CountRow("documents", Icons.Rounded.Description, R.string.handoff_documents, summary.documents.toString())
            CountRow(
                "change_orders", Icons.Rounded.Draw, R.string.handoff_change_orders, summary.changeOrders.toString(),
                supporting = stringResource(R.string.handoff_change_orders_note),
            )
        }
        SectionHeader(stringResource(R.string.handoff_send_title))
        SendSection(state, enabled = !summary.isEmpty, onEvent = onEvent)
        SectionHeader(stringResource(R.string.handoff_export_title))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onEvent(HandoffEvent.Export) },
                enabled = !summary.isEmpty && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("fl_handoff_export"),
            ) {
                Text(stringResource(R.string.handoff_export))
            }
            Text(
                stringResource(R.string.handoff_export_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.exporting) {
                Progress(R.string.handoff_making, "fl_handoff_making")
            } else if (state.failed) {
                Text(
                    stringResource(R.string.handoff_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("fl_handoff_failed"),
                )
            }
        }
    }
}

/** Send to homeowner (AC-B40): the address, Send, then where it went and until when. */
@Composable
private fun SendSection(state: HandoffUiState, enabled: Boolean, onEvent: (HandoffEvent) -> Unit) {
    val sentTo = state.sentTo
    if (sentTo != null) {
        val until = state.sentUntil?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)).orEmpty()
        RecordListItem(
            headline = stringResource(R.string.handoff_sent_title, sentTo),
            supporting = stringResource(R.string.handoff_sent_body, until),
            leading = { Icon(Icons.Rounded.MarkEmailRead, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailing = null,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("fl_handoff_sent"),
        )
        TextButton(
            onClick = { onEvent(HandoffEvent.SendAnother) },
            modifier = Modifier.padding(horizontal = 4.dp).heightIn(min = 48.dp).testTag("fl_handoff_send_again"),
        ) {
            Text(stringResource(R.string.handoff_send_again))
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledField(
            label = stringResource(R.string.handoff_email_label),
            value = state.email,
            onValueChange = { onEvent(HandoffEvent.EmailChanged(it)) },
            modifier = Modifier.testTag("fl_handoff_email"),
            helper = stringResource(R.string.handoff_email_help),
            error = if (state.emailInvalid) stringResource(R.string.handoff_email_invalid) else null,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Send,
            keyboardActions = KeyboardActions(onSend = { onEvent(HandoffEvent.Send) }),
        )
        Button(
            onClick = { onEvent(HandoffEvent.Send) },
            enabled = enabled && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("fl_handoff_send"),
        ) {
            Text(stringResource(R.string.handoff_send))
        }
        if (state.sending) {
            Progress(R.string.handoff_sending, "fl_handoff_sending")
        } else {
            state.sendFailure?.let { failure ->
                Row(
                    Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("fl_handoff_send_error"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(
                            when (failure) {
                                HandoffSendFailure.Offline -> R.string.handoff_send_offline
                                HandoffSendFailure.TooLarge -> R.string.handoff_send_too_large
                                HandoffSendFailure.Failed -> R.string.handoff_send_failed
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    if (failure != HandoffSendFailure.TooLarge) {
                        TextButton(
                            onClick = { onEvent(HandoffEvent.Send) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("fl_handoff_send_retry"),
                        ) {
                            Text(stringResource(R.string.handoff_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Progress(label: Int, tag: String) {
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CountRow(key: String, icon: ImageVector, label: Int, count: String, supporting: String? = null) {
    RecordListItem(
        headline = stringResource(label),
        supporting = supporting,
        leading = { Icon(icon, contentDescription = null) },
        trailing = { Text(count, style = MaterialTheme.typography.titleMedium) },
        modifier = Modifier.testTag("fl_handoff_count_$key"),
    )
}
