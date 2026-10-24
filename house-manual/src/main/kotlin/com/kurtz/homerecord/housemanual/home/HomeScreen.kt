package com.kurtz.homerecord.housemanual.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.home.AttentionKind
import com.kurtz.homerecord.housemodel.home.AttentionRow
import com.kurtz.homerecord.housemodel.ui.itemIcon

/** HM-Home: Emergency card, Needs attention, Recent, quick actions (AC-H1, AC-H2). */
@Composable
fun HomeScreen(state: HomeUiState, onEvent: (HomeEvent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(state.homeName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("hm_home_title"))
            QuickActions(state, onEvent)
            if (state.wallNoteShown) {
                Text(stringResource(R.string.home_wall_soon), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("hm_home_wall_note"))
            }
            EmergencyCard(state.shutoffCount, onAdd = { onEvent(HomeEvent.AddItem) })
            Section(stringResource(R.string.home_attention)) {
                if (state.attention.isEmpty()) {
                    Text(stringResource(R.string.home_attention_none), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("hm_home_attention_none"))
                } else {
                    state.attention.forEach { AttentionItem(it, onEvent) }
                }
            }
            if (state.recent.isNotEmpty()) {
                Section(stringResource(R.string.home_recent)) {
                    state.recent.forEach { r ->
                        RecordListItem(
                            headline = r.name,
                            supporting = listOfNotNull(r.make, r.model).joinToString(" ").ifEmpty { r.roomName.ifEmpty { null } },
                            leading = { Icon(itemIcon(ItemCategory.subtype(r.subtypeId)?.iconName ?: "Category"), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { onEvent(HomeEvent.OpenItem(r.id)) },
                            modifier = Modifier.testTag("hm_home_recent_${r.id}"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun QuickActions(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.itemCount == 0) {
            Text(stringResource(R.string.home_empty_body), style = MaterialTheme.typography.bodyLarge)
        }
        ActionButton(Icons.Rounded.Add, R.string.home_action_add, "hm_home_add_item", filled = true) { onEvent(HomeEvent.AddItem) }
        ActionButton(Icons.Rounded.DocumentScanner, R.string.home_action_scan, "hm_home_scan_plate") { onEvent(HomeEvent.ScanDataPlate) }
        ActionButton(Icons.Rounded.CameraAlt, R.string.home_action_wall, "hm_home_photo_wall") { onEvent(HomeEvent.PhotographWall) }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: Int, tag: String, filled: Boolean = false, onClick: () -> Unit) {
    val m = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
    }
    if (filled) Button(onClick = onClick, modifier = m) { content() } else OutlinedButton(onClick = onClick, modifier = m) { content() }
}

@Composable
private fun EmergencyCard(count: Int, onAdd: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("hm_home_emergency")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_emergency), style = MaterialTheme.typography.titleMedium)
            Text(
                if (count == 0) stringResource(R.string.home_emergency_none)
                else pluralStringResource(R.plurals.home_emergency_count, count, count),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (count == 0) {
                OutlinedButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_home_emergency_add")) {
                    Icon(Icons.Rounded.PowerOff, contentDescription = null)
                    Text(stringResource(R.string.home_emergency_add), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun AttentionItem(row: AttentionRow, onEvent: (HomeEvent) -> Unit) {
    val supporting = when (row.kind) {
        AttentionKind.WARRANTY_EXPIRING -> stringResource(R.string.home_attention_warranty, row.detail)
        AttentionKind.REMINDER_OVERDUE -> stringResource(R.string.home_attention_overdue, row.detail)
        AttentionKind.MISSING_MAKE_MODEL -> stringResource(R.string.home_attention_missing)
    }
    RecordListItem(
        headline = row.title,
        supporting = supporting,
        leading = { Icon(if (row.kind == AttentionKind.MISSING_MAKE_MODEL) Icons.Rounded.Inventory2 else Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        onClick = row.itemId?.let { id -> { onEvent(HomeEvent.OpenItem(id)) } },
        modifier = Modifier.testTag("hm_home_attention_${row.id}"),
    )
}
