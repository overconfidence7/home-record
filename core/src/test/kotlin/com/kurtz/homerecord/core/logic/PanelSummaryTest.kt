package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelSummaryTest {
    private fun breaker(slots: List<Int>, label: String) =
        Breaker(id = "b${slots.first()}", panelId = "p", slots = slots, poles = slots.size, label = label, createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL)

    @Test fun `no slots or no breakers is zero`() {
        assertEquals(0, PanelSummary.percentLabeled(0, emptyList()))
        assertEquals(0, PanelSummary.percentLabeled(20, emptyList()))
    }

    @Test fun `counts slots of labeled breakers only, two-pole covers two slots`() {
        val list = listOf(breaker(listOf(1), "Kitchen"), breaker(listOf(2, 4), "Dryer"), breaker(listOf(3), "  "))
        assertEquals(15, PanelSummary.percentLabeled(20, list))
    }

    @Test fun `clamped to 100`() {
        assertEquals(100, PanelSummary.percentLabeled(1, listOf(breaker(listOf(1, 2), "x"))))
    }
}
