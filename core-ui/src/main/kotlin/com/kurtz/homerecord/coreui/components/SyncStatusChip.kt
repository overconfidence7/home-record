package com.kurtz.homerecord.coreui.components

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.sync.RefusedChange
import com.kurtz.homerecord.core.sync.SyncPhase
import com.kurtz.homerecord.coreui.R
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme

/**
 * Pending-sync count and state, shown on Today (AC-F52); tapping asks for a sync now. [refused] counts changes the
 * server turned down (cr:sync-poison-op); while there are any and nothing more urgent shows, the chip says so.
 */
@Composable
fun SyncStatusChip(phase: SyncPhase, pending: Int, onClick: () -> Unit, modifier: Modifier = Modifier, refused: Int = 0) {
    val label = when {
        phase == SyncPhase.Syncing -> stringResource(R.string.sync_syncing)
        phase == SyncPhase.Offline && pending > 0 -> pluralStringResource(R.plurals.sync_waiting_offline, pending, pending)
        phase == SyncPhase.Offline -> stringResource(R.string.sync_offline)
        phase == SyncPhase.Failed -> stringResource(R.string.sync_failed)
        refused > 0 -> pluralStringResource(R.plurals.sync_refused, refused, refused)
        pending > 0 -> pluralStringResource(R.plurals.sync_waiting, pending, pending)
        else -> stringResource(R.string.sync_up_to_date)
    }
    val icon = when {
        phase == SyncPhase.Syncing -> Icons.Rounded.CloudSync
        phase == SyncPhase.Offline -> Icons.Rounded.CloudOff
        phase == SyncPhase.Failed -> Icons.Rounded.ErrorOutline
        refused > 0 -> Icons.Rounded.SyncProblem
        pending > 0 -> Icons.Rounded.CloudSync
        else -> Icons.Rounded.CloudDone
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = modifier,
    )
}

/**
 * The sync status both apps show: the chip, and when changes were refused, a tap lists them in [RefusedChangesDialog]
 * instead of syncing, so a set-aside change is never silent (cr:sync-poison-op).
 */
@Composable
fun SyncStatus(
    phase: SyncPhase,
    pending: Int,
    refused: List<RefusedChange>,
    onSyncNow: () -> Unit,
    onClearRefused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRefused by rememberSaveable { mutableStateOf(false) }
    val prefix = LocalTagPrefix.current
    SyncStatusChip(
        phase, pending,
        onClick = { if (refused.isNotEmpty() && phase != SyncPhase.Syncing) showRefused = true else onSyncNow() },
        modifier = modifier.testTag("${prefix}_sync_status"),
        refused = refused.size,
    )
    if (showRefused && refused.isNotEmpty()) {
        RefusedChangesDialog(refused, onClear = { showRefused = false; onClearRefused() }, onDismiss = { showRefused = false })
    }
}

/** Lists the changes the server refused: what kind of record and when it was changed on this phone. */
@Composable
fun RefusedChangesDialog(changes: List<RefusedChange>, onClear: () -> Unit, onDismiss: () -> Unit) {
    val prefix = LocalTagPrefix.current
    val context = LocalContext.current
    AlertDialog(
        modifier = Modifier.windowTestTags().testTag("${prefix}_sync_refused"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_refused_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.sync_refused_body), style = MaterialTheme.typography.bodyMedium)
                changes.forEach { change ->
                    Column(Modifier.testTag("${prefix}_sync_refused_row")) {
                        Text(stringResource(refusedEntityLabel(change.entity)), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            DateUtils.formatDateTime(
                                context, change.updatedAt,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_sync_refused_clear")) {
                Text(stringResource(R.string.sync_refused_clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_sync_refused_close")) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

/** The plain name of a synced table, for the refused list; a table without one reads "Other change". */
@StringRes
fun refusedEntityLabel(entity: String): Int = when (entity) {
    "home" -> R.string.sync_entity_home
    "floor" -> R.string.sync_entity_floor
    "room" -> R.string.sync_entity_room
    "wall", "wall_photo" -> R.string.sync_entity_wall
    "opening" -> R.string.sync_entity_opening
    "stair" -> R.string.sync_entity_stair
    "item", "warranty" -> R.string.sync_entity_item
    "consumable", "maintenance_task", "service_record" -> R.string.sync_entity_upkeep
    "contact" -> R.string.sync_entity_contact
    "document", "job_file" -> R.string.sync_entity_file
    "panel", "breaker", "breaker_link" -> R.string.sync_entity_panel
    "job", "job_crew", "assignment" -> R.string.sync_entity_job
    "client" -> R.string.sync_entity_client
    "punch", "shift" -> R.string.sync_entity_punch
    "photo" -> R.string.sync_entity_photo
    "note" -> R.string.sync_entity_note
    "task" -> R.string.sync_entity_task
    "daily_log" -> R.string.sync_entity_daily_log
    "change_order" -> R.string.sync_entity_change_order
    "material" -> R.string.sync_entity_material
    else -> R.string.sync_entity_other
}

@ThemePreviews
@Composable
fun SyncStatusChipPreview() = HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
    Surface {
        Column(Modifier.padding(16.dp)) {
            SyncStatusChip(SyncPhase.Idle, 0, {})
            SyncStatusChip(SyncPhase.Idle, 3, {})
            SyncStatusChip(SyncPhase.Syncing, 3, {})
            SyncStatusChip(SyncPhase.Offline, 12, {})
            SyncStatusChip(SyncPhase.Failed, 2, {})
            SyncStatusChip(SyncPhase.Idle, 0, {}, refused = 2)
        }
    }
}

/** Fixed times (Oct 28, 2026 in the afternoon, UTC) keep the screenshot stable. */
internal val previewRefused = listOf(
    RefusedChange("opening", "o1", 1_793_200_440_000L),
    RefusedChange("punch", "p1", 1_793_194_800_000L),
)

@ThemePreviews
@Composable
fun RefusedChangesDialogPreview() = HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
    RefusedChangesDialog(previewRefused, onClear = {}, onDismiss = {})
}
