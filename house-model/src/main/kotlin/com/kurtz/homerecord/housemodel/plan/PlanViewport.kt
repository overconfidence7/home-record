package com.kurtz.homerecord.housemodel.plan

import kotlin.math.max
import kotlin.math.min

/** How the plan sits on the canvas: [zoom] screen dp per inch, [panX]/[panY] the dp offset of the plan origin. */
data class PlanView(val zoom: Float, val panX: Float, val panY: Float)

/**
 * Where the plan editor's camera goes: fitted and centred on open, on a floor change and on rotation, and moved just
 * enough to show the whole selected room when it would sit under the panel or off an edge.
 */
object PlanViewport {
    const val MIN_ZOOM = 0.15f
    const val MAX_ZOOM = 6f

    /** Space kept between the plan and the canvas edge, in dp. */
    const val MARGIN_DP = 24f

    /** All [rooms] centred in a [widthDp] × [heightDp] canvas at the largest scale that fits; null when nothing to fit. */
    fun fit(rooms: List<PlanRoom>, widthDp: Float, heightDp: Float): PlanView? {
        if (rooms.isEmpty() || widthDp <= 0f || heightDp <= 0f) return null
        return fitBox(rooms.minOf { it.minX }, rooms.minOf { it.minY }, rooms.maxOf { it.maxX }, rooms.maxOf { it.maxY }, widthDp, heightDp)
    }

    /**
     * The view moved (and zoomed out only when the room is larger than the canvas) so all of [room] shows with the
     * margin around it, or null when it already does.
     */
    fun reveal(view: PlanView, room: PlanRoom, widthDp: Float, heightDp: Float): PlanView? {
        if (widthDp <= 0f || heightDp <= 0f) return null
        val left = room.minX * view.zoom + view.panX
        val top = room.minY * view.zoom + view.panY
        val right = room.maxX * view.zoom + view.panX
        val bottom = room.maxY * view.zoom + view.panY
        val margin = min(MARGIN_DP, min(widthDp, heightDp) / 8f)
        if (left >= margin && top >= margin && right <= widthDp - margin && bottom <= heightDp - margin) return null
        val roomW = right - left
        val roomH = bottom - top
        if (roomW > widthDp - 2 * margin || roomH > heightDp - 2 * margin) {
            return fitBox(room.minX, room.minY, room.maxX, room.maxY, widthDp, heightDp)
        }
        return view.copy(
            panX = view.panX + shift(left, right, margin, widthDp - margin),
            panY = view.panY + shift(top, bottom, margin, heightDp - margin),
        )
    }

    /** True when the canvas turned between portrait and landscape shapes, which calls for a fresh fit. */
    fun turned(oldW: Float, oldH: Float, newW: Float, newH: Float): Boolean =
        oldW > 0f && oldH > 0f && newW > 0f && newH > 0f && (oldW > oldH) != (newW > newH)

    private fun shift(start: Float, end: Float, min: Float, max: Float): Float = when {
        start < min -> min - start
        end > max -> max - end
        else -> 0f
    }

    private fun fitBox(minX: Int, minY: Int, maxX: Int, maxY: Int, widthDp: Float, heightDp: Float): PlanView {
        val w = max(maxX - minX, 1)
        val h = max(maxY - minY, 1)
        val margin = min(MARGIN_DP, min(widthDp, heightDp) / 8f)
        val zoom = min((widthDp - 2 * margin) / w, (heightDp - 2 * margin) / h).coerceIn(MIN_ZOOM, MAX_ZOOM)
        return PlanView(zoom, (widthDp - w * zoom) / 2f - minX * zoom, (heightDp - h * zoom) / 2f - minY * zoom)
    }
}
