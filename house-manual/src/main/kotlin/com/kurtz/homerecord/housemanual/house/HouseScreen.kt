package com.kurtz.homerecord.housemanual.house

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.OptionsSheet
import com.kurtz.homerecord.coreui.components.SheetOption
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.blueprint.BlueprintCanvas
import com.kurtz.homerecord.housemodel.blueprint.LayerToggleRow
import com.kurtz.homerecord.housemodel.plan.PlanRoom

/** HM-House: the blueprint of the selected floor. Tap a room to open it, long press for Rename, Edit shape, Delete. */
@Composable
fun HouseScreen(
    state: HouseUiState,
    onEvent: (HouseEvent) -> Unit,
    onOpenRoom: (PlanRoom) -> Unit,
    onEditPlan: () -> Unit,
    onEditShape: (PlanRoom) -> Unit,
    onOpenFloors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    val header: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(state.homeName, style = MaterialTheme.typography.headlineMedium)
            Text(state.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val floorChips: @Composable () -> Unit = {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.floors.reversed(), key = { it.id }) { floor ->
                FilterChip(
                    selected = floor.id == state.floorId,
                    onClick = { onEvent(HouseEvent.SelectFloor(floor.id)) },
                    modifier = Modifier.testTag("hm_house_floor_${floor.id}"),
                    label = { Text(floor.name) },
                )
            }
        }
    }
    val canvas: @Composable (Modifier) -> Unit = { canvasModifier ->
        Box(canvasModifier.fillMaxWidth()) {
            if (state.rooms.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.GridView,
                    headline = stringResource(R.string.house_floor_empty_title),
                    body = stringResource(R.string.house_floor_empty_body),
                    actionLabel = stringResource(R.string.house_edit_plan),
                    onAction = onEditPlan,
                )
            } else {
                BlueprintCanvas(
                    rooms = state.rooms,
                    modifier = Modifier.fillMaxSize(),
                    layers = state.layers,
                    system = state.system,
                    onRoomClick = onOpenRoom,
                    onRoomLongClick = { onEvent(HouseEvent.LongPressRoom(it)) },
                )
            }
        }
    }
    val hint: @Composable () -> Unit = {
        if (state.rooms.isNotEmpty()) {
            Text(
                stringResource(R.string.house_blueprint_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    val layerRow: @Composable () -> Unit = { LayerToggleRow(state.layers, { onEvent(HouseEvent.ToggleLayer(it)) }) }
    val actions: @Composable () -> Unit = {
        val buttons: @Composable (Modifier) -> Unit = { m ->
            OutlinedButton(onClick = onEditPlan, modifier = m.testTag("hm_house_edit_plan")) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Text(stringResource(R.string.house_edit_plan), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onOpenFloors, modifier = m.testTag("hm_house_floors")) {
                Icon(Icons.Rounded.Layers, contentDescription = null)
                Text(stringResource(R.string.floors_row), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = { onEvent(HouseEvent.ExportPdf) },
                enabled = state.rooms.isNotEmpty() && !state.exporting,
                modifier = m.testTag("hm_house_export"),
            ) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                Text(stringResource(R.string.house_export_pdf), modifier = Modifier.padding(start = 8.dp))
            }
        }
        // Three buttons side by side break words at large text, so they stack then.
        if (landscape || LocalDensity.current.fontScale > 1.3f) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { buttons(Modifier.weight(1f)) }
        }
    }
    val statusPane: @Composable (Modifier) -> Unit = { m ->
        when {
            state.loading -> LoadingState(m)
            else -> EmptyState(
                icon = Icons.Rounded.Layers,
                headline = stringResource(R.string.plan_no_floors_title),
                body = stringResource(R.string.plan_no_floors_body),
                actionLabel = stringResource(R.string.plan_open_floors),
                onAction = onOpenFloors,
                modifier = m,
            )
        }
    }
    val ready = !state.loading && state.floorId != null
    if (landscape && ready) {
        // Landscape: controls sit beside the canvas so the plan keeps the full height.
        Row(modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                header()
                floorChips()
                hint()
                layerRow()
                actions()
            }
            canvas(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier.fillMaxSize().statusBarsPadding()) {
            header()
            if (!ready) {
                statusPane(Modifier.weight(1f))
            } else {
                floorChips()
                canvas(Modifier.weight(1f))
                hint()
                layerRow()
                actions()
            }
        }
    }
    HouseDialogs(state.dialog, onEvent, onEditShape)
}

@Composable
private fun HouseDialogs(dialog: HouseDialog?, onEvent: (HouseEvent) -> Unit, onEditShape: (PlanRoom) -> Unit) {
    when (dialog) {
        null -> Unit
        is HouseDialog.Menu -> OptionsSheet(
            title = dialog.room.name.ifBlank { stringResource(R.string.plan_unnamed_room) },
            options = listOf(
                SheetOption("rename", stringResource(R.string.house_room_rename), Icons.Rounded.Edit),
                SheetOption("shape", stringResource(R.string.plan_edit_shape), Icons.Rounded.Polyline),
                SheetOption("delete", stringResource(R.string.house_room_delete), Icons.Rounded.Delete),
            ),
            onDismiss = { onEvent(HouseEvent.DismissDialog) },
            onPick = {
                when (it.id) {
                    "rename" -> onEvent(HouseEvent.StartRename(dialog.room))
                    "shape" -> { onEvent(HouseEvent.DismissDialog); onEditShape(dialog.room) }
                    else -> onEvent(HouseEvent.StartDelete(dialog.room))
                }
            },
        )
        is HouseDialog.Rename -> {
            var name by rememberSaveable(dialog.room.id) { mutableStateOf(dialog.room.name) }
            AlertDialog(
                onDismissRequest = { onEvent(HouseEvent.DismissDialog) },
                title = { Text(stringResource(R.string.house_room_rename)) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.plan_room_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onEvent(HouseEvent.Rename(dialog.room.id, name)) }, enabled = name.isNotBlank()) {
                        Text(stringResource(R.string.plan_done))
                    }
                },
                dismissButton = { TextButton(onClick = { onEvent(HouseEvent.DismissDialog) }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        is HouseDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { onEvent(HouseEvent.DismissDialog) },
            title = { Text(stringResource(R.string.house_room_delete_title, dialog.room.name.ifBlank { stringResource(R.string.plan_unnamed_room) })) },
            text = { Text(stringResource(R.string.house_room_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(HouseEvent.ConfirmDelete(dialog.room.id)) }) { Text(stringResource(R.string.house_room_delete)) }
            },
            dismissButton = { TextButton(onClick = { onEvent(HouseEvent.DismissDialog) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
