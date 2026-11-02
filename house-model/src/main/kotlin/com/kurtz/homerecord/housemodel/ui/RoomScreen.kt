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
import androidx.compose.material3.OutlinedButton
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
import com.kurtz.homerecord.coreui.components.OptionsSheet
import com.kurtz.homerecord.coreui.components.SheetOption
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.WarrantyStatusLine
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.plan.RoomKind
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallSide
import com.kurtz.homerecord.housemodel.walls.WallStage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ListItem
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kurtz.homerecord.core.units.UnitSystem
import java.io.File

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
                if (state.showView3D) {
                    OutlinedButton(
                        onClick = { onEvent(RoomEvent.View3D) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("${LocalTagPrefix.current}_room_view_3d"),
                    ) {
                        Icon(Icons.Rounded.ViewInAr, contentDescription = null)
                        Text(stringResource(R.string.room_view_3d), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                RoomSections.forEach { section ->
                    when (section.tag) {
                        "items" -> RoomItemsCard(state.items, state.canAddItem, onEvent)
                        "walls" -> RoomWallsCard(state, onEvent)
                        else -> RoomSectionCard(section)
                    }
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

/** The Walls section (AC-C17): a stage picker, then each wall by side and length with its photo at that stage. */
@Composable
private fun RoomWallsCard(state: RoomUiState, onEvent: (RoomEvent) -> Unit) {
    val prefix = LocalTagPrefix.current
    Card(Modifier.fillMaxWidth().testTag("${prefix}_room_walls")) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Straighten, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.room_section_walls), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            if (state.walls.isEmpty()) {
                Text(
                    stringResource(R.string.room_section_walls_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                return@Column
            }
            WallStagePicker(state.wallStage, { onEvent(RoomEvent.SelectStage(it)) }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            if (state.photoImportFailed) {
                Text(
                    stringResource(R.string.wall_photo_import_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("${prefix}_room_wall_photo_error"),
                )
            }
            state.walls.forEach { wall -> RoomWallRow(wall, state.wallStage, state.system, state.canPhotograph, onEvent) }
        }
    }
    val sheetWall = state.photoSheetFor?.let { id -> state.walls.firstOrNull { it.id == id } }
    if (sheetWall != null) {
        OptionsSheet(
            title = wallName(sheetWall),
            options = listOf(
                SheetOption("wall_take", stringResource(R.string.wall_photo_take), Icons.Rounded.PhotoCamera),
                SheetOption("wall_choose", stringResource(R.string.wall_photo_choose), Icons.Rounded.PhotoLibrary),
            ),
            onDismiss = { onEvent(RoomEvent.ClosePhotoSheet) },
            onPick = { onEvent(if (it.id == "wall_take") RoomEvent.TakeWallPhoto else RoomEvent.ChooseWallPhoto) },
        )
    }
}

/** Framing, Rough-in, Insulation, Finished as one segmented row (rule 22); a label steps down rather than split at 200%. */
@Composable
internal fun WallStagePicker(selected: WallStage, onSelect: (WallStage) -> Unit, modifier: Modifier = Modifier, screen: String = "room_wall") {
    val prefix = LocalTagPrefix.current
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        WallStage.entries.forEachIndexed { index, stage ->
            SegmentedButton(
                selected = stage == selected,
                onClick = { onSelect(stage) },
                shape = SegmentedButtonDefaults.itemShape(index, WallStage.entries.size),
                icon = {},
                modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_${screen}_stage_${stage.name.lowercase()}"),
            ) {
                Text(
                    stringResource(stage.labelRes),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                )
            }
        }
    }
}

@Composable
private fun RoomWallRow(wall: RoomWall, stage: WallStage, system: UnitSystem, canPhotograph: Boolean, onEvent: (RoomEvent) -> Unit) {
    val prefix = LocalTagPrefix.current
    val name = wallName(wall)
    val photographed = WallStage.entries.filter { it in wall.thumbnails }
    val length = LengthParser.format(wall.lengthIn, system)
    val supporting = if (photographed.isEmpty()) {
        stringResource(R.string.room_wall_no_photos, length)
    } else {
        stringResource(R.string.room_wall_photos, length, photographed.map { stringResource(it.labelRes) }.joinToString(", "))
    }
    val photo = wall.thumbnails[stage]
    // At large text the button moves under the words so the wall name and its stages keep the row's width (rule 32).
    val buttonBelow = LocalDensity.current.fontScale >= 1.5f
    val button: @Composable () -> Unit = {
        TextButton(
            onClick = { onEvent(RoomEvent.PhotographWall(wall.id)) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_room_wall_photograph_${wall.id}"),
        ) { Text(stringResource(if (photo == null) R.string.room_wall_photograph else R.string.room_wall_retake)) }
    }
    ListItem(
        headlineContent = { Text(name, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Column {
                Text(supporting, style = MaterialTheme.typography.bodyMedium)
                if (canPhotograph && buttonBelow) Box(Modifier.padding(top = 4.dp).offset(x = (-12).dp)) { button() }
            }
        },
        leadingContent = { WallThumbnail(photo, stringResource(R.string.room_wall_photo_description, stringResource(stage.labelRes), name)) },
        trailingContent = if (canPhotograph && !buttonBelow) button else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.heightIn(min = 72.dp).testTag("${prefix}_room_wall_${wall.id}"),
    )
}

/** 56 dp picture of the wall at the chosen stage over a plain tile, which shows when the stage has no photo or it fails to load (rule 27). */
@Composable
private fun WallThumbnail(path: String?, description: String) {
    Box(
        Modifier.size(56.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (path != null) {
            AsyncImage(model = File(path), contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
internal fun wallName(wall: RoomWall): String {
    val side = stringResource(
        when (wall.side) {
            WallSide.NORTH -> R.string.room_wall_north
            WallSide.EAST -> R.string.room_wall_east
            WallSide.SOUTH -> R.string.room_wall_south
            WallSide.WEST -> R.string.room_wall_west
            WallSide.ANGLED -> R.string.room_wall_angled
        },
    )
    return wall.number?.let { stringResource(R.string.room_wall_numbered, side, it) } ?: side
}

@get:StringRes
internal val WallStage.labelRes: Int
    get() = when (this) {
        WallStage.FRAMING -> R.string.wall_stage_framing
        WallStage.ROUGH_IN -> R.string.wall_stage_rough_in
        WallStage.INSULATION -> R.string.wall_stage_insulation
        WallStage.FINISHED -> R.string.wall_stage_finished
    }
