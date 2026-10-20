package com.kurtz.homerecord.housemanual.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurtz.homerecord.core.units.LengthParser
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LengthField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.OptionsSheet
import com.kurtz.homerecord.coreui.components.SheetOption
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import com.kurtz.homerecord.housemodel.plan.RoomKind

private const val HANDLE_RADIUS_DP = 10f
private const val HANDLE_HIT_DP = 24f

/** What a touch landed on. */
private sealed interface Target {
    data class Handle(val roomId: String, val edgeIndex: Int) : Target
    data class Body(val roomId: String) : Target
    data object None : Target
}

/** HM-Plan: one floor on a grid. Drag a room to move it, drag a handle to resize, pinch to zoom, two fingers to pan. */
@Composable
fun PlanEditorScreen(
    state: PlanEditorUiState,
    onEvent: (PlanEditorEvent) -> Unit,
    onBack: () -> Unit,
    homeName: String? = null,
    onOpenFloors: () -> Unit = {},
) {
    var canvasWidthDp by remember { mutableStateOf(0f) }
    var canvasHeightDp by remember { mutableStateOf(0f) }
    ScreenScaffold(
        title = stringResource(R.string.plan_title),
        subtitle = homeName,
        onBack = onBack,
        actions = {
            IconButton(onClick = { onEvent(PlanEditorEvent.Fit(canvasWidthDp, canvasHeightDp)) }, enabled = state.rooms.isNotEmpty()) {
                Icon(Icons.Rounded.CenterFocusStrong, contentDescription = stringResource(R.string.plan_fit))
            }
            IconButton(onClick = { onEvent(PlanEditorEvent.Undo) }, enabled = state.canUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.plan_undo))
            }
            IconButton(onClick = { onEvent(PlanEditorEvent.Redo) }, enabled = state.canRedo) {
                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.plan_redo))
            }
        },
        floatingActionButton = {
            if (!state.loading && state.floorId != null && state.selected == null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val z = state.zoom
                        val center = Pt(((canvasWidthDp / 2f - state.panX) / z).toInt(), ((canvasHeightDp / 2f - state.panY) / z).toInt())
                        onEvent(PlanEditorEvent.AddRoom(center))
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.plan_add_room)) },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.floorId == null -> EmptyState(
                icon = Icons.Rounded.GridView,
                headline = stringResource(R.string.plan_no_floors_title),
                body = stringResource(R.string.plan_no_floors_body),
                actionLabel = stringResource(R.string.plan_open_floors),
                onAction = onOpenFloors,
                modifier = Modifier.padding(padding),
            )
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                FloorSwitcher(state, onEvent)
                if (state.unplaced.isNotEmpty()) UnplacedRow(state, onEvent, canvasWidthDp, canvasHeightDp)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PlanCanvas(
                        state = state,
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxSize(),
                        canvasWidthDp = canvasWidthDp,
                        canvasHeightDp = canvasHeightDp,
                        onSized = { w, h -> canvasWidthDp = w; canvasHeightDp = h },
                    )
                    if (state.rooms.isEmpty() && state.unplaced.isEmpty()) {
                        Text(
                            stringResource(R.string.plan_empty_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        )
                    }
                }
                state.selected?.let { room -> RoomPanel(state, room, onEvent) }
            }
        }
    }
}

@Composable
private fun FloorSwitcher(state: PlanEditorUiState, onEvent: (PlanEditorEvent) -> Unit) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.floors.reversed(), key = { it.id }) { floor ->
            FilterChip(
                selected = floor.id == state.floorId,
                onClick = { onEvent(PlanEditorEvent.SelectFloor(floor.id)) },
                label = { Text(floor.name) },
            )
        }
    }
}

@Composable
private fun UnplacedRow(state: PlanEditorUiState, onEvent: (PlanEditorEvent) -> Unit, widthDp: Float, heightDp: Float) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.plan_unplaced), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.unplaced, key = { it.id }) { room ->
                AssistChip(
                    onClick = {
                        val z = state.zoom
                        onEvent(PlanEditorEvent.PlaceRoom(room.id, Pt(((widthDp / 2f - state.panX) / z).toInt(), ((heightDp / 2f - state.panY) / z).toInt())))
                    },
                    label = { Text(stringResource(R.string.plan_place_room, room.name.ifBlank { stringResource(R.string.plan_unnamed_room) })) },
                )
            }
        }
    }
}

@Composable
private fun PlanCanvas(
    state: PlanEditorUiState,
    onEvent: (PlanEditorEvent) -> Unit,
    modifier: Modifier,
    canvasWidthDp: Float,
    canvasHeightDp: Float,
    onSized: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current.density
    val current by rememberUpdatedState(state)
    val send by rememberUpdatedState(onEvent)
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val label = MaterialTheme.typography.labelMedium
    val summary = pluralStringResource(R.plurals.plan_canvas_description, state.rooms.size, state.rooms.size)
    var sized by remember { mutableStateOf(false) }
    LaunchedEffect(state.floorId, sized) {
        // First open of a floor with no saved view: fit the rooms once the canvas size is known.
        if (sized && state.rooms.isNotEmpty() && state.zoom == 1f && state.panX == 0f && state.panY == 0f) {
            onEvent(PlanEditorEvent.Fit(canvasWidthDp, canvasHeightDp))
        }
    }
    Canvas(
        modifier
            .semantics { contentDescription = summary }
            .onSizeChanged {
                onSized(it.width / density, it.height / density)
                if (!sized) sized = true
            }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = current
                    val target = hit(start, down.position.x / density, down.position.y / density)
                    var total = Offset.Zero
                    var moved = false
                    var multi = false
                    var dragging = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.changes.count { it.pressed } >= 2) {
                            if (dragging) { send(PlanEditorEvent.DragEnd); dragging = false }
                            multi = true
                            val c = event.calculateCentroid()
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            val now = current
                            val t = PlanEditorViewModel.zoomAround(now, zoom, c.x / density, c.y / density)
                            send(t.copy(panX = t.panX + pan.x / density, panY = t.panY + pan.y / density))
                            event.changes.forEach { it.consume() }
                        } else if (!multi) {
                            val change = event.changes.first()
                            total += change.positionChange()
                            if (!moved && total.getDistance() > slop) moved = true
                            if (moved && change.pressed) {
                                val dxDp = total.x / density
                                val dyDp = total.y / density
                                when (target) {
                                    is Target.Handle -> {
                                        val room = start.rooms.firstOrNull { it.id == target.roomId }
                                        val horizontal = room?.let { it.points[target.edgeIndex].y == it.points[(target.edgeIndex + 1) % it.points.size].y } ?: true
                                        val delta = PlanEditorViewModel.toInches(if (horizontal) dyDp else dxDp, start.zoom)
                                        send(PlanEditorEvent.DragEdge(target.roomId, target.edgeIndex, delta))
                                        dragging = true
                                    }
                                    is Target.Body -> {
                                        send(PlanEditorEvent.DragMove(target.roomId, PlanEditorViewModel.toInches(dxDp, start.zoom), PlanEditorViewModel.toInches(dyDp, start.zoom)))
                                        dragging = true
                                    }
                                    Target.None -> send(PlanEditorEvent.Transform(start.zoom, start.panX + dxDp, start.panY + dyDp))
                                }
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (dragging) send(PlanEditorEvent.DragEnd)
                    if (!moved && !multi) {
                        send(PlanEditorEvent.Select((target as? Target.Body)?.roomId ?: (target as? Target.Handle)?.roomId))
                    }
                }
            },
    ) {
        drawPlan(state, measurer, density, scheme.outlineVariant, scheme.surfaceContainerHigh, scheme.primaryContainer, scheme.onPrimaryContainer,
            scheme.primary, scheme.errorContainer, scheme.error, scheme.onSurface, label)
    }
}

private fun hit(s: PlanEditorUiState, xDp: Float, yDp: Float): Target {
    val sel = s.selected
    if (sel != null && sel.isAxisAligned()) {
        for (i in sel.points.indices) {
            val a = sel.points[i]
            val b = sel.points[(i + 1) % sel.points.size]
            val mx = (a.x + b.x) / 2f * s.zoom + s.panX
            val my = (a.y + b.y) / 2f * s.zoom + s.panY
            if (kotlin.math.abs(mx - xDp) <= HANDLE_HIT_DP && kotlin.math.abs(my - yDp) <= HANDLE_HIT_DP) return Target.Handle(sel.id, i)
        }
    }
    val wx = (xDp - s.panX) / s.zoom
    val wy = (yDp - s.panY) / s.zoom
    val ordered = s.rooms.sortedBy { if (it.id == s.selectedId) 1 else 0 }.reversed()
    ordered.firstOrNull { it.contains(wx, wy) }?.let { return Target.Body(it.id) }
    return Target.None
}

private fun PlanRoom.isAxisAligned(): Boolean = points.size == 4 &&
    points.indices.all { i -> val a = points[i]; val b = points[(i + 1) % 4]; (a.x == b.x) != (a.y == b.y) }

private fun PlanRoom.contains(x: Float, y: Float): Boolean {
    var inside = false
    var j = points.size - 1
    for (i in points.indices) {
        val pi = points[i]
        val pj = points[j]
        if ((pi.y > y) != (pj.y > y) && x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y).toFloat() + pi.x) inside = !inside
        j = i
    }
    return inside
}

private fun DrawScope.drawPlan(
    s: PlanEditorUiState,
    measurer: TextMeasurer,
    density: Float,
    gridColor: Color,
    roomFill: Color,
    selectedFill: Color,
    roomText: Color,
    primary: Color,
    flaggedFill: Color,
    flaggedLine: Color,
    textColor: Color,
    labelStyle: androidx.compose.ui.text.TextStyle,
) {
    val z = s.zoom * density
    val ox = s.panX * density
    val oy = s.panY * density
    // Grid: a line every foot (or metre-ish block) once zoomed in enough, every 5 ft otherwise.
    val stepIn = if (s.zoom >= 0.5f) 12 else 60
    val left = (-ox / z).toInt() / stepIn * stepIn - stepIn
    val top = (-oy / z).toInt() / stepIn * stepIn - stepIn
    var gx = left
    while (gx * z + ox < size.width) {
        drawLine(gridColor.copy(alpha = if (gx % 60 == 0) 0.7f else 0.35f), Offset(gx * z + ox, 0f), Offset(gx * z + ox, size.height))
        gx += stepIn
    }
    var gy = top
    while (gy * z + oy < size.height) {
        drawLine(gridColor.copy(alpha = if (gy % 60 == 0) 0.7f else 0.35f), Offset(0f, gy * z + oy), Offset(size.width, gy * z + oy))
        gy += stepIn
    }
    s.rooms.forEach { room ->
        val path = Path().apply {
            room.points.forEachIndexed { i, p ->
                val x = p.x * z + ox
                val y = p.y * z + oy
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val flagged = room.id in s.flaggedIds
        val selected = room.id == s.selectedId
        drawPath(path, if (flagged) flaggedFill else if (selected) selectedFill else roomFill)
        drawPath(path, if (flagged) flaggedLine else if (selected) primary else textColor.copy(alpha = 0.6f), style = Stroke(width = (if (selected) 3f else 1.5f) * density))
        val name = room.name.ifBlank { "·" }
        val layout = measurer.measure(name, labelStyle.copy(color = if (selected) roomText else textColor, fontSize = (labelStyle.fontSize.value).sp))
        val cx = (room.minX + room.maxX) / 2f * z + ox
        val cy = (room.minY + room.maxY) / 2f * z + oy
        if (layout.size.width < (room.maxX - room.minX) * z) {
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
        }
    }
    s.selected?.takeIf { it.isAxisAligned() }?.let { room ->
        room.points.indices.forEach { i ->
            val a = room.points[i]
            val b = room.points[(i + 1) % room.points.size]
            drawCircle(primary, HANDLE_RADIUS_DP * density, Offset((a.x + b.x) / 2f * z + ox, (a.y + b.y) / 2f * z + oy))
        }
    }
}

@Composable
private fun RoomPanel(state: PlanEditorUiState, room: PlanRoom, onEvent: (PlanEditorEvent) -> Unit) {
    var pickingKind by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(room.id, state.focusName) { if (state.focusName) focus.requestFocus() }
    BoxWithConstraints {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.heightIn(max = maxHeight * 0.55f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                key(room.id) {
                    LabeledField(
                        label = stringResource(R.string.plan_room_name),
                        value = room.name,
                        onValueChange = { onEvent(PlanEditorEvent.Rename(it)) },
                        modifier = Modifier.focusRequester(focus),
                    )
                }
                OutlinedButton(onClick = { pickingKind = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.plan_room_kind, kindLabel(RoomKind.parse(room.kind))))
                }
                if (room.points.size == 4) {
                    key(room.id, state.fieldsVersion) {
                        var width by remember { mutableStateOf<Int?>(room.widthIn) }
                        var length by remember { mutableStateOf<Int?>(room.lengthIn) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LengthField(
                                label = stringResource(R.string.plan_room_width),
                                inches = room.widthIn,
                                onInchesChange = { width = it; commitSize(width, length, onEvent) },
                                system = state.system,
                                modifier = Modifier.weight(1f),
                            )
                            LengthField(
                                label = stringResource(R.string.plan_room_length),
                                inches = room.lengthIn,
                                onInchesChange = { length = it; commitSize(width, length, onEvent) },
                                system = state.system,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.plan_room_size_summary, LengthParser.format(room.widthIn, state.system), LengthParser.format(room.lengthIn, state.system)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (room.id in state.flaggedIds) {
                    Text(stringResource(R.string.plan_room_flagged), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onEvent(PlanEditorEvent.Select(null)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.plan_done))
                    }
                    OutlinedButton(onClick = { onEvent(PlanEditorEvent.Delete) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Text(stringResource(R.string.plan_delete_room), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    if (pickingKind) {
        OptionsSheet(
            title = stringResource(R.string.plan_kind_title),
            options = RoomKind.entries.map { SheetOption(it.name, kindLabel(it)) },
            onDismiss = { pickingKind = false },
            onPick = { pickingKind = false; onEvent(PlanEditorEvent.SetKind(RoomKind.parse(it.id))) },
        )
    }
}

private fun commitSize(width: Int?, length: Int?, onEvent: (PlanEditorEvent) -> Unit) {
    if (width != null && length != null) onEvent(PlanEditorEvent.SetSize(width, length))
}

@Composable
internal fun kindLabel(kind: RoomKind): String = stringResource(
    when (kind) {
        RoomKind.KITCHEN -> R.string.room_kind_kitchen
        RoomKind.LIVING_ROOM -> R.string.room_kind_living_room
        RoomKind.DINING_ROOM -> R.string.room_kind_dining_room
        RoomKind.BEDROOM -> R.string.room_kind_bedroom
        RoomKind.BATHROOM -> R.string.room_kind_bathroom
        RoomKind.OFFICE -> R.string.room_kind_office
        RoomKind.LAUNDRY -> R.string.room_kind_laundry
        RoomKind.GARAGE -> R.string.room_kind_garage
        RoomKind.BASEMENT -> R.string.room_kind_basement
        RoomKind.UTILITY -> R.string.room_kind_utility
        RoomKind.HALLWAY -> R.string.room_kind_hallway
        RoomKind.CLOSET -> R.string.room_kind_closet
        RoomKind.MUDROOM -> R.string.room_kind_mudroom
        RoomKind.PORCH -> R.string.room_kind_porch
        RoomKind.DECK -> R.string.room_kind_deck
        RoomKind.ATTIC -> R.string.room_kind_attic
        RoomKind.MECHANICAL -> R.string.room_kind_mechanical
        RoomKind.OTHER -> R.string.room_kind_other
    },
)
