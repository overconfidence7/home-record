package com.kurtz.homerecord.fieldlog.changes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.fieldlog.R
import java.text.NumberFormat
import java.util.Locale

/** Dollars for a change order amount: 33500 → "$335.00". */
internal fun dollars(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.US).format(cents / 100.0)

@Composable
internal fun statusLabel(status: ChangeOrderStatus): String = stringResource(
    when (status) {
        ChangeOrderStatus.DRAFT -> R.string.changes_status_draft
        ChangeOrderStatus.SIGNED -> R.string.changes_status_signed
        ChangeOrderStatus.DECLINED -> R.string.changes_status_declined
    },
)

/**
 * The Changes tab of FL-Job (FL-ChangeOrder, AC-F37): change orders newest first with status, client and total. Owner,
 * Office and the job's Lead get New change order and open one to edit it; Crew see the list only (AC-F3).
 */
@Composable
fun ChangesScreen(state: ChangesUiState, onEvent: (ChangesEvent) -> Unit, modifier: Modifier = Modifier) {
    if (state.loading) { LoadingState(modifier.fillMaxSize()); return }
    if (state.loadFailed) {
        ErrorState(
            stringResource(R.string.changes_load_failed), { onEvent(ChangesEvent.Retry) }, modifier.fillMaxSize(),
            retryModifier = Modifier.testTag("fl_changes_retry"),
        )
        return
    }
    Box(modifier.fillMaxSize()) {
        if (state.rows.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.RequestQuote,
                headline = stringResource(if (state.canManage) R.string.changes_empty_title else R.string.changes_empty_crew_title),
                body = stringResource(if (state.canManage) R.string.changes_empty else R.string.changes_empty_crew),
                actionLabel = if (state.canManage) stringResource(R.string.changes_add) else null,
                onAction = { onEvent(ChangesEvent.Add) },
                modifier = Modifier.padding(bottom = 88.dp).testTag("fl_changes_empty"),
                actionModifier = Modifier.heightIn(min = 56.dp).testTag("fl_changes_empty_add"),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().testTag("fl_changes_list"), contentPadding = PaddingValues(bottom = 88.dp)) {
                state.draftsTotalCents?.let { total ->
                    item(key = "drafts-total") {
                        Text(
                            stringResource(R.string.changes_total_drafts, dollars(total)),
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("fl_changes_drafts_total"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.rows, key = { it.id }) { row -> ChangeListRow(row, state.canManage, onEvent) }
            }
        }
        if (state.canManage) {
            val label = stringResource(R.string.changes_add)
            ExtendedFloatingActionButton(
                onClick = { onEvent(ChangesEvent.Add) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(label) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).heightIn(min = 56.dp)
                    .semantics { contentDescription = label }.testTag("fl_changes_add"),
            )
        }
    }
}

@Composable
private fun ChangeListRow(row: ChangeRow, canOpen: Boolean, onEvent: (ChangesEvent) -> Unit) {
    val status = statusLabel(row.status)
    val lines = pluralStringResource(R.plurals.changes_lines, row.lineCount, row.lineCount)
    val supporting = row.clientName?.let { stringResource(R.string.changes_row_client, status, it, lines) }
        ?: stringResource(R.string.changes_row_supporting, status, lines)
    RecordListItem(
        headline = row.title,
        supporting = supporting,
        leading = {
            Icon(
                when (row.status) {
                    ChangeOrderStatus.DRAFT -> Icons.Rounded.EditNote
                    ChangeOrderStatus.SIGNED -> Icons.Rounded.TaskAlt
                    ChangeOrderStatus.DECLINED -> Icons.Rounded.DoNotDisturbOn
                },
                contentDescription = null,
            )
        },
        trailing = { Text(dollars(row.totalCents), style = MaterialTheme.typography.titleMedium) },
        onClick = if (canOpen) ({ onEvent(ChangesEvent.Open(row.id)) }) else null,
        modifier = Modifier.testTag("fl_changes_row_${row.id}"),
    )
}
