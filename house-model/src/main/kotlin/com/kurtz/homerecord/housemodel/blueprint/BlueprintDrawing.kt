package com.kurtz.homerecord.housemodel.blueprint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.kurtz.homerecord.core.units.LengthParser
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanOpening
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.PlanStair
import com.kurtz.homerecord.housemodel.plan.WallDeriver

private const val STAIR_TREAD_IN = 10

/** How far above its tip a task pin's head sits, in dp. */
const val PIN_LIFT_DP = 20f

data class PinColors(val fill: Color, val onFill: Color, val outline: Color)

/** A map pin: a round numbered head above a point at the task's spot, outlined so it reads on any room fill (AC-F35). */
fun DrawScope.drawPin(pin: BlueprintPin, view: BlueprintView, density: Float, measurer: TextMeasurer, style: TextStyle, colors: PinColors) {
    val tip = Offset(view.x(pin.xIn), view.y(pin.yIn))
    val head = Offset(tip.x, tip.y - PIN_LIFT_DP * density)
    val r = 12f * density
    val body = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(head.x - r * 0.7f, head.y + r * 0.7f)
        lineTo(head.x + r * 0.7f, head.y + r * 0.7f)
        close()
    }
    drawPath(body, colors.outline, style = Stroke(width = 4f * density))
    drawCircle(colors.outline, r + 2f * density, head)
    drawPath(body, colors.fill)
    drawCircle(colors.fill, r, head)
    val text = measurer.measure(pin.number.toString(), style.copy(color = colors.onFill))
    drawText(text, topLeft = Offset(head.x - text.size.width / 2f, head.y - text.size.height / 2f))
}

/** A door is a gap in the wall with its swing arc; a window is three thin lines across the wall. */
fun DrawScope.drawOpening(room: PlanRoom, o: PlanOpening, z: Float, ox: Float, oy: Float, density: Float, line: Color, gap: Color) {
    val a = room.points[o.edgeIndex]
    val b = room.points[(room.points.size + o.edgeIndex + 1) % room.points.size]
    val len = room.edgeLength(o.edgeIndex).toFloat()
    if (len == 0f) return
    val ux = (b.x - a.x) / len
    val uy = (b.y - a.y) / len
    val start = Offset((a.x + ux * o.offsetIn) * z + ox, (a.y + uy * o.offsetIn) * z + oy)
    val end = Offset((a.x + ux * (o.offsetIn + o.widthIn)) * z + ox, (a.y + uy * (o.offsetIn + o.widthIn)) * z + oy)
    // Inward normal: the interior lies to the right of travel when the signed area is positive (y down).
    val sign = if (room.points.indices.sumOf { i -> val p = room.points[i]; val q = room.points[(i + 1) % room.points.size]; p.x.toLong() * q.y - q.x.toLong() * p.y } >= 0) 1f else -1f
    val nx = -uy * sign
    val ny = ux * sign
    val width = o.widthIn * z
    when (o.kind) {
        OpeningKind.DOOR -> {
            drawLine(gap, start, end, strokeWidth = 5f * density)
            drawLine(line, start, Offset(start.x + nx * width, start.y + ny * width), strokeWidth = 1.5f * density)
            val path = Path().apply {
                moveTo(end.x, end.y)
                quadraticTo(start.x + nx * width + (end.x - start.x) * 0.55f, start.y + ny * width + (end.y - start.y) * 0.55f, start.x + nx * width, start.y + ny * width)
            }
            drawPath(path, line, style = Stroke(width = 1.25f * density))
        }
        OpeningKind.WINDOW -> {
            drawLine(gap, start, end, strokeWidth = 5f * density)
            for (k in -1..1) {
                val off = k * 2f * density
                drawLine(line, Offset(start.x + nx * off, start.y + ny * off), Offset(end.x + nx * off, end.y + ny * off), strokeWidth = 1.25f * density)
            }
        }
    }
}

/** Stairs as a rectangle crossed by treads along its long side. */
fun DrawScope.drawStair(st: PlanStair, z: Float, ox: Float, oy: Float, density: Float, line: Color) {
    if (st.points.size < 3) return
    val minX = st.points.minOf { it.x }
    val maxX = st.points.maxOf { it.x }
    val minY = st.points.minOf { it.y }
    val maxY = st.points.maxOf { it.y }
    val topLeft = Offset(minX * z + ox, minY * z + oy)
    val size = Size((maxX - minX) * z, (maxY - minY) * z)
    drawRect(line, topLeft, size, style = Stroke(width = 1.25f * density))
    val vertical = (maxY - minY) >= (maxX - minX)
    val treads = ((if (vertical) maxY - minY else maxX - minX) / STAIR_TREAD_IN).coerceAtLeast(2)
    for (k in 1 until treads) {
        val f = k / treads.toFloat()
        if (vertical) drawLine(line, Offset(topLeft.x, topLeft.y + size.height * f), Offset(topLeft.x + size.width, topLeft.y + size.height * f), strokeWidth = 1f * density)
        else drawLine(line, Offset(topLeft.x + size.width * f, topLeft.y), Offset(topLeft.x + size.width * f, topLeft.y + size.height), strokeWidth = 1f * density)
    }
}

class BlueprintColors(
    val fill: Color,
    val wall: Color,
    val text: Color,
    val dimension: Color,
    val dark: Boolean,
)

/** The clean plan (AC-C13): filled rooms, wall outlines, names, outer dimensions, openings, stairs, north arrow. Layers draw on top. */
fun DrawScope.drawBlueprint(
    rooms: List<PlanRoom>,
    view: BlueprintView,
    density: Float,
    measurer: TextMeasurer,
    nameStyle: TextStyle,
    dimStyle: TextStyle,
    colors: BlueprintColors,
    system: UnitSystem,
    /** What the layers draw over the plan (marks and plan overlays), in canvas pixels; room names keep clear of it. */
    obstacles: List<LabelBox> = emptyList(),
) {
    val z = view.scale
    val ox = view.originX
    val oy = view.originY
    rooms.forEach { room ->
        val path = Path().apply {
            room.points.forEachIndexed { i, p -> if (i == 0) moveTo(view.x(p.x), view.y(p.y)) else lineTo(view.x(p.x), view.y(p.y)) }
            close()
        }
        drawPath(path, colors.fill)
        drawPath(path, colors.wall, style = Stroke(width = 2.5f * density))
    }
    rooms.forEach { room ->
        room.stairs.forEach { drawStair(it, z, ox, oy, density, colors.wall) }
        room.openings.forEach { drawOpening(room, it, z, ox, oy, density, colors.wall, colors.fill) }
    }
    rooms.forEach { room ->
        val layouts = roomNameLayouts(
            measurer, room.name.ifBlank { "·" }, nameStyle.copy(color = colors.text),
            maxWidth = room.widthIn * z * 0.9f, maxHeight = room.lengthIn * z * 0.8f,
        )
        val (clx, cly) = labelAnchor(room)
        val roomBox = LabelBox(room.minX * z + ox, room.minY * z + oy, room.maxX * z + ox, room.maxY * z + oy)
        // Keep the name off any stairs, and off the overlays and items the layers show where there is room for it.
        val stairs = room.stairs.filter { it.points.isNotEmpty() }.map { st ->
            LabelBox(st.points.minOf { it.x } * z + ox, st.points.minOf { it.y } * z + oy, st.points.maxOf { it.x } * z + ox, st.points.maxOf { it.y } * z + oy)
        }
        // The first layout (one line, two lines, smaller) that finds a spot clear of the stairs is drawn.
        for (layout in layouts) {
            val half = layout.size.width / 2f
            val at = LabelPlacer.place(clx * z + ox, cly * z + oy, layout.size.width.toFloat(), layout.size.height.toFloat(), roomBox, stairs, obstacles, 4f * density)
                ?: continue
            val (cx, cy) = at
            if (cx - half < room.minX * z + ox) continue
            drawText(layout, topLeft = Offset(cx - half, cy - layout.size.height / 2f))
            break
        }
    }
    drawDimensions(rooms, view, density, measurer, dimStyle, colors, system)
    drawNorthArrow(measurer, dimStyle, colors, density)
}

/** The steps a room name shrinks through before it is ellipsized, as a share of the theme's size. */
private val NAME_SCALES = floatArrayOf(1f, 0.85f, 0.7f)

/**
 * The ways a room name can be laid out to fit its room, best first ([judge:hm-blueprint-names-font200], ui-standards
 * rule 32): at the theme's size on one line, then on two lines ("Living / room" at 200% text), then a step or two
 * smaller, and as a last resort ellipsized. Each layout is only as wide as its longest line, so a wrapped name can sit
 * beside stairs. The caller places the first one that finds a free spot, so every room keeps a readable name.
 */
fun roomNameLayouts(measurer: TextMeasurer, name: String, style: TextStyle, maxWidth: Float, maxHeight: Float): List<TextLayoutResult> {
    val width = maxWidth.toInt().coerceAtLeast(1)
    val out = ArrayList<TextLayoutResult>()
    for (scale in NAME_SCALES) {
        val centred = style.copy(textAlign = TextAlign.Center)
        val sized = if (style.fontSize.isSp) centred.copy(fontSize = style.fontSize * scale) else centred
        val widestWord = name.split(' ').filter { it.isNotEmpty() }.maxOfOrNull {
            measurer.measure(it, sized, maxLines = 1, softWrap = false).size.width
        } ?: 0
        if (widestWord > width) continue
        for (lines in 1..2) {
            val wrapped = measurer.measure(name, sized, softWrap = lines > 1, maxLines = lines, constraints = Constraints(maxWidth = width))
            if (wrapped.hasVisualOverflow || wrapped.size.height > maxHeight || wrapped.lineCount != lines) continue
            val content = (0 until wrapped.lineCount).maxOf { wrapped.getLineRight(it) - wrapped.getLineLeft(it) }
            out += measurer.measure(name, sized, softWrap = lines > 1, maxLines = lines, constraints = Constraints(maxWidth = kotlin.math.ceil(content).toInt().coerceIn(1, width)))
        }
    }
    if (out.isEmpty()) {
        val smallest = if (style.fontSize.isSp) style.copy(fontSize = style.fontSize * NAME_SCALES.last()) else style
        out += measurer.measure(name, smallest, overflow = TextOverflow.Ellipsis, maxLines = 1, softWrap = false, constraints = Constraints(maxWidth = width))
    }
    return out
}

/** The best of [roomNameLayouts]. */
fun roomNameLayout(measurer: TextMeasurer, name: String, style: TextStyle, maxWidth: Float, maxHeight: Float): TextLayoutResult =
    roomNameLayouts(measurer, name, style, maxWidth, maxHeight).first()

private fun DrawScope.drawDimensions(
    rooms: List<PlanRoom>,
    view: BlueprintView,
    density: Float,
    measurer: TextMeasurer,
    style: TextStyle,
    colors: BlueprintColors,
    system: UnitSystem,
) {
    val gap = 4f * density
    val walls = WallDeriver().derive(rooms).filter { it.exterior }
    for (w in walls) {
        val room = rooms.firstOrNull { it.id == w.roomId } ?: continue
        val a = room.points[w.edgeIndex]
        val b = room.points[(w.edgeIndex + 1) % room.points.size]
        if (a.x != b.x && a.y != b.y) continue
        val horizontal = a.y == b.y
        val layout: TextLayoutResult = measurer.measure(LengthParser.format(w.lengthIn, system), style.copy(color = colors.dimension))
        val mx = (view.x(a.x) + view.x(b.x)) / 2f
        val my = (view.y(a.y) + view.y(b.y)) / 2f
        // Outside is the side away from the room's center (works for rectangles and L-shapes alike on their outer edges).
        val cx = view.x((room.minX + room.maxX) / 2)
        val cy = view.y((room.minY + room.maxY) / 2)
        val longEnough = w.lengthIn * view.scale > layout.size.width + 8f * density
        if (!longEnough) continue
        if (horizontal) {
            val up = if (my < cy) -1f else 1f
            val y = my + if (up < 0) -(layout.size.height + gap) else gap
            drawText(layout, topLeft = Offset(mx - layout.size.width / 2f, y))
        } else {
            val left = if (mx < cx) -1f else 1f
            val x = mx + if (left < 0) -(layout.size.height + gap) else gap
            rotate(-90f, pivot = Offset(x + layout.size.height / 2f, my)) {
                drawText(layout, topLeft = Offset(x + layout.size.height / 2f - layout.size.width / 2f, my - layout.size.height / 2f))
            }
        }
    }
}

private fun DrawScope.drawNorthArrow(measurer: TextMeasurer, style: TextStyle, colors: BlueprintColors, density: Float) {
    val h = 28f * density
    val cx = size.width - 24f * density
    val top = 12f * density
    val label = measurer.measure("N", style.copy(color = colors.text))
    val arrow = Path().apply {
        moveTo(cx, top + label.size.height)
        lineTo(cx + 7f * density, top + label.size.height + h)
        lineTo(cx, top + label.size.height + h * 0.75f)
        lineTo(cx - 7f * density, top + label.size.height + h)
        close()
    }
    drawPath(arrow, colors.wall)
    drawText(label, topLeft = Offset(cx - label.size.width / 2f, top))
}


/** Where a room's name goes: the area centroid when it lies inside the room, else the middle of the bounding box. */
internal fun labelAnchor(room: PlanRoom): Pair<Float, Float> {
    val area2 = room.signedArea2().toDouble()
    val mid = (room.minX + room.maxX) / 2f to (room.minY + room.maxY) / 2f
    if (area2 == 0.0) return mid
    var cx = 0.0
    var cy = 0.0
    for (i in room.points.indices) {
        val a = room.points[i]
        val b = room.points[(i + 1) % room.points.size]
        val cross = a.x.toLong() * b.y - b.x.toLong() * a.y
        cx += (a.x + b.x) * cross
        cy += (a.y + b.y) * cross
    }
    val c = (cx / (3 * area2)).toFloat() to (cy / (3 * area2)).toFloat()
    return if (RoomHitTester.contains(room, c.first, c.second)) c else mid
}
