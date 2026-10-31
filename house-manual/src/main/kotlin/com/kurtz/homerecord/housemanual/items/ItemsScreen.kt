package com.kurtz.homerecord.housemanual.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.WarrantyStatusLine
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.items.ItemGrouping
import com.kurtz.homerecord.housemodel.items.WarrantyFilter
import com.kurtz.homerecord.housemodel.items.labelRes
import com.kurtz.homerecord.housemodel.ui.itemIcon

/** Items tab (AC-H6): grouped by category or room, filtered by category, room and warranty status, one tap to open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(state: ItemsUiState, onEvent: (ItemsEvent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(ItemsEvent.Add) }, modifier = Modifier.testTag("hm_items_fab")) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.items_add))
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.totalCount == 0 -> EmptyState(
                icon = Icons.Rounded.Inventory2,
                headline = stringResource(R.string.items_empty_title),
                body = stringResource(R.string.items_empty_body),
                actionLabel = stringResource(R.string.items_add),
                onAction = { onEvent(ItemsEvent.Add) },
                modifier = Modifier.padding(padding),
                actionModifier = Modifier.testTag("hm_items_add"),
            )
            else -> LazyColumn(Modifier.padding(padding).fillMaxWidth(), contentPadding = PaddingValues(bottom = 88.dp)) {
                item { Controls(state, onEvent) }
                if (state.groups.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.items_no_match_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.items_no_match_body, state.totalCount), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                            TextButton(onClick = { onEvent(ItemsEvent.ClearFilters) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_items_clear")) {
                                Text(stringResource(R.string.items_clear_filters))
                            }
                        }
                    }
                }
                state.groups.forEach { g ->
                    val folded = state.isCollapsed(g.key)
                    item(key = "h:${g.key}", contentType = "header") {
                        GroupHeader(
                            title = stringResource(
                                R.string.items_group_header,
                                g.category?.let { stringResource(it.labelRes) } ?: g.title.ifEmpty { stringResource(R.string.items_no_room) },
                                g.count,
                            ),
                            expanded = !folded,
                            onToggle = { onEvent(ItemsEvent.ToggleGroup(g.key)) },
                            modifier = Modifier.testTag("hm_items_group_${g.key}"),
                        )
                    }
                    if (!folded) items(g.items, key = { "${g.key}:${it.id}" }, contentType = { "item" }) { r ->
                        Column(Modifier.testTag("hm_items_row_${r.id}")) {
                            RecordListItem(
                                headline = r.name,
                                supporting = listOfNotNull(r.make, r.model).joinToString(" ").ifEmpty { null }.let { mm ->
                                    if (state.grouping == ItemGrouping.CATEGORY && r.roomName.isNotEmpty()) {
                                        if (mm == null) r.roomName else stringResource(R.string.items_row_supporting, r.roomName, mm)
                                    } else mm
                                },
                                leading = { Icon(itemIcon(ItemCategory.subtype(r.subtypeId)?.iconName ?: "Category"), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { onEvent(ItemsEvent.Open(r.id)) },
                            )
                            WarrantyStatusLine(r.warranty, Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A category or room header that folds its rows away (SPEC HM-Items "grouped by category (collapsible)"). */
@Composable
private fun GroupHeader(title: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val stateText = stringResource(if (expanded) R.string.items_group_expanded else R.string.items_group_collapsed)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(if (expanded) R.string.items_group_collapse else R.string.items_group_expand), onClick = onToggle)
            .semantics { heading(); stateDescription = stateText }
            .padding(start = 16.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Controls(state: ItemsUiState, onEvent: (ItemsEvent) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.filter.query,
            onValueChange = { onEvent(ItemsEvent.SetQuery(it)) },
            modifier = Modifier.fillMaxWidth().testTag("hm_items_search"),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.items_search)) },
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(ItemGrouping.CATEGORY to R.string.items_by_category, ItemGrouping.ROOM to R.string.items_by_room)
            options.forEachIndexed { i, (g, label) ->
                SegmentedButton(
                    selected = state.grouping == g,
                    onClick = { onEvent(ItemsEvent.SetGrouping(g)) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    modifier = Modifier.testTag(if (g == ItemGrouping.CATEGORY) "hm_items_group_category" else "hm_items_group_room"),
                ) { Text(stringResource(label)) }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                MenuChip(
                    label = state.filter.category?.let { stringResource(it.labelRes) } ?: stringResource(R.string.items_filter_category),
                    selected = state.filter.category != null,
                    options = state.categoryOptions.map { it.name to stringResource(it.labelRes) },
                    onPick = { onEvent(ItemsEvent.SetCategory(it?.let(ItemCategory::fromId))) },
                    tag = "hm_items_filter_category",
                )
            }
            item {
                MenuChip(
                    label = state.roomOptions.firstOrNull { it.id == state.filter.roomId }?.name ?: stringResource(R.string.items_filter_room),
                    selected = state.filter.roomId != null,
                    options = state.roomOptions.map { it.id to it.name },
                    onPick = { onEvent(ItemsEvent.SetRoom(it)) },
                    tag = "hm_items_filter_room",
                )
            }
            items(WarrantyFilter.entries) { w ->
                val on = state.filter.warranty == w
                FilterChip(
                    selected = on,
                    onClick = { onEvent(ItemsEvent.SetWarranty(if (on) null else w)) },
                    label = { Text(stringResource(w.label())) },
                    modifier = Modifier.testTag("hm_items_filter_warranty_${w.name.lowercase()}"),
                )
            }
        }
    }
}

private fun WarrantyFilter.label() = when (this) {
    WarrantyFilter.ACTIVE -> R.string.items_warranty_active
    WarrantyFilter.EXPIRING -> R.string.items_warranty_expiring
    WarrantyFilter.EXPIRED -> R.string.items_warranty_expired
    WarrantyFilter.UNKNOWN -> R.string.items_warranty_unknown
}

@Composable
private fun MenuChip(label: String, selected: Boolean, options: List<Pair<String, String>>, onPick: (String?) -> Unit, tag: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected, onClick = { open = true }, label = { Text(label) }, modifier = Modifier.testTag(tag))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.items_filter_any)) }, onClick = { open = false; onPick(null) })
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) }, modifier = Modifier.testTag("${tag}_$value"))
            }
        }
    }
}
