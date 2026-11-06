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
import androidx.compose.runtime.remember
import androidx.compose.material3.HorizontalDivider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
import com.kurtz.homerecord.coreui.shell.SharedKeys
import com.kurtz.homerecord.coreui.shell.sharedScreenBounds
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.plan.RoomKind
import com.kurtz.homerecord.housemodel.walls.RemovedWallPhoto
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallSide
import com.kurtz.homerecord.housemodel.walls.WallStage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.graphics.luminance
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.color
import com.kurtz.homerecord.housemodel.overlays.icon
import com.kurtz.homerecord.housemodel.overlays.nameRes
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
                        modifier = Modifier.sharedScreenBounds(SharedKeys.room3D(room.id)).heightIn(min = 48.dp).testTag("${LocalTagPrefix.current}_room_view_3d"),
                    ) {
                        Icon(Icons.Rounded.ViewInAr, contentDescription = null)
                        Text(stringResource(R.string.room_view_3d), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                RoomSections.forEach { section ->
                    when (section.tag) {
                        "items" -> RoomItemsCard(state.items, state.canAddItem, onEvent)
                        "walls" -> RoomWallsCard(state, onEvent)
                        "overlays" -> if (state.overlays != null) RoomOverlaysCard(state, state.overlays, onEvent) else RoomSectionCard(section)
                        "breakers" -> if (state.breakers != null) RoomBreakersCard(state.breakers, onEvent) else RoomSectionCard(section)
                        "shutoffs" -> if (state.shutoffs != null) RoomShutoffsCard(state.shutoffs, onEvent) else RoomSectionCard(section)
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
            state.walls.forEach { wall -> RoomWallRow(wall, state.wallStage, state.system, state.canPhotograph && !state.viewOnly, state.canOpenWalls, onEvent) }
            if (state.removedWallPhotos.isNotEmpty()) RemovedWallPhotos(state.removedWallPhotos)
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

/**
 * "Photos of removed walls" (N001): photos whose wall a plan edit took away stay on the room, each with its stage and the
 * day it was taken, so a picture of what is behind a wall is never lost to a plan change.
 */
@Composable
private fun RemovedWallPhotos(photos: List<RemovedWallPhoto>) {
    val prefix = LocalTagPrefix.current
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Column(Modifier.testTag("${prefix}_room_removed_wall_photos")) {
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Text(
            stringResource(R.string.room_removed_wall_photos),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).semantics { heading() },
        )
        photos.forEach { photo ->
            val stage = stringResource(photo.stage.labelRes)
            val date = Instant.ofEpochMilli(photo.takenAt).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
            ListItem(
                headlineContent = { Text(stringResource(R.string.room_removed_wall_photo_stage, stage), style = MaterialTheme.typography.bodyLarge) },
                supportingContent = { Text(stringResource(R.string.room_removed_wall_photo_taken, date), style = MaterialTheme.typography.bodyMedium) },
                leadingContent = { WallThumbnail(photo.path, stringResource(R.string.room_removed_wall_photo_description, stage, date)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.heightIn(min = 72.dp).testTag("${prefix}_room_removed_wall_photo_${photo.id}"),
            )
        }
    }
}

/** Framing, Rough-in, Insulation, Finished as one segmented row (rule 22); a label steps down rather than split at 200%. */
@Composable
internal fun WallStagePicker(selected: WallStage, onSelect: (WallStage) -> Unit, modifier: Modifier = Modifier, screen: String = "room_wall") {
    val prefix = LocalTagPrefix.current
    val labels = WallStage.entries.map { stringResource(it.labelRes) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge
    BoxWithConstraints(modifier.fillMaxWidth()) {
    // One size for all four labels (V4): sized one by one, "Insulation" stepped down alone and the
    // row read as four different fonts. Each Text keeps autoSize under this cap as a safety net.
    val segmentWidthPx = with(density) { (maxWidth / labels.size - SegmentLabelInset).toPx() }
    val sharedSize = remember(labels, segmentWidthPx, labelStyle, density) {
        SegmentLabelSize.largestFitting(minSp = 7f, maxSp = labelStyle.fontSize.value, stepSp = 0.25f) { sp ->
            labels.all { label -> measurer.measure(label, labelStyle.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width <= segmentWidthPx }
        }.sp
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
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
                    autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = sharedSize),
                )
            }
        }
    }
    }
}

/** Space a segment's label cannot use: the button's 12 dp padding each side and its 1 dp borders. */
private val SegmentLabelInset = 26.dp

@Composable
private fun RoomWallRow(wall: RoomWall, stage: WallStage, system: UnitSystem, canPhotograph: Boolean, canOpen: Boolean, onEvent: (RoomEvent) -> Unit) {
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
        trailingContent = if (canPhotograph && !buttonBelow) button else if (canOpen) RecordListItemChevron else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .heightIn(min = 72.dp)
            .then(if (canOpen) Modifier.clickable(onClickLabel = stringResource(R.string.room_wall_open)) { onEvent(RoomEvent.OpenWall(wall.id)) } else Modifier)
            .testTag("${prefix}_room_wall_${wall.id}"),
    )
}

/** The Overlays section (AC-C25): every overlay in the room by kind icon and color; a tap opens its wall, or the plan, with it picked. */
@Composable
private fun RoomOverlaysCard(state: RoomUiState, overlays: List<Overlay>, onEvent: (RoomEvent) -> Unit) {
    val prefix = LocalTagPrefix.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Card(Modifier.fillMaxWidth().testTag("${prefix}_room_overlays")) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.room_section_overlays), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            if (overlays.isEmpty()) {
                Text(
                    stringResource(if (state.walls.isEmpty() || state.viewOnly) R.string.room_section_overlays_empty else R.string.room_section_overlays_empty_walls),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            overlays.forEach { o ->
                val kindName = stringResource(o.kind.nameRes)
                val wall = o.wallId?.let { id -> state.walls.firstOrNull { it.id == id } }
                val where = wall?.let { wallName(it) } ?: stringResource(R.string.room_overlay_on_plan)
                val wallId = o.wallId
                val opens = state.canOpenWalls && (wallId != null || !state.viewOnly)
                RecordListItem(
                    headline = o.title(kindName),
                    supporting = listOfNotNull(kindName.takeIf { o.label.isNotBlank() }, where, stringResource(R.string.wall_overlay_recorded_at, stringResource(o.stage.labelRes))).joinToString(" · "),
                    leading = { Icon(o.kind.icon, contentDescription = null, tint = o.kind.color(dark)) },
                    onClick = when {
                        !opens -> null
                        wallId != null -> ({ onEvent(RoomEvent.OpenWall(wallId, o.id)) })
                        else -> ({ onEvent(RoomEvent.OpenPlanOverlay(o.id)) })
                    },
                    trailing = if (opens) RecordListItemChevron else null,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.testTag("${prefix}_room_overlay_${o.id}"),
                )
            }
        }
    }
}

/**
 * Breakers feeding this room (AC-C41): one row per breaker, panel then slot order, with a confidence dot (filled for
 * Confirmed, half for Likely, an empty ring for Unknown) and the word beside it, so the state never rests on color alone.
 * A tap opens the panel at that breaker (AC-H7).
 */
@Composable
private fun RoomBreakersCard(breakers: List<com.kurtz.homerecord.housemodel.panel.FeedingBreaker>, onEvent: (RoomEvent) -> Unit) {
    val prefix = LocalTagPrefix.current
    Card(Modifier.fillMaxWidth().testTag("${prefix}_room_breakers")) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.ElectricalServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.room_section_breakers_feeding), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            if (breakers.isEmpty()) {
                Text(
                    stringResource(R.string.room_section_breakers_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            breakers.forEach { f ->
                val b = f.breaker
                val confidence = stringResource(
                    when (f.confidence) {
                        com.kurtz.homerecord.core.model.LinkConfidence.CONFIRMED -> R.string.room_breaker_confirmed
                        com.kurtz.homerecord.core.model.LinkConfidence.LIKELY -> R.string.room_breaker_likely
                        com.kurtz.homerecord.core.model.LinkConfidence.UNKNOWN -> R.string.room_breaker_unknown
                    },
                )
                RecordListItem(
                    headline = listOf(stringResource(R.string.room_breaker_headline, b.number), b.label.ifBlank { stringResource(R.string.room_breaker_unlabeled) }).joinToString(" · "),
                    supporting = listOfNotNull(f.panelName, b.amps?.let { stringResource(R.string.room_breaker_amps, it) }, confidence).joinToString(" · "),
                    leading = { ConfidenceDot(f.confidence) },
                    onClick = { onEvent(RoomEvent.OpenBreaker(f)) },
                    onClickLabel = stringResource(R.string.room_breaker_open),
                    trailing = RecordListItemChevron,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.testTag("${prefix}_room_breaker_${b.number}"),
                )
            }
        }
    }
}

/** Shutoffs here (AC-C45): the room's shutoff items, mains first; a row opens the shutoff's page. */
@Composable
private fun RoomShutoffsCard(shutoffs: List<RoomItem>, onEvent: (RoomEvent) -> Unit) {
    val prefix = LocalTagPrefix.current
    Card(Modifier.fillMaxWidth().testTag("${prefix}_room_shutoffs")) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.room_section_shutoffs_here), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            if (shutoffs.isEmpty()) {
                Text(
                    stringResource(R.string.room_section_shutoffs_here_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            shutoffs.forEachIndexed { i, s ->
                val subtype = com.kurtz.homerecord.core.model.ItemCategory.subtype(s.subtypeId)
                RecordListItem(
                    headline = s.name,
                    supporting = s.detail.ifBlank { subtype?.let { stringResource(it.labelRes) } ?: stringResource(s.category.labelRes) },
                    leading = { Icon(itemIcon(s.iconName), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { onEvent(RoomEvent.OpenItem(s.id)) },
                    trailing = RecordListItemChevron,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.testTag("${prefix}_room_shutoff_$i"),
                )
            }
        }
    }
}

/** 16 dp dot in the primary color: filled (Confirmed), left half filled (Likely), ring only (Unknown). */
@Composable
private fun ConfidenceDot(confidence: com.kurtz.homerecord.core.model.LinkConfidence) {
    val color = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(Modifier.size(24.dp).padding(4.dp)) {
        val stroke = 2.dp.toPx()
        val r = size.minDimension / 2f - stroke / 2f
        when (confidence) {
            com.kurtz.homerecord.core.model.LinkConfidence.CONFIRMED -> drawCircle(color, radius = size.minDimension / 2f)
            com.kurtz.homerecord.core.model.LinkConfidence.LIKELY -> {
                drawArc(color, startAngle = 90f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
                drawCircle(color, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            }
            com.kurtz.homerecord.core.model.LinkConfidence.UNKNOWN -> drawCircle(color, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        }
    }
}

private val RecordListItemChevron: @Composable () -> Unit = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }

/** 56 dp picture of the wall at the chosen stage over a plain tile, which shows when the stage has no photo or it fails to load (rule 27). */
@Composable
internal fun WallThumbnail(path: String?, description: String) {
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

/** "North wall", or "North wall 2" when the room has more than one wall on that side. */
@Composable
fun wallName(wall: RoomWall): String {
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
val WallStage.labelRes: Int
    get() = when (this) {
        WallStage.FRAMING -> R.string.wall_stage_framing
        WallStage.ROUGH_IN -> R.string.wall_stage_rough_in
        WallStage.INSULATION -> R.string.wall_stage_insulation
        WallStage.FINISHED -> R.string.wall_stage_finished
    }
