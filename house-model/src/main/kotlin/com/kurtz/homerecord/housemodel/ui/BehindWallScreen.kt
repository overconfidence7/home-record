package com.kurtz.homerecord.housemodel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.units.LengthParser
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.OptionsSheet
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SheetOption
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallStage

/**
 * FL-BehindWall (AC-F49): the job's rooms by floor with how many walls have their Rough-in and Finished photos, then the
 * walls of the picked room; a wall opens Take photo / Choose from gallery and the photo is saved at the stage the wall is
 * at (Rough-in before drywall, Finished after).
 */
@Composable
fun BehindWallScreen(state: BehindWallUiState, onEvent: (BehindWallEvent) -> Unit) {
    val room = state.room
    ScreenScaffold(
        title = room?.name?.ifBlank { stringResource(R.string.plan_unnamed_room) } ?: stringResource(R.string.behind_wall_title),
        subtitle = if (room != null) stringResource(R.string.behind_wall_room_subtitle, room.floorName) else state.title.ifBlank { null },
        onBack = { onEvent(BehindWallEvent.Back) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.roomMissing -> EmptyState(
                icon = Icons.Rounded.MeetingRoom,
                headline = stringResource(R.string.room_not_found_title),
                body = stringResource(R.string.room_not_found_body),
                actionLabel = stringResource(R.string.room_back),
                onAction = { onEvent(BehindWallEvent.Back) },
                modifier = Modifier.padding(padding),
            )
            room != null -> BehindWallWalls(state, room, padding, onEvent)
            state.rooms.isEmpty() -> EmptyState(
                icon = Icons.Rounded.GridView,
                headline = stringResource(R.string.behind_wall_empty_title),
                body = stringResource(R.string.behind_wall_empty_body),
                actionLabel = stringResource(R.string.house_edit_plan),
                onAction = { onEvent(BehindWallEvent.EditPlan) },
                modifier = Modifier.padding(padding),
                actionModifier = Modifier.testTag("${LocalTagPrefix.current}_behind_edit_plan"),
            )
            else -> BehindWallRooms(state, padding, onEvent)
        }
    }
}

@Composable
private fun BehindWallRooms(state: BehindWallUiState, padding: PaddingValues, onEvent: (BehindWallEvent) -> Unit) {
    val prefix = LocalTagPrefix.current
    val byFloor = state.rooms.groupBy { it.floorName }
    LazyColumn(Modifier.fillMaxSize().testTag("${prefix}_behind_rooms"), contentPadding = padding) {
        item {
            Text(
                stringResource(R.string.behind_wall_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        byFloor.forEach { (floor, rooms) ->
            if (byFloor.size > 1) {
                item(key = "floor:$floor") {
                    Text(
                        floor,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                    )
                }
            }
            items(rooms, key = { it.id }) { room ->
                val p = room.progress
                RecordListItem(
                    headline = room.name.ifBlank { stringResource(R.string.plan_unnamed_room) },
                    supporting = if (p.total == 0) stringResource(R.string.behind_wall_room_no_walls)
                    else stringResource(R.string.behind_wall_room_progress, p.roughIn, p.total, p.finished),
                    leading = {
                        if (p.complete) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Rounded.MeetingRoom, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = { onEvent(BehindWallEvent.OpenRoom(room.id)) },
                    modifier = Modifier.testTag("${prefix}_behind_room_${room.id}"),
                )
            }
        }
    }
}

@Composable
private fun BehindWallWalls(state: BehindWallUiState, room: BehindWallRoom, padding: PaddingValues, onEvent: (BehindWallEvent) -> Unit) {
    val prefix = LocalTagPrefix.current
    LazyColumn(Modifier.fillMaxSize().testTag("${prefix}_behind_walls"), contentPadding = padding) {
        item {
            Text(
                stringResource(if (room.walls.isEmpty()) R.string.room_section_walls_empty else R.string.behind_wall_walls_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (state.photoImportFailed) {
            item {
                Text(
                    stringResource(R.string.wall_photo_import_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("${prefix}_behind_photo_error"),
                )
            }
        }
        items(room.walls, key = { it.id }) { wall -> BehindWallRow(wall, state, onEvent) }
        if (room.walls.isNotEmpty()) {
            item {
                TextButton(
                    onClick = { onEvent(BehindWallEvent.OpenRoomPage) },
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp).heightIn(min = 48.dp).testTag("${prefix}_behind_open_room"),
                ) {
                    Icon(Icons.Rounded.Layers, contentDescription = null)
                    Text(stringResource(R.string.behind_wall_open_room), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    val sheetWall = state.photoSheetFor?.let { id -> room.walls.firstOrNull { it.id == id } }
    if (sheetWall != null) {
        OptionsSheet(
            title = stringResource(R.string.behind_wall_sheet_title, wallName(sheetWall), stringResource(sheetWall.nextBehindWallStage.labelRes)),
            options = listOf(
                SheetOption("wall_take", stringResource(R.string.wall_photo_take), Icons.Rounded.PhotoCamera),
                SheetOption("wall_choose", stringResource(R.string.wall_photo_choose), Icons.Rounded.PhotoLibrary),
            ),
            onDismiss = { onEvent(BehindWallEvent.CloseSheet) },
            onPick = { onEvent(if (it.id == "wall_take") BehindWallEvent.TakePhoto else BehindWallEvent.ChoosePhoto) },
        )
    }
}

/** A wall: its newest photo, side and length with the stages photographed, and the stage its next photo is saved at. */
@Composable
private fun BehindWallRow(wall: RoomWall, state: BehindWallUiState, onEvent: (BehindWallEvent) -> Unit) {
    val prefix = LocalTagPrefix.current
    val name = wallName(wall)
    val length = LengthParser.format(wall.lengthIn, state.system)
    val photographed = WallStage.entries.filter { it in wall.thumbnails }
    val photos = if (photographed.isEmpty()) stringResource(R.string.room_wall_no_photos, length)
    else stringResource(R.string.room_wall_photos, length, photographed.map { stringResource(it.labelRes) }.joinToString(", "))
    val next = wall.nextBehindWallStage
    val shownStage = photographed.lastOrNull()
    ListItem(
        headlineContent = { Text(name, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(photos, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(if (next == WallStage.FINISHED) R.string.behind_wall_next_finished else R.string.behind_wall_next_rough_in),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("${prefix}_behind_wall_next_${wall.id}"),
                )
            }
        },
        leadingContent = {
            WallThumbnail(
                shownStage?.let { wall.thumbnails[it] },
                shownStage?.let { stringResource(R.string.room_wall_photo_description, stringResource(it.labelRes), name) }.orEmpty(),
            )
        },
        trailingContent = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .heightIn(min = 72.dp)
            .clickable(onClickLabel = stringResource(R.string.behind_wall_photograph, name)) { onEvent(BehindWallEvent.PickWall(wall.id)) }
            .testTag("${prefix}_behind_wall_${wall.id}"),
    )
}
