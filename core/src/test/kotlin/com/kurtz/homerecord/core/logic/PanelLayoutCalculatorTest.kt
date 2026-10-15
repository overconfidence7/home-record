package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.PanelLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelLayoutCalculatorTest {
    @Test fun `AC-7 NA layout puts odd slots left and even right top to bottom`() {
        assertEquals(SlotPosition(0, 0), PanelLayoutCalculator.position(1, PanelLayout.NA_NUMBERED))
        assertEquals(SlotPosition(0, 1), PanelLayoutCalculator.position(2, PanelLayout.NA_NUMBERED))
        assertEquals(SlotPosition(1, 0), PanelLayoutCalculator.position(3, PanelLayout.NA_NUMBERED))
        assertEquals(SlotPosition(19, 1), PanelLayoutCalculator.position(40, PanelLayout.NA_NUMBERED))
    }

    @Test fun `AC-7 DIN layout is one column`() {
        assertEquals(SlotPosition(0, 0), PanelLayoutCalculator.position(1, PanelLayout.DIN))
        assertEquals(SlotPosition(6, 0), PanelLayoutCalculator.position(7, PanelLayout.DIN))
    }

    @Test fun `rows group slots per layout`() {
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), PanelLayoutCalculator.rows(5, PanelLayout.NA_NUMBERED))
        assertEquals(listOf(listOf(1), listOf(2), listOf(3)), PanelLayoutCalculator.rows(3, PanelLayout.DIN))
        assertEquals(20, PanelLayoutCalculator.rowCount(40, PanelLayout.NA_NUMBERED))
        assertEquals(emptyList<List<Int>>(), PanelLayoutCalculator.rows(0, PanelLayout.DIN))
    }

    @Test fun `slot count rule accepts range and rejects junk`() {
        assertEquals(40, SlotCountRule.parse(" 40 "))
        assertEquals(2, SlotCountRule.parse("2"))
        assertEquals(84, SlotCountRule.parse("84"))
        assertNull(SlotCountRule.parse("1"))
        assertNull(SlotCountRule.parse("85"))
        assertNull(SlotCountRule.parse("abc"))
        assertNull(SlotCountRule.parse(""))
    }
}
