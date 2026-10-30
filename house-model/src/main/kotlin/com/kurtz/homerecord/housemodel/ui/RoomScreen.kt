package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.annotation.StringRes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.housemodel.items.labelRes
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.units.LengthParser
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.WarrantyStatusLine
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.plan.RoomKind

/** HM-Room, opened from the blueprint (AC-C14). Each section says what it will hold until its feature lands (AC-U4). */
@Composable
fun RoomScreen(state: RoomUiState, onEvent: (RoomEvent) -> Unit) {
    val subtitle = listOfNotNull(state.floorName, state.homeName).joinToString(" · ").ifBlank { null }
    val room = state.room
    ScreenScaffold(
        title = when {
            room != null -> room.name.ifBlank { stringResource(R.string.plan_unnamed_room) }
            else -> stringResource(R.string.room_screen_title)
        },
        subtitle = subtitle,
        onBack = { onEvent(RoomEvent.Back) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            room == null -> EmptyState(
                icon = Icons.Rounded.MeetingRoom,
                headline = stringResource(R.string.room_not_found_title),
                body = stringResource(R.string.room_not_found_body),
                actionLabel = stringResource(R.string.room_back),
                onAction = { onEvent(RoomEvent.Back) },
                modifier = Modifier.padding(padding),
            )
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(
                        R.string.room_size_summary,
                        LengthParser.format(room.widthIn, state.system),
                        LengthParser.format(room.lengthIn, state.system),
                        kindLabel(RoomKind.parse(room.kind)),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                RoomSections.forEach { section ->
                    if (section.tag == "items") RoomItemsCard(state.items, state.canAddItem, onEvent) else RoomSectionCard(section)
                }
            }
        }
    }
}

private data class RoomSection(val tag: String, val icon: ImageVector, @StringRes val title: Int, @StringRes val empty: Int)

private val RoomSections = listOf(
    RoomSection("items", Icons.Rounded.Inventory2, R.string.room_section_items, R.string.room_section_items_empty),
    RoomSection("walls", Icons.Rounded.Straighten, R.string.room_section_walls, R.string.room_section_walls_empty),
    RoomSection("photos", Icons.Rounded.PhotoLibrary, R.string.room_section_photos, R.string.room_section_photos_empty),
    RoomSection("overlays", Icons.Rounded.Layers, R.string.room_section_overlays, R.string.room_section_overlays_empty),
    RoomSection("breakers", Icons.Rounded.ElectricalServices, R.string.room_section_breakers, R.string.room_section_breakers_empty),
    RoomSection("shutoffs", Icons.Rounded.WaterDrop, R.string.room_section_shutoffs, R.string.room_section_shutoffs_empty),
    RoomSection("notes", Icons.Rounded.StickyNote2, R.string.room_section_notes, R.string.room_section_notes_empty),
)

@Composable
private fun RoomSectionCard(section: RoomSection) {
    Card(Modifier.fillMaxWidth().testTag("${LocalTagPrefix.current}_room_${section.tag}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(section.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            Text(stringResource(section.empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoomItemsCard(items: List<RoomItem>, canAdd: Boolean, onEvent: (RoomEvent) -> Unit) {
    val prefix = LocalTagPrefix.current
    Card(Modifier.fillMaxWidth().testTag("${prefix}_room_items")) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.room_section_items), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            if (items.isEmpty()) {
                Text(
                    stringResource(R.string.room_section_items_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items.forEach { item ->
                Column(Modifier.testTag("${prefix}_room_item_${item.id}")) {
                    RecordListItem(
                        headline = item.name,
                        supporting = item.detail.ifBlank { stringResource(item.category.labelRes) },
                        leading = { Icon(itemIcon(item.iconName), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onEvent(RoomEvent.OpenItem(item.id)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    WarrantyStatusLine(item.warranty, Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp))
                }
            }
            if (canAdd) {
                TextButton(
                    onClick = { onEvent(RoomEvent.AddItem) },
                    modifier = Modifier.padding(horizontal = 4.dp).heightIn(min = 48.dp).testTag("${prefix}_room_add_item"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.room_add_item), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
