package com.kurtz.homerecord.housemodel.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.kurtz.homerecord.core.units.LengthParser
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.walls.OutlineGuide
import com.kurtz.homerecord.housemodel.walls.PhotoPoint
import com.kurtz.homerecord.housemodel.walls.WallOutline
import java.io.File
import kotlin.math.roundToInt

private val HandleTouch = 48.dp
private val HandleDot = 22.dp
private val LoupeSize = 112.dp
private const val LOUPE_ZOOM = 3f

/**
 * HM-WallOutline (AC-C18): the photo with four draggable corner handles (starting 10% in), lines between them, the wall's
 * length × height as a dashed guide, a loupe while a handle moves, and Use. [photo] stands in for the file in previews.
 */
@Composable
fun WallOutlineScreen(state: WallOutlineUiState, onEvent: (WallOutlineEvent) -> Unit, photo: Painter? = null) {
    val prefix = LocalTagPrefix.current
    val wall = state.wall
    val name = wall?.let { wallName(it) }
    ScreenScaffold(
        title = stringResource(R.string.wall_outline_title),
        subtitle = name?.let { stringResource(R.string.wall_outline_subtitle, it, stringResource(state.stage.labelRes)) },
        onBack = { onEvent(WallOutlineEvent.Back) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error == WallOutlineError.PhotoUnreadable -> EmptyState(
                icon = Icons.Rounded.BrokenImage,
                headline = stringResource(R.string.wall_outline_unreadable_title),
                body = stringResource(R.string.wall_outline_unreadable_body),
                actionLabel = stringResource(R.string.wall_outline_back),
                onAction = { onEvent(WallOutlineEvent.Back) },
                modifier = Modifier.padding(padding),
                actionModifier = Modifier.testTag("${prefix}_wall_outline_back"),
            )
            else -> Column(
                Modifier.fillMaxSize().padding(padding).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (wall != null) {
                    Text(
                        stringResource(R.string.wall_outline_hint, LengthParser.format(wall.lengthIn, state.system), LengthParser.format(wall.heightIn, state.system)),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                    )
                }
                OutlineEditor(
                    state = state,
                    photo = photo ?: rememberAsyncImagePainter(File(state.photoPath), placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)),
                    description = stringResource(R.string.wall_outline_photo_description, name.orEmpty()),
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                state.error?.let { error ->
                    Text(
                        stringResource(error.messageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("${prefix}_wall_outline_error"),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { onEvent(WallOutlineEvent.Reset) },
                        enabled = !state.saving,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_wall_outline_reset"),
                    ) { Text(stringResource(R.string.wall_outline_reset), maxLines = 1) }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { onEvent(WallOutlineEvent.Use) },
                        enabled = !state.saving && wall != null,
                        modifier = Modifier.heightIn(min = 56.dp).testTag("${prefix}_wall_outline_use"),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.wall_outline_saving), maxLines = 1)
                        } else {
                            Text(stringResource(R.string.wall_outline_use), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** The photo at its own aspect, as large as fits, with the guide, the outline, the handles and the loupe on top. */
@Composable
private fun OutlineEditor(state: WallOutlineUiState, photo: Painter, description: String, onEvent: (WallOutlineEvent) -> Unit, modifier: Modifier) {
    val prefix = LocalTagPrefix.current
    val density = LocalDensity.current
    val photoAspect = if (state.photoWidth > 0 && state.photoHeight > 0) state.photoWidth.toFloat() / state.photoHeight else 4f / 3f
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val fitWidth = if (maxWidth / maxHeight > photoAspect) maxHeight * photoAspect else maxWidth
        val fitHeight = fitWidth / photoAspect
        val widthPx = with(density) { fitWidth.toPx() }
        val heightPx = with(density) { fitHeight.toPx() }
        val outline by rememberUpdatedState(state.outline)
        val dragging = remember { intArrayOf(-1) }
        val radiusPx = with(density) { HandleTouch.toPx() }
        val guide = state.wall?.let { OutlineGuide.guide(state.photoWidth, state.photoHeight, it.lengthIn, it.heightIn) }
        val primary = MaterialTheme.colorScheme.primary
        val onPrimary = MaterialTheme.colorScheme.onPrimary
        val surface = MaterialTheme.colorScheme.surface
        Box(
            Modifier
                .size(fitWidth, fitHeight)
                .clip(MaterialTheme.shapes.small)
                .testTag("${prefix}_wall_outline_photo")
                .pointerInput(widthPx, heightPx, state.saving) {
                    if (state.saving) return@pointerInput
                    detectDragGestures(
                        onDragStart = { at ->
                            val corner = OutlineGuide.cornerAt(outline, at.x, at.y, widthPx, heightPx, radiusPx)
                            dragging[0] = corner ?: -1
                            if (corner != null) onEvent(WallOutlineEvent.DragStart(corner))
                        },
                        onDrag = { change, amount ->
                            val corner = dragging[0]
                            if (corner >= 0) {
                                change.consume()
                                val from = outline.corners[corner]
                                onEvent(WallOutlineEvent.Drag(corner, PhotoPoint(from.x + amount.x / widthPx, from.y + amount.y / heightPx)))
                            }
                        },
                        onDragEnd = {
                            if (dragging[0] >= 0) onEvent(WallOutlineEvent.DragEnd)
                            dragging[0] = -1
                        },
                        onDragCancel = {
                            if (dragging[0] >= 0) onEvent(WallOutlineEvent.DragEnd)
                            dragging[0] = -1
                        },
                    )
                },
        ) {
            Image(photo, contentDescription = description, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                guide?.let { drawGuide(it.left * size.width, it.top * size.height, it.right * size.width, it.bottom * size.height) }
                drawOutline(state.outline, primary, surface)
            }
            state.outline.corners.forEachIndexed { i, c ->
                CornerHandle(
                    corner = i,
                    at = c,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    active = state.dragging == i,
                    color = primary,
                    ring = onPrimary,
                    onMove = { to -> onEvent(WallOutlineEvent.Drag(i, to)); onEvent(WallOutlineEvent.DragEnd) },
                )
            }
            state.dragging?.let { corner ->
                val point = state.outline.corners[corner]
                Loupe(photo, point, widthPx, heightPx, primary, surface, Modifier.align(if (OutlineGuide.loupeOnRight(point)) Alignment.TopEnd else Alignment.TopStart))
            }
        }
    }
}

/** The wall's shape as a dashed rectangle: white dashes over a dark line so it shows on light and dark walls alike. */
private fun DrawScope.drawGuide(left: Float, top: Float, right: Float, bottom: Float) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
    val topLeft = Offset(left, top)
    val size = Size(right - left, bottom - top)
    drawRect(Color.Black.copy(alpha = 0.45f), topLeft, size, style = Stroke(width = 3.dp.toPx()))
    drawRect(Color.White, topLeft, size, style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash))
}

/** The four edges between the handles, with a halo so the line reads over a busy photo. */
private fun DrawScope.drawOutline(outline: WallOutline, color: Color, halo: Color) {
    val path = Path().apply {
        outline.corners.forEachIndexed { i, c ->
            val x = c.x * size.width
            val y = c.y * size.height
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path, halo.copy(alpha = 0.7f), style = Stroke(width = 4.dp.toPx()))
    drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
}

/** A 48 dp target around each corner with a visible dot; TalkBack moves it with the four custom actions (rule 15). */
@Composable
private fun CornerHandle(
    corner: Int,
    at: PhotoPoint,
    widthPx: Float,
    heightPx: Float,
    active: Boolean,
    color: Color,
    ring: Color,
    onMove: (PhotoPoint) -> Unit,
) {
    val prefix = LocalTagPrefix.current
    val density = LocalDensity.current
    val half = with(density) { (HandleTouch / 2).toPx() }
    val label = stringResource(cornerLabel(corner))
    val left = stringResource(R.string.wall_outline_move_left)
    val right = stringResource(R.string.wall_outline_move_right)
    val up = stringResource(R.string.wall_outline_move_up)
    val down = stringResource(R.string.wall_outline_move_down)
    val step = 0.01f
    Box(
        Modifier
            .offset { IntOffset((at.x * widthPx - half).roundToInt(), (at.y * heightPx - half).roundToInt()) }
            .size(HandleTouch)
            .testTag("${prefix}_wall_outline_handle_$corner")
            .semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction(left) { onMove(PhotoPoint(at.x - step, at.y)); true },
                    CustomAccessibilityAction(right) { onMove(PhotoPoint(at.x + step, at.y)); true },
                    CustomAccessibilityAction(up) { onMove(PhotoPoint(at.x, at.y - step)); true },
                    CustomAccessibilityAction(down) { onMove(PhotoPoint(at.x, at.y + step)); true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val dot = if (active) HandleDot + 6.dp else HandleDot
        Box(Modifier.size(dot + 4.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
            Box(Modifier.size(dot).clip(CircleShape).background(color))
        }
    }
}

/** A round close-up of the photo around the corner being dragged, with a crosshair on the corner itself. */
@Composable
private fun Loupe(photo: Painter, point: PhotoPoint, widthPx: Float, heightPx: Float, color: Color, backdrop: Color, modifier: Modifier) {
    val prefix = LocalTagPrefix.current
    val description = stringResource(R.string.wall_outline_loupe_description)
    Canvas(
        modifier
            .padding(8.dp)
            .size(LoupeSize)
            .testTag("${prefix}_wall_outline_loupe")
            .semantics { contentDescription = description },
    ) {
        val r = size.minDimension / 2f
        val center = Offset(r, r)
        val focus = Offset(point.x * widthPx, point.y * heightPx)
        clipPath(Path().apply { addOval(Rect(center, r)) }) {
            drawRect(backdrop)
            withTransform({
                translate(center.x - focus.x * LOUPE_ZOOM, center.y - focus.y * LOUPE_ZOOM)
                scale(LOUPE_ZOOM, LOUPE_ZOOM, pivot = Offset.Zero)
            }) {
                with(photo) { draw(Size(widthPx, heightPx)) }
            }
        }
        val arm = 10.dp.toPx()
        drawLine(color, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), strokeWidth = 2.dp.toPx())
        drawLine(color, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), strokeWidth = 2.dp.toPx())
        drawCircle(backdrop, r, center, style = Stroke(width = 4.dp.toPx()))
        drawCircle(color, r, center, style = Stroke(width = 2.dp.toPx()))
    }
}

@StringRes
private fun cornerLabel(corner: Int): Int = when (corner) {
    0 -> R.string.wall_outline_corner_top_left
    1 -> R.string.wall_outline_corner_top_right
    2 -> R.string.wall_outline_corner_bottom_right
    else -> R.string.wall_outline_corner_bottom_left
}

@get:StringRes
private val WallOutlineError.messageRes: Int
    get() = when (this) {
        WallOutlineError.PhotoUnreadable -> R.string.wall_outline_unreadable_body
        WallOutlineError.NotUsable -> R.string.wall_outline_not_usable
        WallOutlineError.TextureFailed -> R.string.wall_outline_texture_failed
        WallOutlineError.WallGone -> R.string.wall_outline_wall_gone
    }
