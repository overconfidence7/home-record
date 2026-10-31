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

/** A layer marker at a plan position; items and overlays supply these as they are added. */
data class BlueprintMarker(val layer: BlueprintLayer, val xIn: Int, val yIn: Int)

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

/** The clean plan (AC-C13): filled rooms, wall outlines, names, outer dimensions, openings, stairs, north arrow, layer markers. */
fun DrawScope.drawBlueprint(
    rooms: List<PlanRoom>,
    view: BlueprintView,
    density: Float,
    measurer: TextMeasurer,
    nameStyle: TextStyle,
    dimStyle: TextStyle,
    colors: BlueprintColors,
    system: UnitSystem,
    layers: LayerSet = LayerSet(),
    markers: List<BlueprintMarker> = emptyList(),
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
        val layout = measurer.measure(room.name.ifBlank { "·" }, nameStyle.copy(color = colors.text))
        val (clx, cly) = labelAnchor(room)
        var cx = clx * z + ox
        val cy = cly * z + oy
        val half = layout.size.width / 2f
        // Keep the name off any stairs: slide it left of them when it would overlap.
        room.stairs.forEach { st ->
            if (st.points.isEmpty()) return@forEach
            val left = st.points.minOf { it.x } * z + ox
            val right = st.points.maxOf { it.x } * z + ox
            val top = st.points.minOf { it.y } * z + oy
            val bottom = st.points.maxOf { it.y } * z + oy
            if (cx + half > left && cx - half < right && cy + layout.size.height / 2f > top && cy - layout.size.height / 2f < bottom) {
                cx = left - 4f * density - half
            }
        }
        val fits = layout.size.width < room.widthIn * z * 0.9f && cx - half >= room.minX * z + ox
        if (fits) drawText(layout, topLeft = Offset(cx - half, cy - layout.size.height / 2f))
    }
    drawDimensions(rooms, view, density, measurer, dimStyle, colors, system)
    markers.filter { it.layer in layers }.forEach { m ->
        drawCircle(LayerPalette.color(m.layer, colors.dark), 6f * density, Offset(view.x(m.xIn), view.y(m.yIn)))
    }
    drawNorthArrow(measurer, dimStyle, colors, density)
}

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
