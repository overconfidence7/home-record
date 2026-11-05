package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.components.windowTestTags
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.blueprint.BlueprintCanvas
import com.kurtz.homerecord.housemodel.blueprint.BlueprintPin
import com.kurtz.homerecord.housemodel.blueprint.LayerToggleRow
import com.kurtz.homerecord.housemodel.blueprint.PlanMark
import com.kurtz.homerecord.housemodel.blueprint.PlanMarks
import com.kurtz.homerecord.housemodel.blueprint.shows
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
    /** Task pins on the floor in view (FL-House, AC-F35); House Manual has none. */
    pins: List<BlueprintPin> = emptyList(),
    onOpenPin: (String) -> Unit = {},
    /** Opens HM-3D on this floor (AC-C20); no button when null. */
    on3D: (() -> Unit)? = null,
    /** Field Log's "Behind the wall" capture: pick a room, then a wall, then the camera (AC-F49); no button when null. */
    onBehindWall: (() -> Unit)? = null,
    /** A tapped wall overlay, plan overlay or item on the blueprint (T055a); marks are not tappable when null. */
    onOpenMark: ((PlanMark) -> Unit)? = null,
    /** The home's name and address over the plan; off in Field Log, whose job top bar already names the job (rule 2). */
    showHeader: Boolean = true,
) {
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    val header: @Composable () -> Unit = {
        if (showHeader) Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                    modifier = Modifier.testTag("${LocalTagPrefix.current}_house_floor_${floor.id}"),
                    label = { Text(floor.name) },
                )
            }
        }
    }
    val canvas: @Composable (Modifier) -> Unit = { canvasModifier ->
        Box(canvasModifier.fillMaxWidth().testTag("${LocalTagPrefix.current}_house_plan")) {
            if (state.rooms.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.GridView,
                    headline = stringResource(R.string.house_floor_empty_title),
                    body = stringResource(R.string.house_floor_empty_body),
                    actionLabel = stringResource(R.string.house_edit_plan),
                    onAction = onEditPlan,
                    actionModifier = Modifier.testTag("${LocalTagPrefix.current}_house_edit_plan"),
                )
            } else {
                BlueprintCanvas(
                    rooms = state.rooms,
                    modifier = Modifier.fillMaxSize(),
                    layers = state.layers,
                    overlays = state.overlays,
                    marks = state.marks,
                    system = state.system,
                    pins = pins,
                    onPinClick = onOpenPin,
                    onRoomClick = onOpenRoom,
                    onRoomLongClick = { onEvent(HouseEvent.LongPressRoom(it)) },
                    onMarkClick = onOpenMark,
                )
            }
        }
    }
    val hint: @Composable () -> Unit = {
        if (state.rooms.isNotEmpty()) {
            val base = stringResource(if (pins.isEmpty()) R.string.house_blueprint_hint else R.string.house_blueprint_hint_pins)
            val marksShown = onOpenMark != null &&
                (PlanMarks.visible(state.marks, state.layers).isNotEmpty() || state.overlays.any { state.layers.shows(it) })
            Text(
                if (marksShown) base + " " + stringResource(R.string.house3d_marker_hint) else base,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    val layerRow: @Composable () -> Unit = { LayerToggleRow(state.layers, { onEvent(HouseEvent.ToggleLayer(it)) }) }
    val actions: @Composable () -> Unit = {
        val prefix = LocalTagPrefix.current
        val list = buildList {
            if (onBehindWall != null && state.rooms.isNotEmpty()) {
                add(HouseAction("${prefix}_house_behind_wall", Icons.Rounded.PhotoCamera, R.string.house_behind_wall, onClick = onBehindWall))
            }
            // An empty floor already offers Edit plan in its empty state; one primary action only.
            if (state.rooms.isNotEmpty()) add(HouseAction("${prefix}_house_edit_plan", Icons.Rounded.Edit, R.string.house_edit_plan, onClick = onEditPlan))
            if (on3D != null && state.rooms.isNotEmpty()) add(HouseAction("${prefix}_house_3d", Icons.Rounded.ViewInAr, R.string.house_3d, onClick = on3D))
            add(HouseAction("${prefix}_house_floors", Icons.Rounded.Layers, R.string.floors_row, onClick = onOpenFloors))
            add(
                HouseAction(
                    "${prefix}_house_export", Icons.Rounded.PictureAsPdf, R.string.house_export_pdf,
                    enabled = state.rooms.isNotEmpty() && !state.exporting, onClick = { onEvent(HouseEvent.ExportPdf) },
                ),
            )
        }
        val button: @Composable (HouseAction, Modifier) -> Unit = { a, m ->
            OutlinedButton(onClick = a.onClick, enabled = a.enabled, modifier = m.testTag(a.tag)) {
                Icon(a.icon, contentDescription = null)
                Text(stringResource(a.label), modifier = Modifier.padding(start = 8.dp), maxLines = 1, softWrap = false)
            }
        }
        when {
            landscape -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { button(it, Modifier.fillMaxWidth()) }
            }
            // At large text five stacked full-width buttons leave no room for the plan in portrait:
            // the first action keeps its button and the rest go in a More actions menu beside it.
            LocalDensity.current.fontScale > 1.3f -> Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                list.firstOrNull()?.let { button(it, Modifier.weight(1f)) }
                val rest = list.drop(1)
                if (rest.isNotEmpty()) {
                    var open by rememberSaveable { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { open = true }, modifier = Modifier.testTag("${prefix}_house_more")) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.house_more_actions))
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            rest.forEach { a ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(a.label)) },
                                    leadingIcon = { Icon(a.icon, contentDescription = null) },
                                    enabled = a.enabled,
                                    onClick = { open = false; a.onClick() },
                                    modifier = Modifier.testTag(a.tag),
                                )
                            }
                        }
                    }
                }
            }
            // Three labelled buttons in a third of a 360 dp phone each cut "Edit plan" and "Export
            // PDF" in two, so at most two share a row: Export PDF takes the full row under them.
            else -> FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) { list.forEach { button(it, Modifier.weight(1f)) } }
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

/** One of the House tab's actions under the plan. */
private class HouseAction(
    val tag: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    @androidx.annotation.StringRes val label: Int,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

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
                modifier = Modifier.windowTestTags(),
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
            modifier = Modifier.windowTestTags(),
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
