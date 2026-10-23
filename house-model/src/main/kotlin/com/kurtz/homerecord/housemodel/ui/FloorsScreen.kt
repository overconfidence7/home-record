package com.kurtz.homerecord.housemodel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.units.LengthParser
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LengthField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.FloorSummary

/** HM-Floors: floors listed top floor first, with move, edit and delete in each row's menu (AC-C3). */
@Composable
fun FloorsScreen(
    state: FloorsUiState,
    onEvent: (FloorsEvent) -> Unit,
    onBack: () -> Unit,
    homeName: String? = null,
    system: UnitSystem = UnitSystem.IMPERIAL,
) {
    ScreenScaffold(
        title = stringResource(R.string.floors_title),
        subtitle = homeName,
        onBack = onBack,
        floatingActionButton = {
            if (!state.loading && state.floors.isNotEmpty()) {
                FloatingActionButton(onClick = { onEvent(FloorsEvent.AddClicked) }, modifier = Modifier.testTag("hm_floors_fab")) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.floors_add))
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.floors.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Layers,
                headline = stringResource(R.string.floors_empty_title),
                body = stringResource(R.string.floors_empty_body),
                actionLabel = stringResource(R.string.floors_add),
                onAction = { onEvent(FloorsEvent.AddClicked) },
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                val top = state.floors.reversed()
                items(top, key = { it.id }) { floor ->
                    FloorRow(
                        floor = floor,
                        canMoveUp = floor.id != top.first().id,
                        canMoveDown = floor.id != top.last().id,
                        system = system,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
    when (val dialog = state.dialog) {
        FloorsDialog.Add -> FloorDialog(null, system, { onEvent(FloorsEvent.DismissDialog) }) { name, _ -> onEvent(FloorsEvent.ConfirmAdd(name)) }
        is FloorsDialog.Edit -> FloorDialog(dialog.floor, system, { onEvent(FloorsEvent.DismissDialog) }) { name, ceiling ->
            onEvent(FloorsEvent.ConfirmEdit(dialog.floor.id, name, ceiling))
        }
        is FloorsDialog.Delete -> AlertDialog(
            onDismissRequest = { onEvent(FloorsEvent.DismissDialog) },
            title = { Text(stringResource(R.string.floor_delete_title, dialog.floor.name)) },
            text = {
                Text(
                    when (dialog.floor.roomCount) {
                        0 -> stringResource(R.string.floor_delete_body_empty)
                        1 -> stringResource(R.string.floor_delete_body_one)
                        else -> stringResource(R.string.floor_delete_body_many, dialog.floor.roomCount)
                    },
                )
            },
            confirmButton = { TextButton({ onEvent(FloorsEvent.ConfirmDelete(dialog.floor.id)) }) { Text(stringResource(R.string.floor_delete)) } },
            dismissButton = { TextButton({ onEvent(FloorsEvent.DismissDialog) }) { Text(stringResource(R.string.cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun FloorRow(floor: FloorSummary, canMoveUp: Boolean, canMoveDown: Boolean, system: UnitSystem, onEvent: (FloorsEvent) -> Unit) {
    var menu by rememberSaveable { mutableStateOf(false) }
    val rooms = if (floor.roomCount == 1) stringResource(R.string.floor_room_one) else stringResource(R.string.floor_rooms, floor.roomCount)
    val ceiling = LengthParser.format(floor.ceilingHeightIn, system)
    val supporting = stringResource(R.string.floor_row_supporting, rooms, ceiling) +
        if (floor.halfLevel) " · " + stringResource(R.string.floor_half_level) else ""
    RecordListItem(
        headline = floor.name,
        supporting = supporting,
        leading = { Icon(Icons.Rounded.Layers, contentDescription = null) },
        onClick = { onEvent(FloorsEvent.EditClicked(floor)) },
        modifier = Modifier.testTag("row_${floor.id}"),
        trailing = {
            Column {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("hm_floors_menu_${floor.id}")) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.floor_more, floor.name))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.floor_move_up)) },
                        enabled = canMoveUp,
                        onClick = { menu = false; onEvent(FloorsEvent.MoveUp(floor)) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.floor_move_down)) },
                        enabled = canMoveDown,
                        onClick = { menu = false; onEvent(FloorsEvent.MoveDown(floor)) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.floor_edit)) },
                        onClick = { menu = false; onEvent(FloorsEvent.EditClicked(floor)) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.floor_delete)) },
                        onClick = { menu = false; onEvent(FloorsEvent.DeleteClicked(floor)) },
                    )
                }
            }
        },
    )
}

/** Add (floor == null) or edit a floor: two fields, so a dialog is allowed (ui-standards 15). */
@Composable
private fun FloorDialog(floor: FloorSummary?, system: UnitSystem, onDismiss: () -> Unit, onConfirm: (name: String, ceilingIn: Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf(floor?.name.orEmpty()) }
    var ceiling by rememberSaveable { mutableStateOf<Int?>(floor?.ceilingHeightIn ?: 96) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (floor == null) R.string.floor_add_title else R.string.floor_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(stringResource(R.string.floor_name), name, { name = it }, required = true)
                LengthField(stringResource(R.string.floor_ceiling), ceiling, { ceiling = it }, system)
            }
        },
        confirmButton = {
            TextButton(onClick = { ceiling?.let { onConfirm(name, it) } }, enabled = name.isNotBlank() && ceiling != null) {
                Text(stringResource(R.string.save_floor))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
