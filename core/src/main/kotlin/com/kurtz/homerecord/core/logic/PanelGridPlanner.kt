package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.PanelLayout

/** One cell in a grid column: starts at [slot] and covers [rowSpan] grid rows (2 for a two-pole breaker). */
data class GridCell(val slot: Int, val row: Int, val rowSpan: Int, val breaker: Breaker? = null)

/**
 * AC-9: on a two-column panel a two-pole breaker (slots n and n+2, same column) is drawn as ONE cell
 * spanning both rows. Single-column (DIN) panels have no vertically adjacent n+2, so nothing spans.
 */
object PanelGridPlanner {
    /** Cells for each column, top to bottom; continuation slots covered by a span are omitted. */
    fun columns(slotCount: Int, layout: PanelLayout, breakers: List<Breaker>): List<List<GridCell>> {
        val cols = PanelLayoutCalculator.columns(layout)
        val canSpan = cols == 2
        return (0 until cols).map { column ->
            val cells = mutableListOf<GridCell>()
            var row = 0
            while (true) {
                val slot = row * cols + column + 1
                if (slot > slotCount) break
                val breaker = breakers.firstOrNull { slot in it.slots }
                val spans = canSpan && breaker != null && breaker.poles == 2 && breaker.slots.min() == slot &&
                    slot + 2 <= slotCount && (slot + 2) in breaker.slots
                if (spans) {
                    cells += GridCell(slot, row, 2, breaker)
                    row += 2
                } else {
                    cells += GridCell(slot, row, 1, breaker)
                    row += 1
                }
            }
            cells
        }
    }
}
