package com.kurtz.homerecord.housemodel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
                if (state.floors.size > 1) {
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
                if (state.rooms.isEmpty()) {
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
                if (state.spot != null) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton({ onEvent(ItemPlaceEvent.Remove) }, Modifier.heightIn(min = 48.dp).testTag("${prefix}_item_place_remove")) {
                            Icon(Icons.Rounded.Delete, null, Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.item_place_remove))
                        }
                    }
                } else {
                    Box(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}
