package com.kurtz.homerecord.housemodel.plan

/**
 * Which way a wall's own space runs (R10b, AC-C20). A wall photo, its outline and the overlays drawn on it use wall
 * space: x from 0 at the left end of the wall to 1 at the right end as you stand in the room facing it. A room's
 * corners can be stored either way round (a corner dragged across flips the order), so the left end is the edge's first
 * corner when the corners run clockwise on the plan (x east, y south) and its second corner when they run
 * counter-clockwise. Everything that turns wall space into plan positions (the 3D wall faces, the photo and overlay
 * carriers) goes through here, so a photo never shows mirrored.
 */
object WallWinding {
    /** True when the room's edges run right to left seen from inside: wall x = 1 at the edge's first corner. */
    fun runsBackward(points: List<Pt>): Boolean = signedArea2(points) < 0

    /** Wall x for a point [along] the edge (0 at its first corner, 1 at its second), and the reverse. */
    fun wallX(along: Float, backward: Boolean): Float = if (backward) 1f - along else along

    /** Fraction along the edge from its first corner for wall x [x]. */
    fun along(x: Float, backward: Boolean): Float = if (backward) 1f - x else x

    /** Twice the signed area in plan coordinates; positive when the corners run clockwise on screen (y down). */
    fun signedArea2(points: List<Pt>): Long {
        if (points.size < 3) return 0L
        var sum = 0L
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x.toLong() * b.y - b.x.toLong() * a.y
        }
        return sum
    }
}
