package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.PanelLayout

/** Grid position of one slot. Rows and columns are zero-based, top to bottom, left to right. */
data class SlotPosition(val row: Int, val column: Int)

/**
 * AC-7: North American panels have odd slots in the left column and even slots in the right,
 * top to bottom. DIN panels are a single column.
 */
object PanelLayoutCalculator {
    fun columns(layout: PanelLayout): Int = when (layout) {
        PanelLayout.NA_NUMBERED -> 2
        PanelLayout.DIN -> 1
    }

    /** Position of 1-based [slot]. */
    fun position(slot: Int, layout: PanelLayout): SlotPosition {
        require(slot >= 1) { "slot must be >= 1" }
        val cols = columns(layout)
        return SlotPosition(row = (slot - 1) / cols, column = (slot - 1) % cols)
    }

    fun rowCount(slotCount: Int, layout: PanelLayout): Int {
        if (slotCount <= 0) return 0
        val cols = columns(layout)
        return (slotCount + cols - 1) / cols
    }

    /** Slot numbers per row; the last NA row may hold one slot when the count is odd. */
    fun rows(slotCount: Int, layout: PanelLayout): List<List<Int>> =
        (0 until rowCount(slotCount, layout)).map { row ->
            val cols = columns(layout)
            (1..cols).map { row * cols + it }.filter { it <= slotCount }
        }
}
