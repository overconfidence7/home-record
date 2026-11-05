package com.kurtz.homerecord.housemanual.importing

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SectionHeader
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

/** HM-Import: what a Field Log handoff brings (counts per kind), the conflicts with Keep mine / Use imported, Import (AC-H17). */
@Composable
fun ImportScreen(state: ImportUiState, onEvent: (ImportEvent) -> Unit) {
    ScreenScaffold(
        title = stringResource(R.string.import_title),
        subtitle = state.homeName.ifBlank { null },
        onBack = { onEvent(ImportEvent.Back) },
    ) { padding ->
        val fill = Modifier.fillMaxSize().padding(padding)
        val error = state.error
        when {
            state.loading -> LoadingState(fill)
            state.done -> EmptyState(
                icon = Icons.Rounded.TaskAlt,
                headline = stringResource(R.string.import_done_title),
                body = stringResource(R.string.import_done_body),
                actionLabel = stringResource(R.string.import_done_action),
                onAction = { onEvent(ImportEvent.Finish) },
                modifier = fill.testTag("hm_import_done"),
                actionModifier = Modifier.testTag("hm_import_finish"),
            )
            error != null && error != ImportError.Failed -> EmptyState(
                icon = Icons.Rounded.ErrorOutline,
                headline = stringResource(R.string.import_error_title),
                body = stringResource(errorText(error)),
                actionLabel = stringResource(R.string.import_close),
                onAction = { onEvent(ImportEvent.Back) },
                modifier = fill.testTag("hm_import_error"),
                actionModifier = Modifier.testTag("hm_import_close"),
            )
            else -> ImportContent(state, onEvent, fill)
        }
    }
}

private fun errorText(error: ImportError): Int = when (error) {
    ImportError.NewerVersion -> R.string.import_error_newer
    ImportError.NotABundle -> R.string.import_error_not_bundle
    ImportError.TooLarge -> R.string.import_error_too_large
    ImportError.Unreadable -> R.string.import_error_unreadable
    ImportError.Failed -> R.string.import_failed
}

@Composable
private fun ImportContent(state: ImportUiState, onEvent: (ImportEvent) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.import_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            SectionHeader(stringResource(R.string.import_new_header))
            if (state.counts.isEmpty()) {
                RecordListItem(
                    headline = stringResource(R.string.import_nothing_new_title),
                    supporting = stringResource(R.string.import_nothing_new_body),
                    leading = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                    trailing = null,
                    modifier = Modifier.testTag("hm_import_nothing_new"),
                )
            }
            state.counts.forEach { c ->
                RecordListItem(
                    headline = stringResource(kindLabel(c.kind)),
                    leading = { Icon(kindIcon(c.kind), contentDescription = null) },
                    trailing = { Text(c.count.toString(), style = MaterialTheme.typography.titleMedium) },
                    modifier = Modifier.testTag("hm_import_count_${c.kind.type}"),
                )
            }
            if (state.conflicts.isNotEmpty()) {
                SectionHeader(stringResource(R.string.import_conflicts_header))
                Text(
                    pluralStringResource(R.plurals.import_conflicts_help, state.conflicts.size, state.conflicts.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                state.conflicts.forEach { ConflictRow(it, enabled = !state.importing, onEvent = onEvent) }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.importing) {
                Row(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("hm_import_importing"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.import_importing), style = MaterialTheme.typography.bodyMedium)
                }
            } else if (state.error == ImportError.Failed) {
                Text(
                    stringResource(R.string.import_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("hm_import_failed"),
                )
            }
            Button(
                onClick = { onEvent(ImportEvent.Import) },
                enabled = !state.importing && (state.counts.isNotEmpty() || state.conflicts.any { it.useImported }),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("hm_import_confirm"),
            ) {
                Text(stringResource(if (state.error == ImportError.Failed) R.string.import_retry else R.string.import_confirm))
            }
        }
    }
}

@Composable
private fun ConflictRow(conflict: ImportConflict, enabled: Boolean, onEvent: (ImportEvent) -> Unit) {
    val kind = stringResource(kindSingular(conflict.kind))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("hm_import_conflict_${conflict.id}")) {
        Text(conflict.name.ifBlank { kind }, style = MaterialTheme.typography.bodyLarge)
        if (conflict.name.isNotBlank()) {
            Text(kind, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            listOf(false to R.string.import_keep_mine, true to R.string.import_use_imported).forEachIndexed { i, (useImported, label) ->
                SegmentedButton(
                    selected = conflict.useImported == useImported,
                    onClick = { onEvent(ImportEvent.Choose(conflict.type, conflict.id, useImported)) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp)
                        .testTag(if (useImported) "hm_import_use_imported_${conflict.id}" else "hm_import_keep_mine_${conflict.id}"),
                ) { Text(stringResource(label)) }
            }
        }
    }
}

private fun kindLabel(kind: ImportKind): Int = when (kind) {
    ImportKind.Floors -> R.string.import_kind_floors
    ImportKind.Rooms -> R.string.import_kind_rooms
    ImportKind.WallPhotos -> R.string.import_kind_wall_photos
    ImportKind.Overlays -> R.string.import_kind_overlays
    ImportKind.Items -> R.string.import_kind_items
    ImportKind.Documents -> R.string.import_kind_documents
    ImportKind.ChangeOrders -> R.string.import_kind_change_orders
    ImportKind.Other -> R.string.import_kind_other
}

private fun kindSingular(kind: ImportKind): Int = when (kind) {
    ImportKind.Floors -> R.string.import_one_floor
    ImportKind.Rooms -> R.string.import_one_room
    ImportKind.WallPhotos -> R.string.import_one_wall_photo
    ImportKind.Overlays -> R.string.import_one_overlay
    ImportKind.Items -> R.string.import_one_item
    ImportKind.Documents -> R.string.import_one_document
    ImportKind.ChangeOrders -> R.string.import_one_change_order
    ImportKind.Other -> R.string.import_one_other
}

private fun kindIcon(kind: ImportKind): ImageVector = when (kind) {
    ImportKind.Floors -> Icons.Rounded.Layers
    ImportKind.Rooms -> Icons.Rounded.MeetingRoom
    ImportKind.WallPhotos -> Icons.Rounded.PhotoCamera
    ImportKind.Overlays -> Icons.Rounded.Polyline
    ImportKind.Items -> Icons.Rounded.Inventory2
    ImportKind.Documents -> Icons.Rounded.Description
    ImportKind.ChangeOrders -> Icons.Rounded.Draw
    ImportKind.Other -> Icons.Rounded.Description
}
