package com.kurtz.homerecord.housemodel.blueprint

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.overlays.color
import com.kurtz.homerecord.housemodel.overlays.icon

/** Radius of a spot overlay's disc on the plan, in dp; the disc carries the kind's icon (rule 26). */
const val PLAN_SPOT_RADIUS_DP = 14f

/** One painter per overlay kind's icon, for drawing spots on a canvas. */
@Composable
fun rememberOverlayIcons(): Map<OverlayKind, VectorPainter> = OverlayKind.entries.associateWith { rememberVectorPainter(it.icon) }

/** Whether a plan overlay shows with these layers on: its kind's layer is on, or the kind is on no layer (studs, other). */
fun LayerSet.shows(o: Overlay): Boolean = o.kind.layer?.let { it in this } ?: true

/**
 * Overlays drawn on the floor plan (AC-C25): a run is a line in its kind's color over a [halo] edge so it reads on any room
 * fill; a spot is a disc in that color with the kind's icon. [toScreen] maps plan inches to canvas pixels. The [selectedId]
 * overlay is drawn thicker, a run with a handle on each point, ready to drag (AC-C28).
 */
fun DrawScope.drawPlanOverlays(
    overlays: List<Overlay>,
    toScreen: (OverlayPoint) -> Offset,
    density: Float,
    dark: Boolean,
    halo: Color,
    icons: Map<OverlayKind, VectorPainter>,
    selectedId: String? = null,
) {
    overlays.filter { it.points.size >= 2 }.forEach { o ->
        drawRun(o.points.map(toScreen), o.kind.color(dark), halo, density, selected = o.id == selectedId)
    }
    overlays.filter { it.points.size == 1 }.forEach { o ->
        drawSpot(toScreen(o.points[0]), o.kind, dark, halo, density, icons[o.kind], selected = o.id == selectedId)
    }
    // A picked run shows a handle on each point; a picked spot is its own handle, drawn with a wider ring.
    overlays.firstOrNull { it.id == selectedId && it.points.size >= 2 }?.let { o ->
        o.points.map(toScreen).forEach { drawHandle(it, o.kind.color(dark), halo, density) }
    }
}

/** The points of an overlay still being drawn, in [color]. */
fun DrawScope.drawPendingRun(points: List<Offset>, color: Color, halo: Color, density: Float) {
    if (points.size >= 2) drawRun(points, color, halo, density, selected = false)
    points.forEach { drawHandle(it, color, halo, density) }
}

private fun DrawScope.drawRun(points: List<Offset>, color: Color, halo: Color, density: Float, selected: Boolean) {
    val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
    val width = (if (selected) 6f else 4f) * density
    drawPath(path, halo.copy(alpha = 0.85f), style = Stroke(width = width + 3f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawSpot(at: Offset, kind: OverlayKind, dark: Boolean, halo: Color, density: Float, icon: VectorPainter?, selected: Boolean) {
    val r = PLAN_SPOT_RADIUS_DP * density
    val fill = kind.color(dark)
    drawCircle(halo, r + (if (selected) 4f else 2f) * density, at)
    drawCircle(fill, r, at)
    if (icon != null) {
        val s = r * 1.3f
        translate(at.x - s / 2f, at.y - s / 2f) {
            with(icon) { draw(Size(s, s), colorFilter = ColorFilter.tint(halo)) }
        }
    }
}

private fun DrawScope.drawHandle(at: Offset, color: Color, ring: Color, density: Float) {
    drawCircle(ring, 12f * density, at)
    drawCircle(color, 9f * density, at)
}

/** Radius of an item's marker on the plan, in dp; the disc carries the item's category icon (rule 26). */
const val PLAN_ITEM_RADIUS_DP = 12f

/** How far inside its wall a wall overlay is drawn on the plan, in dp, so it reads beside the wall stroke. */
const val PLAN_WALL_INSET_DP = 7f

/** One painter per item icon name, for drawing item marks on a canvas. */
@Composable
fun rememberItemIcons(): Map<String, VectorPainter> =
    com.kurtz.homerecord.housemodel.ui.ItemIconNames.associateWith { rememberVectorPainter(com.kurtz.homerecord.housemodel.ui.itemIcon(it)) }

/**
 * Wall overlays and items on the blueprint (AC-C15). A wall overlay is drawn [PLAN_WALL_INSET_DP] inside its wall in its
 * kind's color, a run as a line and a spot as a disc with the kind's icon, like a plan overlay; an item is a disc in the
 * Items layer color with its category icon. [toScreen] maps plan inches to canvas pixels. Pass [items] false to draw the
 * wall overlays only and true for the items only, so items can sit above the plan overlays.
 */
fun DrawScope.drawPlanMarks(
    marks: List<PlanMark>,
    toScreen: (OverlayPoint) -> Offset,
    density: Float,
    dark: Boolean,
    halo: Color,
    overlayIcons: Map<OverlayKind, VectorPainter>,
    itemIcons: Map<String, VectorPainter>,
    items: Boolean,
) {
    val inset = PLAN_WALL_INSET_DP * density
    val placed = { m: PlanMark -> m.points.map(toScreen).map { p -> m.inward?.let { Offset(p.x + it.x * inset, p.y + it.y * inset) } ?: p } }
    if (!items) {
        marks.filter { !it.isItem && it.isRun }.forEach { m ->
            val kind = m.overlayKind ?: return@forEach
            drawRun(placed(m), kind.color(dark), halo, density, selected = false)
        }
        marks.filter { !it.isItem && !it.isRun }.forEach { m ->
            val kind = m.overlayKind ?: return@forEach
            drawSpot(placed(m)[0], kind, dark, halo, density, overlayIcons[kind], selected = false)
        }
        return
    }
    val fill = LayerPalette.color(BlueprintLayer.ITEMS, dark)
    val r = PLAN_ITEM_RADIUS_DP * density
    // Faint reference items first, so the item being placed always draws on top of them.
    marks.filter { it.isItem }.sortedBy { !it.faint }.forEach { m ->
        val at = placed(m)[0]
        val alpha = if (m.faint) FAINT_ITEM_ALPHA else 1f
        drawCircle(halo, r + 2f * density, at, alpha = alpha)
        drawCircle(fill, r, at, alpha = alpha)
        val icon = itemIcons[m.iconName] ?: itemIcons["Category"]
        if (icon != null) {
            val s = r * 1.3f
            translate(at.x - s / 2f, at.y - s / 2f) {
                with(icon) { draw(Size(s, s), alpha = alpha, colorFilter = ColorFilter.tint(halo)) }
            }
        }
    }
}

/** How strongly a reference item (a [PlanMark.faint] one) is drawn. */
const val FAINT_ITEM_ALPHA = 0.38f
