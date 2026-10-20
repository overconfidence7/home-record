package com.kurtz.homerecord.coreui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.sync.SyncPhase
import com.kurtz.homerecord.coreui.R
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme

/** Pending-sync count and state, shown on Today (AC-F52); tapping asks for a sync now. */
@Composable
fun SyncStatusChip(phase: SyncPhase, pending: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = when {
        phase == SyncPhase.Syncing -> stringResource(R.string.sync_syncing)
        phase == SyncPhase.Offline && pending > 0 -> pluralStringResource(R.plurals.sync_waiting_offline, pending, pending)
        phase == SyncPhase.Offline -> stringResource(R.string.sync_offline)
        phase == SyncPhase.Failed -> stringResource(R.string.sync_failed)
        pending > 0 -> pluralStringResource(R.plurals.sync_waiting, pending, pending)
        else -> stringResource(R.string.sync_up_to_date)
    }
    val icon = when {
        phase == SyncPhase.Syncing -> Icons.Rounded.CloudSync
        phase == SyncPhase.Offline -> Icons.Rounded.CloudOff
        phase == SyncPhase.Failed -> Icons.Rounded.ErrorOutline
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
        }
    }
}
