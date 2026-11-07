package com.kurtz.homerecord.fieldlog.sync

import android.text.format.DateUtils
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.sync.ConflictEntry
import com.kurtz.homerecord.core.sync.ConflictOutcome
import com.kurtz.homerecord.core.sync.SyncPhase
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SectionHeader
import com.kurtz.homerecord.coreui.components.refusedEntityLabel
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R

/**
 * FL-Sync (AC-F52, AC-F53): the top says whether every change on this phone has reached the server, with Sync now;
 * below, the conflict log lists each change sync kept out (a newer edit from another phone, the office's punch edit,
 * a change the server refused, a row this phone couldn't save), newest first. Clear log empties the list only.
 */
@Composable
fun SyncLogScreen(state: SyncLogUiState, onEvent: (SyncLogEvent) -> Unit, modifier: Modifier = Modifier) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(
        title = stringResource(R.string.sync_log_title),
        onBack = { onEvent(SyncLogEvent.Back) },
        actions = {
            if (state.entries.isNotEmpty()) {
                TextButton({ confirmClear = true }, Modifier.heightIn(min = 48.dp).testTag("fl_sync_clear")) {
                    Text(stringResource(R.string.sync_log_clear))
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@ScreenScaffold
        }
        if (state.loadFailed) {
            ErrorState(
                stringResource(R.string.sync_log_load_failed), { onEvent(SyncLogEvent.Retry) }, Modifier.padding(padding),
                retryModifier = Modifier.testTag("fl_sync_retry"),
            )
            return@ScreenScaffold
        }
        LazyColumn(
            modifier.padding(padding).fillMaxSize().testTag("fl_sync_list"),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "status") { SyncStatusBlock(state, onEvent) }
            item(key = "header") { SectionHeader(stringResource(R.string.sync_log_conflicts)) }
            if (state.entries.isEmpty()) {
                item(key = "none") {
                    RecordListItem(
                        headline = stringResource(R.string.sync_log_none),
                        supporting = stringResource(R.string.sync_log_none_body),
                        supportingMaxLines = 4,
                        leading = { Icon(Icons.Rounded.CloudDone, contentDescription = null) },
                        trailing = null,
                        modifier = Modifier.testTag("fl_sync_none"),
                    )
                }
            }
            itemsIndexed(state.entries, key = { i, e -> "${e.entity}/${e.entityId}/${e.at}/${e.outcome}/$i" }) { _, entry ->
                ConflictRow(entry, Modifier.animateItem())
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.sync_log_clear_title)) },
            text = { Text(stringResource(R.string.sync_log_clear_body)) },
            confirmButton = {
                TextButton({ confirmClear = false; onEvent(SyncLogEvent.Clear) }, Modifier.testTag("fl_sync_clear_confirm")) {
                    Text(stringResource(R.string.sync_log_clear))
                }
            },
            dismissButton = {
                TextButton({ confirmClear = false }, Modifier.testTag("fl_sync_clear_cancel")) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SyncStatusBlock(state: SyncLogUiState, onEvent: (SyncLogEvent) -> Unit) {
    val pending = state.pending
    val (icon, headline) = when {
        state.phase == SyncPhase.Syncing -> Icons.Rounded.CloudSync to stringResource(R.string.sync_log_syncing)
        state.phase == SyncPhase.Offline && pending > 0 ->
            Icons.Rounded.CloudOff to pluralStringResource(R.plurals.sync_log_saved_here, pending, pending)
        state.phase == SyncPhase.Offline -> Icons.Rounded.CloudOff to stringResource(R.string.sync_log_offline)
        state.phase == SyncPhase.Failed -> Icons.Rounded.ErrorOutline to stringResource(R.string.sync_log_failed)
        pending > 0 -> Icons.Rounded.CloudSync to pluralStringResource(R.plurals.sync_log_waiting, pending, pending)
        else -> Icons.Rounded.CloudDone to stringResource(R.string.sync_log_up_to_date)
    }
    val body = when {
        state.phase == SyncPhase.Offline -> stringResource(R.string.sync_log_offline_body)
        state.phase == SyncPhase.Failed -> stringResource(R.string.sync_log_failed_body)
        pending > 0 || state.phase == SyncPhase.Syncing -> stringResource(R.string.sync_log_waiting_body)
        else -> stringResource(R.string.sync_log_up_to_date_body)
    }
    RecordListItem(
        headline = headline,
        supporting = body,
        supportingMaxLines = 4,
        leading = { Icon(icon, contentDescription = null) },
        trailing = null,
        modifier = Modifier.testTag("fl_sync_status"),
    )
    FilledTonalButton(
        onClick = { onEvent(SyncLogEvent.SyncNow) },
        enabled = state.phase != SyncPhase.Syncing,
        modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp).testTag("fl_sync_now"),
    ) {
        Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.sync_log_sync_now))
    }
}

@Composable
private fun ConflictRow(entry: ConflictEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val kind = stringResource(refusedEntityLabel(entry.entity))
    val fields = entry.fields.joinToString(", ")
    val outcome = when (entry.outcome) {
        ConflictOutcome.NewerKept ->
            if (fields.isEmpty()) stringResource(R.string.sync_log_newer_kept) else stringResource(R.string.sync_log_newer_kept_fields, fields)
        // The sentence already says "this time"; only the other fields are worth naming.
        ConflictOutcome.OfficeKept -> {
            val other = entry.fields.filter { it != "time" }.joinToString(", ")
            if (other.isEmpty()) stringResource(R.string.sync_log_office_kept) else stringResource(R.string.sync_log_office_kept_fields, other)
        }
        ConflictOutcome.Refused -> stringResource(R.string.sync_log_refused)
        ConflictOutcome.NotStored -> stringResource(R.string.sync_log_not_stored)
    }
    val time = DateUtils.formatDateTime(
        context, entry.at, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
    RecordListItem(
        overline = kind.takeIf { entry.label != null },
        headline = entry.label?.let { if (entry.entity == "daily_log") dayLabel(it) else it } ?: kind,
        supporting = "$outcome\n$time",
        supportingMaxLines = 6,
        leading = { Icon(entry.outcome.icon(), contentDescription = null) },
        trailing = null,
        modifier = modifier.testTag("fl_sync_entry"),
    )
}

private fun ConflictOutcome.icon(): ImageVector = when (this) {
    ConflictOutcome.NewerKept -> Icons.Rounded.History
    ConflictOutcome.OfficeKept -> Icons.Rounded.AdminPanelSettings
    ConflictOutcome.Refused, ConflictOutcome.NotStored -> Icons.Rounded.SyncProblem
}

/** A daily log is named by its day; show "Oct 27, 2026" instead of the stored ISO date. */
private fun dayLabel(iso: String): String =
    runCatching { java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)) }
        .getOrDefault(iso)
