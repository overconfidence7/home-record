package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelGridPlannerTest {
    private fun breaker(slots: List<Int>, poles: Int) = Breaker(
        id = "b${slots.first()}", panelId = "p", slots = slots, poles = poles, label = "x",
        createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL,
    )

    @Test fun twoPoleSpansTwoRowsInItsColumn() {
        val cols = PanelGridPlanner.columns(8, PanelLayout.NA_NUMBERED, listOf(breaker(listOf(2, 4), 2)))
        assertEquals(listOf(2 to 2, 6 to 1, 8 to 1), cols[1].map { it.slot to it.rowSpan })
        assertEquals(listOf(1, 3, 5, 7), cols[0].map { it.slot })
    }

    @Test fun staggeredTwoPolesKeepRowsAligned() {
        val cols = PanelGridPlanner.columns(8, PanelLayout.NA_NUMBERED, listOf(breaker(listOf(2, 4), 2), breaker(listOf(3, 5), 2)))
        assertEquals(listOf(0, 2, 3), cols[1].map { it.row })
        assertEquals(listOf(0 to 1, 1 to 2, 3 to 1), cols[0].map { it.row to it.rowSpan })
    }

    @Test fun dinNeverSpans() {
        val cols = PanelGridPlanner.columns(4, PanelLayout.DIN, listOf(breaker(listOf(1, 3), 2)))
        assertEquals(listOf(1, 1, 1, 1), cols.single().map { it.rowSpan })
    }
}
