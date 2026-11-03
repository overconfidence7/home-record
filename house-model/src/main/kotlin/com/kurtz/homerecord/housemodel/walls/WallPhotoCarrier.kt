package com.kurtz.homerecord.housemodel.walls

import com.kurtz.homerecord.housemodel.plan.Pt

/** A wall photo as the carrier sees it: its row, the wall it is on, its stage and outline. */
data class CarriedPhoto(val rowId: String, val wallId: String, val stage: WallStage, val outline: WallOutline?)

/** What happens to a wall photo when its room is reshaped (R10a). */
sealed interface PhotoMove {
    /** The photo stays on its wall, outlined by [outline] now (the part of the old wall this wall still covers). */
    data class Recrop(val rowId: String, val outline: WallOutline) : PhotoMove

    /** A copy of the photo goes onto wall [toWallId], outlined by [outline] (the part of the photo that wall shows). */
    data class Copy(val rowId: String, val toWallId: String, val outline: WallOutline) : PhotoMove
}

/**
 * Keeps wall photos whole through a reshape of their room in the plan editor (R10a, AC-C17, AC-C19, AC-C27), the same
 * way [com.kurtz.homerecord.housemodel.overlays.WallOverlayCarrier] carries the overlays drawn on them:
 *
 * - A wall whose corners moved, one wall to one wall, keeps its photo and outline (the photo stretches corner to corner).
 * - A wall split by a new corner: each part shows its own stretch of the photo. The outline is cut at the new corner
 *   through the photo's perspective (wall space to photo), so the part that kept the wall's id keeps the photo with the
 *   cut outline and the new part gets a copy of the photo with the rest of it.
 * - Walls merged when the corner between them is removed: the merged wall keeps its own photo at each stage (else the
 *   photo of the old wall that covered most of it), with the outline stretched along the wall to cover the whole merged
 *   wall when the photo saw that far; when it did not, the outline is kept as it was (decision to confirm).
 *
 * [oldOutline] is the room's previous outline lined up with [newPoints] (WallMatcher.alignedOutline); [oldEdgeOfWall]
 * gives each stored wall its old edge, [wallOfEdge] each new edge its wall (kept or new id); [photos] are the newest
 * photo per wall and stage.
 */
object WallPhotoCarrier {
    /** The stretch of old edge [oldEdge] that a new edge runs along: [start]..[end] in the old wall's 0..1 space. */
    data class Span(val oldEdge: Int, val start: Float, val end: Float, val overlap: Long)

    fun carry(
        oldOutline: List<Pt>,
        oldEdgeOfWall: Map<String, Int>,
        wallOfEdge: Map<Int, String>,
        newPoints: List<Pt>,
        photos: List<CarriedPhoto>,
    ): List<PhotoMove> {
        if (oldOutline.size < 2 || newPoints.size < 2 || photos.isEmpty()) return emptyList()
        val spans = newPoints.indices.associateWith { e -> spansOf(oldOutline, newPoints, e) }
        val piecesOfOld = spans.entries.flatMap { (e, list) -> list.map { it.oldEdge to e } }.groupBy({ it.first }, { it.second })
        val wallOfOldEdge = oldEdgeOfWall.entries.associate { (id, edge) -> edge to id }
        val photosOfWall = photos.groupBy { it.wallId }
        val moves = mutableListOf<PhotoMove>()
        for ((e, wallId) in wallOfEdge) {
            val sources = spans[e].orEmpty()
            if (sources.isEmpty()) continue
            val matchedEdge = oldEdgeOfWall[wallId]
            if (matchedEdge != null && sources.all { it.oldEdge == matchedEdge } && piecesOfOld[matchedEdge].orEmpty().all { it == e }) continue
            val own = photosOfWall[wallId].orEmpty().associateBy { it.stage }
            val stages = (sources.flatMap { s -> photosOfWall[wallOfOldEdge[s.oldEdge]].orEmpty().map { it.stage } } + own.keys).distinct()
            for (stage in stages) {
                val ownPhoto = own[stage]
                val span: Span
                val photo: CarriedPhoto
                if (ownPhoto != null) {
                    // The wall keeps its own picture at this stage; it is recut only when it still runs along its old line.
                    span = sources.firstOrNull { it.oldEdge == matchedEdge } ?: continue
                    photo = ownPhoto
                } else {
                    val best = sources
                        .mapNotNull { s -> photosOfWall[wallOfOldEdge[s.oldEdge]].orEmpty().firstOrNull { it.stage == stage }?.let { s to it } }
                        .maxByOrNull { (s, _) -> s.overlap } ?: continue
                    span = best.first
                    photo = best.second
                }
                val outline = photo.outline ?: FULL
                val cut = crop(outline, span.start, span.end) ?: outline
                if (photo.wallId == wallId) {
                    if (photo.outline == null || cut != photo.outline) moves += PhotoMove.Recrop(photo.rowId, cut)
                } else {
                    moves += PhotoMove.Copy(photo.rowId, wallId, cut)
                }
            }
        }
        return moves
    }

    /** The old edges new edge [e] runs along (same line, same way, sharing some length), with where it starts and ends on each. */
    fun spansOf(oldOutline: List<Pt>, newPoints: List<Pt>, e: Int): List<Span> {
        val c = newPoints[e]
        val d = newPoints[(e + 1) % newPoints.size]
        return oldOutline.indices.mapNotNull { j ->
            val a = oldOutline[j]
            val b = oldOutline[(j + 1) % oldOutline.size]
            val ux = (b.x - a.x).toLong()
            val uy = (b.y - a.y).toLong()
            val len2 = ux * ux + uy * uy
            if (len2 == 0L) return@mapNotNull null
            if (!onLine(a, b, c) || !onLine(a, b, d)) return@mapNotNull null
            if (ux * (d.x - c.x) + uy * (d.y - c.y) <= 0L) return@mapNotNull null
            fun along(p: Pt) = (p.x - a.x) * ux + (p.y - a.y) * uy
            val lo = maxOf(0L, along(c))
            val hi = minOf(len2, along(d))
            if (hi <= lo) return@mapNotNull null
            Span(j, (along(c).toDouble() / len2).toFloat(), (along(d).toDouble() / len2).toFloat(), hi - lo)
        }
    }

    /**
     * The outline of the stretch [start]..[end] (0..1 along the wall, may run past either end for a merged wall) of the
     * wall outlined by [outline], through the photo's perspective; null when that stretch runs off the photo.
     */
    fun crop(outline: WallOutline, start: Float, end: Float): WallOutline? {
        val corners = listOf(WallPoint(start, 0f), WallPoint(end, 0f), WallPoint(end, 1f), WallPoint(start, 1f))
            .map { WallSpaceMapper.wallToPhoto(outline, it) }
        if (corners.any { it.x.isNaN() || it.y.isNaN() || it.x !in -SLACK..1f + SLACK || it.y !in -SLACK..1f + SLACK }) return null
        return WallOutline.of(corners)
    }

    private fun onLine(a: Pt, b: Pt, p: Pt): Boolean =
        (b.x - a.x).toLong() * (p.y - a.y) - (b.y - a.y).toLong() * (p.x - a.x) == 0L

    /** A photo with no outline is the wall itself, corner to corner. */
    private val FULL = WallOutline(PhotoPoint(0f, 0f), PhotoPoint(1f, 0f), PhotoPoint(1f, 1f), PhotoPoint(0f, 1f))

    private const val SLACK = 1e-3f
}
