package com.kurtz.homerecord.housemanual.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.kurtz.homerecord.core.model.SearchKind
import androidx.compose.foundation.isSystemInDarkTheme
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.color
import com.kurtz.homerecord.housemodel.overlays.icon
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
import com.kurtz.homerecord.coreui.components.ErrorState
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
        if (state.loadFailed) {
            ErrorState(
                stringResource(R.string.home_load_failed), { onEvent(HomeEvent.Retry) }, Modifier.padding(padding),
                retryModifier = Modifier.testTag("hm_home_retry"),
            )
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(state.homeName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("hm_home_title"))
            if (state.viewOnly) com.kurtz.homerecord.housemanual.share.ViewOnlyNote(Modifier.testTag("hm_home_view_only"))
            SearchBar(state.query) { onEvent(HomeEvent.QueryChanged(it)) }
            if (state.searching) {
                SearchResults(state, onEvent)
                return@Column
            }
            if (!state.viewOnly) QuickActions(state, onEvent)
            EmergencyCard(state.shutoffCount, onAdd = { onEvent(HomeEvent.AddShutoff) }.takeUnless { state.viewOnly }, onOpen = { onEvent(HomeEvent.OpenEmergency) })
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
private fun EmergencyCard(count: Int, onAdd: (() -> Unit)?, onOpen: () -> Unit) {
    // With shutoffs recorded the button opens HM-Emergency (AC-H11); without, it adds the first one.
    val modifier = Modifier.fillMaxWidth().testTag("hm_home_emergency")
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_emergency), style = MaterialTheme.typography.titleMedium)
            Text(
                if (count == 0) stringResource(R.string.home_emergency_none)
                else pluralStringResource(R.plurals.home_emergency_count, count, count),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (count == 0) {
                if (onAdd != null) OutlinedButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_home_emergency_add")) {
                    Icon(Icons.Rounded.PowerOff, contentDescription = null)
                    Text(stringResource(R.string.home_emergency_add), modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Button(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_home_emergency_open")) {
                    Icon(Icons.Rounded.PowerOff, contentDescription = null)
                    Text(stringResource(R.string.home_emergency_open), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    Card(modifier) { body() }
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
        onClick = if (row.kind == AttentionKind.REMINDER_OVERDUE) ({ onEvent(HomeEvent.OpenReminders) })
            else row.itemId?.let { id -> { onEvent(HomeEvent.OpenItem(id)) } },
        modifier = Modifier.testTag("hm_home_attention_${row.id}"),
    )
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    val clear = stringResource(R.string.home_search_clear)
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("hm_home_search"),
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isEmpty()) null else {
            { IconButton(onClick = { onChange("") }, modifier = Modifier.testTag("hm_home_search_clear")) { Icon(Icons.Rounded.Close, contentDescription = clear) } }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun SearchResults(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    if (state.results.isEmpty()) {
        NoResults(state.query.trim(), canAdd = !state.viewOnly, onEvent)
        return
    }
    Column {
        state.results.forEach { r ->
            RecordListItem(
                headline = r.title,
                supporting = listOfNotNull(kindLabel(r.kind)?.let { stringResource(it) }, r.subtitle).joinToString(" · ").ifEmpty { null },
                leading = { ResultIcon(r) },
                onClick = { onEvent(HomeEvent.OpenResult(r.kind, r.openId ?: r.id, r.slot)) },
                modifier = Modifier.testTag("hm_home_result_${r.id}"),
            )
        }
    }
}

/** Rule 11: no results says so and offers to clear the search or add the thing as an item. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoResults(query: String, canAdd: Boolean, onEvent: (HomeEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.home_search_none, query), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("hm_home_search_none"))
        Text(stringResource(if (canAdd) R.string.home_search_none_body else R.string.home_search_none_body_view_only), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onEvent(HomeEvent.QueryChanged("")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_home_search_none_clear")) {
                Text(stringResource(R.string.home_search_clear))
            }
            if (canAdd) Button(onClick = { onEvent(HomeEvent.AddItem) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_home_search_none_add")) {
                Text(stringResource(R.string.items_add))
            }
        }
    }
}

/** A breaker's title or subtitle already reads "Breaker 14", so its row carries no kind word. */
private fun kindLabel(kind: SearchKind): Int? = when (kind) {
    SearchKind.ITEM -> R.string.search_kind_item
    SearchKind.ROOM -> R.string.search_kind_room
    SearchKind.DOCUMENT -> R.string.search_kind_document
    SearchKind.CONTACT -> R.string.search_kind_contact
    SearchKind.OVERLAY -> R.string.search_kind_overlay
    SearchKind.BREAKER -> null
    SearchKind.SHUTOFF -> R.string.search_kind_item
}

/** An overlay shows its own kind's icon in its layer color (rule 26, AC-C25); other results the kind's icon in primary. */
@Composable
private fun ResultIcon(r: SearchResultRow) {
    if (r.kind == SearchKind.OVERLAY) {
        val kind = OverlayKind.fromWire(r.subkind)
        Icon(kind.icon, contentDescription = null, tint = kind.color(isSystemInDarkTheme()))
    } else {
        Icon(kindIcon(r.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

private fun kindIcon(kind: SearchKind): ImageVector = when (kind) {
    SearchKind.ROOM -> Icons.Rounded.MeetingRoom
    SearchKind.DOCUMENT -> Icons.Rounded.Description
    SearchKind.CONTACT -> Icons.Rounded.Person
    SearchKind.BREAKER -> Icons.Rounded.ElectricalServices
    else -> Icons.Rounded.Inventory2
}
