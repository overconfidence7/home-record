package com.kurtz.homerecord.coreui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.Signature
import com.kurtz.homerecord.core.model.SignatureStroke
import com.kurtz.homerecord.coreui.R

/**
 * A signature pad (AC-F38): one finger draws strokes, each finished stroke goes to [onStroke] in pad-relative 0..1
 * coordinates (the ViewModel keeps them, so they survive rotation). The stroke being drawn lives only here. A sign
 * line with an X sits near the bottom; a hint shows on a blank pad.
 */
@Composable
fun SignaturePad(
    strokes: List<SignatureStroke>,
    onStroke: (SignatureStroke) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val current = remember { mutableStateListOf<Float>() }
    val emit by rememberUpdatedState(onStroke)
    val ink = MaterialTheme.colorScheme.onSurface
    val guide = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(12.dp)
    val described = stringResource(R.string.signature_pad_description)
    val state = stringResource(if (strokes.isEmpty()) R.string.signature_pad_blank else R.string.signature_pad_signed)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .semantics { contentDescription = described; stateDescription = state }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun add(p: Offset) {
                        current += Signature.clamp(p.x / size.width)
                        current += Signature.clamp(p.y / size.height)
                    }
                    current.clear()
                    add(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        add(change.position)
                        change.consume()
                    }
                    // A tap leaves a dot: two points at the same place.
                    if (current.size == 2) current += listOf(current[0], current[1])
                    emit(SignatureStroke(current.toList()))
                    current.clear()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val baseline = size.height * 0.78f
            drawLine(guide, Offset(24.dp.toPx(), baseline), Offset(size.width - 24.dp.toPx(), baseline), strokeWidth = 1.dp.toPx())
            val x = 24.dp.toPx()
            val s = 8.dp.toPx()
            val top = baseline - 2 * s - 6.dp.toPx()
            drawLine(guide, Offset(x, top), Offset(x + 2 * s, top + 2 * s), strokeWidth = 2.dp.toPx())
            drawLine(guide, Offset(x + 2 * s, top), Offset(x, top + 2 * s), strokeWidth = 2.dp.toPx())
            strokes.forEach { drawStroke(it.points, ink) }
            if (current.isNotEmpty()) drawStroke(current, ink)
        }
        if (strokes.isEmpty() && current.isEmpty()) {
            Text(
                stringResource(R.string.signature_pad_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
        }
    }
}

private fun DrawScope.drawStroke(points: List<Float>, ink: Color) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(points[0] * size.width, points[1] * size.height)
    var i = 2
    while (i + 1 < points.size) {
        path.lineTo(points[i] * size.width, points[i + 1] * size.height)
        i += 2
    }
    if (points.size == 2) path.lineTo(points[0] * size.width + 0.5f, points[1] * size.height)
    drawPath(path, ink, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
