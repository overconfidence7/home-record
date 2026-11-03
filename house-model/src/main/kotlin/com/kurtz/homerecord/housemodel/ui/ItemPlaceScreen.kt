package com.kurtz.homerecord.housemodel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.blueprint.BlueprintCanvas

/**
 * Place on plan (AC-C30 position): the floor chips, a hint that says where the item is, the floor's blueprint with the
 * item's marker, and Remove from plan once it is placed. A tap anywhere on the plan moves the marker there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemPlaceScreen(state: ItemPlaceUiState, onEvent: (ItemPlaceEvent) -> Unit, modifier: Modifier = Modifier) {
    val prefix = LocalTagPrefix.current
    ScreenScaffold(
        title = stringResource(R.string.item_place_title),
        subtitle = state.itemName.takeIf { it.isNotBlank() },
        onBack = { onEvent(ItemPlaceEvent.Done) },
        actions = {
            if (!state.loading && !state.missing) {
                TextButton({ onEvent(ItemPlaceEvent.Done) }, Modifier.heightIn(min = 48.dp).testTag("${prefix}_item_place_done")) {
                    Text(stringResource(R.string.item_place_done))
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.missing -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                headline = stringResource(R.string.item_place_missing_title),
                body = stringResource(R.string.item_place_missing_body),
                actionLabel = stringResource(R.string.item_place_back),
                onAction = { onEvent(ItemPlaceEvent.Done) },
                modifier = Modifier.padding(padding),
            )
            else -> Column(modifier.fillMaxSize().padding(padding)) {
                if (state.floors.size > 1 && !state.wallMode) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.floors, key = { it.id }) { f ->
                            FilterChip(
                                selected = f.id == state.floorId,
                                onClick = { onEvent(ItemPlaceEvent.PickFloor(f.id)) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_item_place_floor_${f.id}"),
                                label = { Text(f.name) },
                            )
                        }
                    }
                }
                if (state.wallMode) {
                    WallPicker(state, onEvent, Modifier.weight(1f))
                } else if (state.rooms.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.Map,
                        headline = stringResource(R.string.item_place_empty_title, state.floorName.orEmpty()),
                        body = stringResource(R.string.item_place_empty_body),
                        actionLabel = stringResource(R.string.house_edit_plan),
                        onAction = { onEvent(ItemPlaceEvent.EditPlan) },
                        modifier = Modifier.weight(1f),
                        actionModifier = Modifier.testTag("${prefix}_item_place_edit_plan"),
                    )
                } else {
                    val room = state.spotRoomName
                    Text(
                        if (room != null) stringResource(R.string.item_place_hint_set, room)
                        else stringResource(R.string.item_place_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("${prefix}_item_place_hint"),
                    )
                    BlueprintCanvas(
                        rooms = state.rooms,
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("${prefix}_item_place_plan"),
                        layers = state.layers,
                        marks = state.marks,
                        system = state.system,
                        onPlanTap = { x, y -> onEvent(ItemPlaceEvent.Place(x, y)) },
                    )
                }
                FlowRow(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (state.wallMode) {
                        TextButton({ onEvent(ItemPlaceEvent.WallMode(false)) }, Modifier.heightIn(min = 48.dp).testTag("${prefix}_item_place_back_to_plan")) {
                            Icon(Icons.Rounded.Map, null, Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.item_place_back_to_plan))
                        }
                    } else if (state.canHang) {
                        TextButton({ onEvent(ItemPlaceEvent.WallMode(true)) }, Modifier.heightIn(min = 48.dp).testTag("${prefix}_item_place_on_wall")) {
                            Icon(Icons.Rounded.CropPortrait, null, Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.item_place_on_wall))
                        }
                    }
                    if (state.spot != null) {
                        TextButton({ onEvent(ItemPlaceEvent.Remove) }, Modifier.heightIn(min = 48.dp).testTag("${prefix}_item_place_remove")) {
                            Icon(Icons.Rounded.Delete, null, Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.item_place_remove))
                        }
                    }
                }
            }
        }
    }
}

/**
 * On a wall (AC-C30 position): chips for the walls of the item's room, a hint that says where it hangs, and the wall
 * drawn to its length and height with its photo when there is one; a tap hangs the item there.
 */
@Composable
private fun WallPicker(state: ItemPlaceUiState, onEvent: (ItemPlaceEvent) -> Unit, modifier: Modifier) {
    val prefix = LocalTagPrefix.current
    val wall = state.wall ?: return
    val name = wallName(wall)
    Column(modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.walls, key = { it.id }) { w ->
                FilterChip(
                    selected = w.id == wall.id,
                    onClick = { onEvent(ItemPlaceEvent.PickWall(w.id)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_item_place_wall_${w.id}"),
                    label = { Text(wallName(w)) },
                    leadingIcon = { Icon(Icons.Rounded.CropPortrait, null, Modifier.size(18.dp)) },
                )
            }
        }
        val spot = state.wallSpotHere
        Text(
            if (spot != null) stringResource(R.string.item_place_wall_hint_set, name) else stringResource(R.string.item_place_wall_hint, name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("${prefix}_item_place_wall_hint"),
        )
        val ratio = (wall.lengthIn.toFloat() / wall.heightIn.coerceAtLeast(1)).coerceIn(0.4f, 6f)
        val picture = wall.thumbnails.maxByOrNull { it.key.ordinal }?.value
        val description = stringResource(R.string.item_place_wall_canvas, name)
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            BoxWithConstraints(
                Modifier
                    .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .semantics { contentDescription = description }
                    .testTag("${prefix}_item_place_wall")
                    .pointerInput(wall.id) {
                        detectTapGestures { p ->
                            onEvent(ItemPlaceEvent.HangOnWall(wall.id, p.x / size.width, p.y / size.height))
                        }
                    },
            ) {
                if (picture != null) {
                    AsyncImage(model = File(picture), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
                }
                if (spot != null) {
                    val marker = 40.dp
                    Box(
                        Modifier
                            .offset(x = maxWidth * spot.x - marker / 2, y = maxHeight * spot.y - marker / 2)
                            .size(marker)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
                            .testTag("${prefix}_item_place_wall_marker"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(itemIcon(state.iconName), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
