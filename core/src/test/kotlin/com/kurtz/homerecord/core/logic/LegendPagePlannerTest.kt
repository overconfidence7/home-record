package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegendPagePlannerTest {
    private fun panel(id: String, name: String, slots: Int, createdAt: Long) =
        Panel(id, "h", name, null, null, slots, PanelLayout.NA_NUMBERED, createdAt, createdAt, Source.HOUSE_MANUAL)

    private fun breaker(id: String, panelId: String, slot: Int) =
        Breaker(id, panelId, listOf(slot), label = id, createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL)

    private fun plan(panels: List<Panel>, breakers: List<Breaker> = emptyList()) =
        LegendPagePlanner.plan(panels, breakers, { emptyList() }, { "Standard" }, subtitleFor = { "sub ${it.name}" })

    @Test fun `AC-98 one page per panel oldest first with only that panel's breakers`() {
        val pages = plan(
            listOf(panel("g", "Garage", 2, 20), panel("m", "Main", 3, 10)),
            listOf(breaker("Oven", "m", 1), breaker("Saw", "g", 2)),
        )
        assertEquals(listOf("Main", "Garage"), pages.map { it.panelName })
        assertEquals(listOf("Oven", "—", "—"), pages[0].rows.map { it.label })
        assertEquals(listOf("—", "Saw"), pages[1].rows.map { it.label })
        assertEquals("sub Garage", pages[1].subtitle)
    }

    @Test fun `AC-22 a single panel prints one untitled-by-name page`() {
        val pages = plan(listOf(panel("m", "Main", 2, 1)))
        assertEquals(1, pages.size)
        assertNull(pages[0].panelName)
    }

    @Test fun `AC-97 next panel name is numbered and unique`() {
        assertEquals("Sub-panel 2", PanelNamer.next("Sub-panel", listOf("Panel")))
        assertEquals("Sub-panel 4", PanelNamer.next("Sub-panel", listOf("Panel", "Sub-panel 3", "x")))
    }
}
