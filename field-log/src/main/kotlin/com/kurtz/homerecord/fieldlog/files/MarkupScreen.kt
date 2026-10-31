package com.kurtz.homerecord.fieldlog.files

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NorthEast
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.photo.NormPoint
import com.kurtz.homerecord.core.photo.PdfMark
import com.kurtz.homerecord.core.photo.PdfMarkColors
import com.kurtz.homerecord.core.photo.PdfMarkSketch
import com.kurtz.homerecord.core.photo.PdfSketchResult
import com.kurtz.homerecord.core.photo.PdfTool
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.FormScaffold
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.pages.PageSource
import com.kurtz.homerecord.fieldlog.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val PAGE_WIDTH_PX = 1080

private fun toolIcon(t: PdfTool): ImageVector = when (t) {
    PdfTool.Pen -> Icons.Rounded.Draw
    PdfTool.Arrow -> Icons.Rounded.NorthEast
    PdfTool.Rectangle -> Icons.Rounded.CropSquare
    PdfTool.Text -> Icons.Rounded.TextFields
    PdfTool.Highlight -> Icons.Rounded.BorderColor
}

private fun toolLabel(t: PdfTool): Int = when (t) {
    PdfTool.Pen -> R.string.markup_pen
    PdfTool.Arrow -> R.string.markup_arrow
    PdfTool.Rectangle -> R.string.markup_box
    PdfTool.Text -> R.string.markup_text
    PdfTool.Highlight -> R.string.markup_highlight
}

/**
 * FL-Markup: mark up a PDF page by page and save the result as a new version; the original is kept (AC-F21). A
 * full-screen editor (rules 3 and 15): Close and Save in the top bar, Close or back asks before throwing marks away.
 * The tools wrap onto a second row rather than scrolling off the edge, so all five show at 200% text.
 */
@Composable
fun MarkupScreen(state: MarkupUiState, onEvent: (MarkupEvent) -> Unit) {
    val problem = state.problem
    FormScaffold(
        title = stringResource(R.string.markup_title),
        dirty = state.marks.isNotEmpty() && state.savedId == null,
        saveEnabled = state.canSave,
        onClose = { onEvent(MarkupEvent.Close) },
        onSave = { onEvent(MarkupEvent.Save) },
        placeholder = when {
            state.loading -> { m -> LoadingState(m) }
            problem != null -> { m -> Problem(problem, m) { onEvent(MarkupEvent.Close) } }
            else -> null
        },
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton({ onEvent(MarkupEvent.Undo) }, Modifier.testTag("fl_markup_undo"), enabled = state.canUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.markup_undo))
            }
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfTool.entries.forEach { t ->
                    FilterChip(
                        t == state.tool, { onEvent(MarkupEvent.Tool(t)) }, { Text(stringResource(toolLabel(t))) },
                        Modifier.heightIn(min = 48.dp).testTag("fl_markup_tool_${t.name.lowercase()}"),
                        leadingIcon = { Icon(toolIcon(t), null) },
                    )
                }
            }
        }
        state.pages?.let { PageCanvas(it, state, onEvent) }
        if (state.pageCount > 1) PageSwitcher(state, onEvent)
        when {
            state.saving -> Text(stringResource(R.string.markup_saving), style = MaterialTheme.typography.bodyMedium)
            state.failed -> Text(
                stringResource(R.string.markup_failed), Modifier.testTag("fl_markup_failed"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            )
            state.marks.isEmpty() -> Text(stringResource(R.string.markup_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (state.pendingText != null) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(MarkupEvent.CancelText) },
            title = { Text(stringResource(R.string.markup_text_title)) },
            text = {
                OutlinedTextField(
                    state.pendingValue, { onEvent(MarkupEvent.PendingValue(it)) }, Modifier.fillMaxWidth().testTag("fl_markup_text_value"),
                    label = { Text(stringResource(R.string.markup_text_label)) }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    { onEvent(MarkupEvent.ConfirmText) }, Modifier.heightIn(min = 48.dp).testTag("fl_markup_text_add"), enabled = state.pendingValue.isNotBlank(),
                ) { Text(stringResource(R.string.markup_add)) }
            },
            dismissButton = { TextButton({ onEvent(MarkupEvent.CancelText) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Problem(problem: MarkupProblem, modifier: Modifier, onBack: () -> Unit) {
    val (headline, body) = when (problem) {
        MarkupProblem.Missing -> R.string.file_viewer_missing_title to R.string.file_viewer_missing_body
        MarkupProblem.NotAvailable -> R.string.markup_not_available_title to R.string.markup_not_available_body
        MarkupProblem.Unreadable -> R.string.file_viewer_unreadable_title to R.string.file_viewer_unreadable_body
    }
    EmptyState(
        Icons.Rounded.ErrorOutline, stringResource(headline), stringResource(body), stringResource(R.string.markup_back_to_file), onBack,
        modifier = modifier.testTag("fl_markup_problem"), actionModifier = Modifier.testTag("fl_markup_back"),
    )
}

@Composable
private fun PageSwitcher(state: MarkupUiState, onEvent: (MarkupEvent) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconButton({ onEvent(MarkupEvent.PreviousPage) }, Modifier.testTag("fl_markup_previous"), enabled = state.page > 0) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.markup_previous_page))
        }
        Text(
            stringResource(R.string.markup_page_of, state.page + 1, state.pageCount),
            Modifier.testTag("fl_markup_page"), style = MaterialTheme.typography.bodyLarge,
        )
        IconButton({ onEvent(MarkupEvent.NextPage) }, Modifier.testTag("fl_markup_next"), enabled = state.page < state.pageCount - 1) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.markup_next_page))
        }
    }
}

@Composable
private fun PageCanvas(pages: PageSource, state: MarkupUiState, onEvent: (MarkupEvent) -> Unit) {
    val ink = Color(PdfMarkColors.INK)
    val highlight = Color(PdfMarkColors.HIGHLIGHT)
    val textStyle = MaterialTheme.typography.titleMedium.copy(color = ink)
    val measurer = rememberTextMeasurer()
    val bitmap by produceState(pages.peek(state.page), pages, state.page) { value = pages.render(state.page, PAGE_WIDTH_PX) }
    val shown = bitmap
    val aspect = shown?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: (8.5f / 11f)
    var live by remember { mutableStateOf<List<NormPoint>>(emptyList()) }
    val liveMark = remember(live, state.tool, state.page) {
        (PdfMarkSketch.finish(state.tool, state.page, live) as? PdfSketchResult.Mark)?.mark
    }
    val label = stringResource(R.string.markup_page_of, state.page + 1, state.pageCount)
    Box(
        Modifier.fillMaxWidth().aspectRatio(aspect)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .testTag("fl_markup_canvas")
            .semantics { contentDescription = label },
    ) {
        if (shown != null) Image(shown, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Canvas(
            Modifier.fillMaxSize().pointerInput(state.tool, state.page) {
                fun norm(o: Offset) = NormPoint((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
                if (state.tool == PdfTool.Text) {
                    detectTapGestures { onEvent(MarkupEvent.Gesture(listOf(norm(it)))) }
                } else {
                    detectDragGestures(
                        onDragStart = { live = listOf(norm(it)) },
                        onDrag = { change, _ -> live = live + norm(change.position) },
                        onDragEnd = { onEvent(MarkupEvent.Gesture(live)); live = emptyList() },
                        onDragCancel = { live = emptyList() },
                    )
                }
            },
        ) {
            drawPdfMarks(state.pageMarks, ink, highlight, measurer, textStyle)
            liveMark?.let { drawPdfMarks(listOf(it), ink, highlight, measurer, textStyle) }
        }
    }
}

/** Draws the same shapes as [AndroidMarkedPdfWriter.draw], scaled to the canvas. */
private fun DrawScope.drawPdfMarks(marks: List<PdfMark>, ink: Color, highlight: Color, measurer: TextMeasurer, textStyle: TextStyle) {
    val w = size.width
    val h = size.height
    val stroke = (minOf(w, h) * 0.006f).coerceAtLeast(3f)
    fun o(p: NormPoint) = Offset(p.x * w, p.y * h)
    marks.forEach { m ->
        when (m) {
            is PdfMark.Pen -> drawPath(
                Path().apply { m.points.forEachIndexed { i, p -> if (i == 0) moveTo(o(p).x, o(p).y) else lineTo(o(p).x, o(p).y) } },
                ink, style = Stroke(stroke, cap = StrokeCap.Round),
            )
            is PdfMark.Arrow -> {
                drawLine(ink, o(m.from), o(m.to), stroke, StrokeCap.Round)
                val angle = atan2(o(m.to).y - o(m.from).y, o(m.to).x - o(m.from).x)
                for (side in listOf(-1, 1)) {
                    val a = angle + Math.PI.toFloat() + side * 0.5f
                    val head = stroke * 5f
                    drawLine(ink, o(m.to), Offset(o(m.to).x + head * cos(a), o(m.to).y + head * sin(a)), stroke, StrokeCap.Round)
                }
            }
            is PdfMark.Rect -> drawRect(ink, o(m.topLeft), Size(o(m.bottomRight).x - o(m.topLeft).x, o(m.bottomRight).y - o(m.topLeft).y), style = Stroke(stroke))
            is PdfMark.Highlight -> drawRect(highlight, o(m.topLeft), Size(o(m.bottomRight).x - o(m.topLeft).x, o(m.bottomRight).y - o(m.topLeft).y))
            is PdfMark.Text -> {
                val laid = measurer.measure(m.text, textStyle)
                // The writer draws text on its baseline at the tap; lift the layout so the screen matches.
                drawText(laid, topLeft = Offset(o(m.at).x, o(m.at).y - laid.firstBaseline))
            }
        }
    }
}
