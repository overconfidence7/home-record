package com.kurtz.homerecord.fieldlog.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kurtz.homerecord.core.photo.Annotation
import com.kurtz.homerecord.core.photo.AnnotationTool
import com.kurtz.homerecord.core.photo.NormPoint
import com.kurtz.homerecord.coreui.components.FormScaffold
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.fieldlog.R
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private fun toolIcon(t: AnnotationTool): ImageVector = when (t) {
    AnnotationTool.Arrow -> Icons.Rounded.NorthEast
    AnnotationTool.Circle -> Icons.Rounded.RadioButtonUnchecked
    AnnotationTool.Rectangle -> Icons.Rounded.CropSquare
    AnnotationTool.Freehand -> Icons.Rounded.Gesture
    AnnotationTool.Text -> Icons.Rounded.TextFields
    AnnotationTool.Measure -> Icons.Rounded.Straighten
}

private fun toolLabel(t: AnnotationTool): Int = when (t) {
    AnnotationTool.Arrow -> R.string.annotate_arrow
    AnnotationTool.Circle -> R.string.annotate_circle
    AnnotationTool.Rectangle -> R.string.annotate_rectangle
    AnnotationTool.Freehand -> R.string.annotate_freehand
    AnnotationTool.Text -> R.string.annotate_text
    AnnotationTool.Measure -> R.string.annotate_measure
}

/**
 * FL-Annotate: mark up a photo, undo, and save the result as a copy (AC-F26). A full-screen editor (rules 3 and 15):
 * Close and Save in the top bar, and Close or system back asks before throwing drawn marks away.
 */
@Composable
fun AnnotateScreen(state: AnnotateUiState, onEvent: (AnnotateEvent) -> Unit, onClose: () -> Unit) {
    FormScaffold(
        title = stringResource(R.string.annotate_title),
        dirty = state.marks.isNotEmpty() && !state.saved,
        saveEnabled = state.canSave,
        onClose = onClose,
        onSave = { onEvent(AnnotateEvent.Save) },
    ) {
        when {
            state.loading -> LoadingState()
            state.missing -> Text(
                stringResource(R.string.photo_missing), Modifier.padding(vertical = 16.dp).testTag("fl_annotate_missing"),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton({ onEvent(AnnotateEvent.Undo) }, Modifier.testTag("fl_annotate_undo"), enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.annotate_undo))
                    }
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnnotationTool.entries.forEach { t ->
                            FilterChip(
                                t == state.tool, { onEvent(AnnotateEvent.Tool(t)) }, { Text(stringResource(toolLabel(t))) },
                                Modifier.heightIn(min = 48.dp).testTag("fl_annotate_tool_${t.name.lowercase()}"),
                                leadingIcon = { Icon(toolIcon(t), null) },
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PhotoCanvas(state, onEvent)
                }
                when {
                    state.saving -> Text(stringResource(R.string.annotate_saving), style = MaterialTheme.typography.bodyMedium)
                    state.failed -> Text(
                        stringResource(R.string.annotate_failed), Modifier.testTag("fl_annotate_failed"),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                    )
                    state.marks.isEmpty() -> Text(stringResource(R.string.annotate_hint), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    state.pending?.let { p ->
        val measure = p is PendingMark.Measure
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(AnnotateEvent.CancelPending) },
            title = { Text(stringResource(if (measure) R.string.annotate_measure_title else R.string.annotate_text_title)) },
            text = {
                OutlinedTextField(
                    state.pendingText, { onEvent(AnnotateEvent.PendingText(it)) }, Modifier.fillMaxWidth().testTag("fl_annotate_pending_text"),
                    label = { Text(stringResource(if (measure) R.string.annotate_measure_label else R.string.annotate_text_label)) }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    { onEvent(AnnotateEvent.ConfirmPending) }, Modifier.heightIn(min = 48.dp).testTag("fl_annotate_pending_ok"), enabled = state.pendingText.isNotBlank(),
                ) { Text(stringResource(R.string.annotate_add)) }
            },
            dismissButton = { TextButton({ onEvent(AnnotateEvent.CancelPending) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PhotoCanvas(state: AnnotateUiState, onEvent: (AnnotateEvent) -> Unit) {
    val markColor = MaterialTheme.colorScheme.error
    val textStyle = MaterialTheme.typography.titleMedium.copy(color = markColor)
    val measurer = rememberTextMeasurer()
    var live by remember { mutableStateOf<List<NormPoint>>(emptyList()) }
    val liveMark = remember(live, state.tool, state.aspect) { liveMark(state.tool, live, state.aspect) }
    Box(Modifier.fillMaxWidth().aspectRatio(state.aspect).testTag("fl_annotate_canvas")) {
        AsyncImage(File(state.imagePath), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Canvas(
            Modifier.fillMaxSize().pointerInput(state.tool) {
                fun norm(o: Offset) = NormPoint((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
                if (state.tool == AnnotationTool.Text) {
                    detectTapGestures { onEvent(AnnotateEvent.Gesture(listOf(norm(it)))) }
                } else {
                    detectDragGestures(
                        onDragStart = { live = listOf(norm(it)) },
                        onDrag = { change, _ -> live = live + norm(change.position) },
                        onDragEnd = { onEvent(AnnotateEvent.Gesture(live)); live = emptyList() },
                        onDragCancel = { live = emptyList() },
                    )
                }
            },
        ) {
            drawMarks(state.marks, markColor, measurer, textStyle)
            liveMark?.let { drawMarks(listOf(it), markColor, measurer, textStyle) }
        }
    }
}

private fun liveMark(tool: AnnotationTool, points: List<NormPoint>, aspect: Float): Annotation? {
    val r = com.kurtz.homerecord.core.photo.MarkSketch.finish(tool, points, aspect)
    return when (r) {
        is com.kurtz.homerecord.core.photo.SketchResult.Mark -> r.mark
        is com.kurtz.homerecord.core.photo.SketchResult.AskMeasure -> Annotation.Measure(r.from, r.to, " ")
        else -> null
    }
}

/** Draws the same shapes as [BitmapAnnotationRenderer], scaled to the canvas. */
fun DrawScope.drawMarks(marks: List<Annotation>, color: Color, measurer: TextMeasurer, textStyle: TextStyle) {
    val w = size.width
    val h = size.height
    val stroke = Stroke((minOf(w, h) * 0.008f).coerceAtLeast(3f), cap = StrokeCap.Round)
    fun o(p: NormPoint) = Offset(p.x * w, p.y * h)
    marks.forEach { m ->
        when (m) {
            is Annotation.Arrow -> {
                drawLine(color, o(m.from), o(m.to), stroke.width, StrokeCap.Round)
                val angle = atan2(o(m.to).y - o(m.from).y, o(m.to).x - o(m.from).x)
                for (side in listOf(-1, 1)) {
                    val a = angle + Math.PI.toFloat() + side * 0.5f
                    val head = stroke.width * 5f
                    drawLine(color, o(m.to), Offset(o(m.to).x + head * cos(a), o(m.to).y + head * sin(a)), stroke.width, StrokeCap.Round)
                }
            }
            is Annotation.Circle -> drawCircle(color, m.radius * minOf(w, h), o(m.center), style = stroke)
            is Annotation.Rectangle -> drawRect(color, o(m.topLeft).let { Offset(minOf(it.x, o(m.bottomRight).x), minOf(it.y, o(m.bottomRight).y)) },
                androidx.compose.ui.geometry.Size(kotlin.math.abs(o(m.bottomRight).x - o(m.topLeft).x), kotlin.math.abs(o(m.bottomRight).y - o(m.topLeft).y)), style = stroke)
            is Annotation.Freehand -> drawPath(Path().apply { m.points.forEachIndexed { i, p -> if (i == 0) moveTo(o(p).x, o(p).y) else lineTo(o(p).x, o(p).y) } }, color, style = stroke)
            is Annotation.Text -> drawText(measurer, m.text, o(m.at), textStyle)
            is Annotation.Measure -> {
                drawLine(color, o(m.from), o(m.to), stroke.width, StrokeCap.Round)
                val label = measurer.measure(m.label, textStyle)
                drawText(label, topLeft = Offset((o(m.from).x + o(m.to).x) / 2f - label.size.width / 2f, (o(m.from).y + o(m.to).y) / 2f - label.size.height - stroke.width * 2))
            }
        }
    }
}
