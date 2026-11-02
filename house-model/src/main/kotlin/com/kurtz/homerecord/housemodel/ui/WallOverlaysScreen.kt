package com.kurtz.homerecord.housemodel.ui

import androidx.compose.foundation.Canvas
import com.kurtz.homerecord.coreui.components.windowTestTags
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayHitTest
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.overlays.color
import com.kurtz.homerecord.housemodel.overlays.icon
import com.kurtz.homerecord.housemodel.overlays.nameRes
import com.kurtz.homerecord.housemodel.walls.PhotoPoint
import com.kurtz.homerecord.housemodel.walls.WallOutline
import com.kurtz.homerecord.housemodel.walls.WallPoint
import com.kurtz.homerecord.housemodel.walls.WallSpaceMapper
import java.io.File
import kotlin.math.roundToInt

private val TouchRadius = 24.dp
private val SpotSize = 32.dp

/**
 * HM-Wall (AC-C25..C28): the wall at the chosen stage with every overlay drawn on it in its kind's color, the overlays as a
 * list below, "Add overlay" to draw one by tapping points, and the selected overlay's points as handles to drag. Shows the
 * form instead while one is open. [photo] stands in for the picture file in previews.
 */
@Composable
fun WallOverlaysScreen(state: WallOverlaysUiState, onEvent: (WallOverlaysEvent) -> Unit, photo: Painter? = null) {
    val form = state.form
    if (form != null) {
        OverlayFormScreen(form, onEvent, items = state.itemRows, linked = state.item(form.itemId), canAddPhoto = state.canAddPhoto, houseHasItems = state.items.isNotEmpty())
        return
    }
    val prefix = LocalTagPrefix.current
    val wall = state.wall
    val idle = state.drawing == null && state.selected == null
    ScreenScaffold(
        title = wall?.let { wallName(it) } ?: stringResource(R.string.wall_overlays_title),
        subtitle = state.roomName,
        onBack = { onEvent(WallOverlaysEvent.Back) },
        floatingActionButton = {
            if (wall != null && idle && !state.loading) {
                ExtendedFloatingActionButton(
                    onClick = { onEvent(WallOverlaysEvent.StartDrawing) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.wall_overlays_add)) },
                    modifier = Modifier.testTag("${prefix}_wall_add_overlay"),
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            wall == null -> EmptyState(
                icon = Icons.Rounded.Straighten,
                headline = stringResource(R.string.wall_overlays_gone_title),
                body = stringResource(R.string.wall_overlays_gone_body),
                actionLabel = stringResource(R.string.room_back),
                onAction = { onEvent(WallOverlaysEvent.Back) },
                modifier = Modifier.padding(padding),
            )
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                WallStagePicker(state.stage, { onEvent(WallOverlaysEvent.SelectStage(it)) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), screen = "wall")
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 360.dp), contentAlignment = Alignment.Center) {
                    val width = if (maxWidth / maxHeight > state.aspect) maxHeight * state.aspect else maxWidth
                    WallCanvas(state, photo, onEvent, Modifier.size(width, width / state.aspect))
                }
                if (state.picture == null) {
                    Text(
                        stringResource(R.string.wall_overlays_no_photo, stringResource(state.stage.labelRes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("${prefix}_wall_no_photo"),
                    )
                }
                if (state.error == WallOverlaysError.SaveFailed) {
                    Text(
                        stringResource(R.string.wall_overlays_save_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("${prefix}_wall_error"),
                    )
                }
                val drawing = state.drawing
                val selected = state.selected
                when {
                    drawing != null -> DrawingBar(drawing.size, onEvent)
                    selected != null -> SelectionBar(selected, state.item(selected.itemId)?.name, onEvent)
                    else -> OverlayList(state.overlays, onEvent, Modifier.weight(1f))
                }
            }
        }
    }
    if (state.confirmDelete) state.selected?.let { OverlayDeleteDialog(it, onEvent) }
}

/** Asks before deleting [overlay] (rule 3); [screen] names the test tags ("wall" or "plan"). */
@Composable
internal fun OverlayDeleteDialog(overlay: Overlay, onEvent: (WallOverlaysEvent) -> Unit, screen: String = "wall") {
    val prefix = LocalTagPrefix.current
    val name = overlay.title(stringResource(overlay.kind.nameRes))
    AlertDialog(
        modifier = Modifier.windowTestTags(),
        onDismissRequest = { onEvent(WallOverlaysEvent.CancelDelete) },
        title = { Text(stringResource(R.string.wall_overlay_delete_title, name)) },
        text = { Text(stringResource(R.string.wall_overlay_delete_body)) },
        confirmButton = {
            TextButton(onClick = { onEvent(WallOverlaysEvent.ConfirmDelete) }, modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_${screen}_overlay_delete_confirm")) {
                Text(stringResource(R.string.wall_overlay_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(WallOverlaysEvent.CancelDelete) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.wall_overlay_cancel)) }
        },
    )
}

/**
 * The wall picture with the overlays on top. Taps add points while drawing and pick an overlay otherwise; with one picked,
 * a drag that starts on one of its points moves that point and saves it when the finger lifts.
 */
@Composable
private fun WallCanvas(state: WallOverlaysUiState, preview: Painter?, onEvent: (WallOverlaysEvent) -> Unit, modifier: Modifier) {
    val prefix = LocalTagPrefix.current
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val outline = state.picture?.outline
    val picture = state.picture
    val painter = preview ?: picture?.let { rememberAsyncImagePainter(File(it.path), placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)) }
    var dragged by remember { mutableStateOf<Triple<String, Int, OverlayPoint>?>(null) }
    val current by rememberUpdatedState(state)
    val shown = state.overlays.map { o ->
        val d = dragged
        if (d != null && d.first == o.id && d.second in o.points.indices) o.copy(points = o.points.toMutableList().also { it[d.second] = d.third }) else o
    }
    val description = pluralStringResource(R.plurals.wall_overlays_picture_description, state.overlays.size, state.overlays.size)
    val halo = MaterialTheme.colorScheme.surface
    val pending = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("${prefix}_wall_picture")
            .semantics { contentDescription = description },
    ) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val radius = with(density) { TouchRadius.toPx() }
        fun toView(p: OverlayPoint): OverlayPoint = WallSpaceMapper.wallToPhoto(outline, WallPoint(p.x, p.y)).let { OverlayPoint(it.x * w, it.y * h) }
        fun toWall(x: Float, y: Float): OverlayPoint? {
            val at = PhotoPoint(x / w, y / h)
            if (!WallSpaceMapper.isOnWall(outline, at)) return null
            return WallSpaceMapper.photoToWall(outline, at)?.let { OverlayPoint(it.x, it.y) }
        }
        Box(
            Modifier.fillMaxSize().pointerInput(w, h, outline) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val s = current
                    val selected = s.selected
                    val handle = if (s.drawing == null && selected != null) {
                        OverlayHitTest.pointAt(selected.points.map(::toView), down.position.x, down.position.y, radius)
                    } else null
                    if (selected != null && handle != null) {
                        down.consume()
                        drag(down.id) { change ->
                            change.consume()
                            toWall(change.position.x.coerceIn(0f, w), change.position.y.coerceIn(0f, h))?.let { dragged = Triple(selected.id, handle, it) }
                        }
                        dragged?.let { (id, index, to) -> onEvent(WallOverlaysEvent.MovePoint(id, index, to)) }
                        dragged = null
                    } else {
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        if (s.drawing != null) {
                            toWall(up.position.x, up.position.y)?.let { onEvent(WallOverlaysEvent.AddPoint(it)) }
                        } else {
                            val hit = OverlayHitTest.overlayAt(s.overlays.map { o -> o.points.map(::toView) }, up.position.x, up.position.y, radius)
                            onEvent(WallOverlaysEvent.Select(hit?.let { s.overlays[it].id }))
                        }
                    }
                }
            },
        ) {
            if (painter != null) Image(painter, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                if (outline != null) drawWallEdge(outline, halo)
                shown.forEach { o ->
                    if (o.points.size >= 2) drawRun(o.points.map(::toView), o.kind.color(dark), halo, selected = o.id == state.selectedId)
                }
                state.drawing?.let { points ->
                    val view = points.map(::toView)
                    if (view.size >= 2) drawRun(view, pending, halo, selected = false)
                    view.forEach { drawHandle(it, pending, halo) }
                }
                state.selected?.let { sel -> shown.firstOrNull { it.id == sel.id }?.points?.map(::toView)?.forEach { drawHandle(it, sel.kind.color(dark), halo) } }
            }
            // Spots show their kind's icon on a disc of the kind's color (rule 26); runs are lines in that color.
            shown.filter { it.points.size == 1 }.forEach { o ->
                val at = toView(o.points[0])
                val half = with(density) { (SpotSize / 2).toPx() }
                Box(
                    Modifier
                        .offset { IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt()) }
                        .size(SpotSize)
                        .clip(CircleShape)
                        .background(if (o.id == state.selectedId) halo else o.kind.color(dark))
                        .testTag("${prefix}_wall_spot_${o.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(o.kind.icon, contentDescription = null, tint = if (o.id == state.selectedId) o.kind.color(dark) else halo, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun DrawScope.drawWallEdge(outline: WallOutline, halo: Color) {
    val path = Path().apply {
        outline.corners.forEachIndexed { i, c -> if (i == 0) moveTo(c.x * size.width, c.y * size.height) else lineTo(c.x * size.width, c.y * size.height) }
        close()
    }
    drawPath(path, halo.copy(alpha = 0.6f), style = Stroke(width = 2.dp.toPx()))
}

private fun DrawScope.drawRun(points: List<OverlayPoint>, color: Color, halo: Color, selected: Boolean) {
    val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
    val width = (if (selected) 6.dp else 4.dp).toPx()
    drawPath(path, halo.copy(alpha = 0.8f), style = Stroke(width = width + 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawHandle(at: OverlayPoint, color: Color, ring: Color) {
    drawCircle(ring, 12.dp.toPx(), Offset(at.x, at.y))
    drawCircle(color, 9.dp.toPx(), Offset(at.x, at.y))
}

/**
 * While drawing: what to do, Undo the last point, Cancel and Next to the form (Next once there is a point). [screen] names
 * the test tags ("wall" or "plan").
 */
@Composable
internal fun DrawingBar(points: Int, onEvent: (WallOverlaysEvent) -> Unit, screen: String = "wall", @androidx.annotation.StringRes emptyHint: Int = R.string.wall_overlays_draw_hint) {
    val prefix = LocalTagPrefix.current
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (points == 0) stringResource(emptyHint) else pluralStringResource(R.plurals.wall_overlays_draw_points, points, points),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("${prefix}_${screen}_draw_hint"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onEvent(WallOverlaysEvent.CancelDrawing) }, modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_${screen}_draw_cancel")) {
                Text(stringResource(R.string.wall_overlay_cancel), maxLines = 1)
            }
            TextButton(
                onClick = { onEvent(WallOverlaysEvent.UndoPoint) },
                enabled = points > 0,
                modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_${screen}_draw_undo"),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                Text(stringResource(R.string.wall_overlays_undo_point), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onEvent(WallOverlaysEvent.FinishDrawing) },
                enabled = points > 0,
                modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_${screen}_draw_next"),
            ) { Text(stringResource(R.string.wall_overlays_next), maxLines = 1) }
        }
    }
}

/** The picked overlay ([screen] names the test tags): what it is, the item it serves and its photo (AC-C26), "Drag a dot to move it", Edit, Delete and Done. */
@Composable
internal fun SelectionBar(overlay: Overlay, itemName: String?, onEvent: (WallOverlaysEvent) -> Unit, screen: String = "wall") {
    val prefix = LocalTagPrefix.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val kindName = stringResource(overlay.kind.nameRes)
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp).testTag("${prefix}_${screen}_selection")) {
        RecordListItem(
            headline = overlay.title(kindName),
            supporting = listOfNotNull(overlaySummary(overlay, kindName), itemName?.let { stringResource(R.string.wall_overlay_serves, it) }).joinToString("\n"),
            leading = { Icon(overlay.kind.icon, contentDescription = null, tint = overlay.kind.color(dark)) },
            trailing = overlay.photoPath?.let { path ->
                {
                    val title = overlay.title(kindName)
                    OverlayPhoto(path, null, stringResource(R.string.wall_overlay_photo_description, title), Modifier.size(56.dp).testTag("${prefix}_${screen}_overlay_photo"))
                }
            },
        )
        Text(
            stringResource(R.string.wall_overlays_drag_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onEvent(WallOverlaysEvent.EditSelected) }, modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_${screen}_overlay_edit")) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Text(stringResource(R.string.wall_overlay_edit), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
            }
            TextButton(onClick = { onEvent(WallOverlaysEvent.AskDelete) }, modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_${screen}_overlay_delete")) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Text(stringResource(R.string.wall_overlay_delete), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onEvent(WallOverlaysEvent.Select(null)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_${screen}_overlay_done")) {
                Text(stringResource(R.string.wall_overlay_done), maxLines = 1)
            }
        }
    }
}

/** The wall's overlays as rows (rule 7): kind icon in its color, label, kind, depth and stage; a tap picks it on the wall. */
@Composable
private fun OverlayList(overlays: List<Overlay>, onEvent: (WallOverlaysEvent) -> Unit, modifier: Modifier) {
    val prefix = LocalTagPrefix.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    LazyColumn(modifier.fillMaxWidth().testTag("${prefix}_wall_overlay_list"), contentPadding = PaddingValues(bottom = 88.dp)) {
        item(key = "header") {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.room_section_overlays), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
            }
        }
        if (overlays.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.wall_overlays_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("${prefix}_wall_overlays_empty"),
                )
            }
        }
        items(overlays, key = { it.id }) { o ->
            val kindName = stringResource(o.kind.nameRes)
            RecordListItem(
                headline = o.title(kindName),
                supporting = overlaySummary(o, kindName),
                leading = { Icon(o.kind.icon, contentDescription = null, tint = o.kind.color(dark)) },
                onClick = { onEvent(WallOverlaysEvent.Select(o.id)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.animateItem().testTag("${prefix}_wall_overlay_${o.id}"),
            )
        }
    }
}

/** "Hot water · Behind drywall · Rough-in", the kind left out when it is already the headline. */
@Composable
internal fun overlaySummary(o: Overlay, kindName: String): String = listOfNotNull(
    kindName.takeIf { o.label.isNotBlank() },
    stringResource(o.depth.nameRes),
    stringResource(R.string.wall_overlay_recorded_at, stringResource(o.stage.labelRes)),
).joinToString(" · ")
