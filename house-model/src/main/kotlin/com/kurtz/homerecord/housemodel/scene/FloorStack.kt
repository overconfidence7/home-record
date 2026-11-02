package com.kurtz.homerecord.housemodel.scene

import com.kurtz.homerecord.housemodel.FloorSummary
import kotlin.math.max

/** One floor as the 3D view places it: how high its floor sits and whether it is drawn faded under the chosen one. */
data class StackedFloor(val floorId: String, val ceilingHeightIn: Int, val baseYIn: Int, val ghost: Boolean)

/**
 * How floors stack in the 3D view (AC-C21):
 *
 * - one floor chosen: that floor at its elevation, every floor below it at theirs, faded ("ghosted"), floors above
 *   left out so they do not hide it;
 * - All floors: every floor drawn solid, bottom first, pulled apart by [EXPLODE_GAP_IN] so each level's rooms can be
 *   seen between the ones above and below. A floor never starts below the top of the one under it, even when the
 *   stored elevations overlap.
 */
object FloorStack {
    const val EXPLODE_GAP_IN = 60

    fun layout(floors: List<FloorSummary>, selectedId: String?, allFloors: Boolean): List<StackedFloor> {
        val ordered = floors.withIndex().sortedWith(compareBy({ it.value.elevationIn }, { it.index })).map { it.value }
        if (allFloors) {
            val out = ArrayList<StackedFloor>(ordered.size)
            ordered.forEachIndexed { i, f ->
                val base = if (i == 0) {
                    f.elevationIn
                } else {
                    val below = out.last()
                    max(f.elevationIn + i * EXPLODE_GAP_IN, below.baseYIn + below.ceilingHeightIn + EXPLODE_GAP_IN)
                }
                out += StackedFloor(f.id, f.ceilingHeightIn, base, ghost = false)
            }
            return out
        }
        val chosen = ordered.firstOrNull { it.id == selectedId } ?: return emptyList()
        return ordered
            .filter { it.id == chosen.id || it.elevationIn < chosen.elevationIn }
            .map { StackedFloor(it.id, it.ceilingHeightIn, it.elevationIn, ghost = it.id != chosen.id) }
    }
}
