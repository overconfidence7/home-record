package com.kurtz.homerecord.housemodel.blueprint

import com.kurtz.homerecord.housemodel.plan.PlanRoom
import kotlin.math.min

/** Maps plan inches to screen pixels: [scale] pixels per inch and the pixel position of plan origin. */
data class BlueprintView(val scale: Float, val originX: Float, val originY: Float) {
    fun x(planX: Int) = planX * scale + originX
    fun y(planY: Int) = planY * scale + originY
    fun planX(px: Float) = (px - originX) / scale
    fun planY(py: Float) = (py - originY) / scale

    companion object {
        /** Fits every room into the viewport, leaving [marginPx] on each side for dimensions. */
        fun fit(rooms: List<PlanRoom>, widthPx: Float, heightPx: Float, marginPx: Float): BlueprintView {
            if (rooms.isEmpty() || widthPx <= 0f || heightPx <= 0f) return BlueprintView(1f, 0f, 0f)
            val minX = rooms.minOf { it.minX }
            val maxX = rooms.maxOf { it.maxX }
            val minY = rooms.minOf { it.minY }
            val maxY = rooms.maxOf { it.maxY }
            val availW = (widthPx - 2 * marginPx).coerceAtLeast(1f)
            val availH = (heightPx - 2 * marginPx).coerceAtLeast(1f)
            val spanX = (maxX - minX).coerceAtLeast(1)
            val spanY = (maxY - minY).coerceAtLeast(1)
            val scale = min(availW / spanX, availH / spanY)
            val originX = marginPx + (availW - spanX * scale) / 2f - minX * scale
            val originY = marginPx + (availH - spanY * scale) / 2f - minY * scale
            return BlueprintView(scale, originX, originY)
        }
    }
}

/** Which room a tap at plan coordinates lands in; the smallest room wins where rooms overlap. */
object RoomHitTester {
    fun roomAt(rooms: List<PlanRoom>, x: Float, y: Float): PlanRoom? =
        rooms.filter { contains(it, x, y) }.minByOrNull { it.areaSqIn() }

    fun contains(room: PlanRoom, x: Float, y: Float): Boolean {
        var inside = false
        var j = room.points.size - 1
        for (i in room.points.indices) {
            val pi = room.points[i]
            val pj = room.points[j]
            if ((pi.y > y) != (pj.y > y) && x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y).toFloat() + pi.x) inside = !inside
            j = i
        }
        return inside
    }
}

/**
 * A task pin on the plan (AC-F35): [number] is drawn in the head, [label] is what TalkBack reads. The point is the pin's
 * tip; its round head sits above it.
 */
data class BlueprintPin(val id: String, val label: String, val xIn: Int, val yIn: Int, val number: Int)

/** Where a pin's head sits on screen and which pin a tap lands on; the head is the 48 dp target (rule 13). */
object PinHitTester {
    /** Head center in pixels: straight above the tip by [headLiftPx]. */
    fun head(pin: BlueprintPin, view: BlueprintView, headLiftPx: Float): Pair<Float, Float> = view.x(pin.xIn) to view.y(pin.yIn) - headLiftPx

    /** The pin whose head is nearest the tap within [radiusPx]; the pin drawn last (on top) wins a tie. */
    fun pinAt(pins: List<BlueprintPin>, view: BlueprintView, px: Float, py: Float, headLiftPx: Float, radiusPx: Float): BlueprintPin? {
        var best: BlueprintPin? = null
        var bestD = Float.MAX_VALUE
        pins.forEach { pin ->
            val (hx, hy) = head(pin, view, headLiftPx)
            val d = (hx - px) * (hx - px) + (hy - py) * (hy - py)
            if (d <= radiusPx * radiusPx && d <= bestD) { best = pin; bestD = d }
        }
        return best
    }
}
