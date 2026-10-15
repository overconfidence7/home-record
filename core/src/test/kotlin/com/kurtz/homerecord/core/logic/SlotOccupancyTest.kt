package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotOccupancyTest {
    private fun breaker(id: String, vararg slots: Int, poles: Int = 1, tandem: Int? = null) =
        Breaker(id, "p", slots.toList(), poles, label = id, tandemIndex = tandem, createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL)

    @Test fun `two-pole covers N and N+2 on NA and N and N+1 on DIN`() {
        assertEquals(listOf(3, 5), SlotOccupancy.slotsFor(3, 2, PanelLayout.NA_NUMBERED))
        assertEquals(listOf(3, 4), SlotOccupancy.slotsFor(3, 2, PanelLayout.DIN))
        assertEquals(listOf(3), SlotOccupancy.slotsFor(3, 1, PanelLayout.NA_NUMBERED))
    }

    @Test fun `AC-9 two-pole is blocked when N+2 is occupied`() {
        val result = SlotOccupancy.place(1, 2, PanelLayout.NA_NUMBERED, 40, listOf(breaker("a", 3)))
        assertEquals(Placement.Occupied(3), result)
    }

    @Test fun `AC-9 two-pole into free slots is ok`() {
        val result = SlotOccupancy.place(1, 2, PanelLayout.NA_NUMBERED, 40, listOf(breaker("a", 2)))
        assertEquals(Placement.Ok(listOf(1, 3)), result)
    }

    @Test fun `two-pole past the last slot is out of range`() {
        assertEquals(Placement.OutOfRange, SlotOccupancy.place(39, 2, PanelLayout.NA_NUMBERED, 40, emptyList()))
    }

    @Test fun `editing a breaker ignores its own slots`() {
        val own = breaker("a", 1, 3, poles = 2)
        assertEquals(Placement.Ok(listOf(1, 3)), SlotOccupancy.place(1, 2, PanelLayout.NA_NUMBERED, 40, listOf(own), "a"))
    }

    @Test fun `a tandem pair keeps its slot occupied`() {
        val pair = listOf(breaker("a", 5, tandem = 0), breaker("b", 5, tandem = 1))
        assertEquals(Placement.Occupied(5), SlotOccupancy.place(5, 1, PanelLayout.NA_NUMBERED, 40, pair))
    }

    @Test fun `AC-11 shrink below highest occupied slot is blocked and names it`() {
        val list = listOf(breaker("a", 2), breaker("b", 11, 13, poles = 2))
        assertEquals(ShrinkCheck.Blocked(13), SlotOccupancy.checkShrink(12, list))
        assertEquals(ShrinkCheck.Ok, SlotOccupancy.checkShrink(13, list))
        assertEquals(ShrinkCheck.Ok, SlotOccupancy.checkShrink(2, emptyList()))
    }

    @Test fun `tandem candidate is a lone one-pole non-tandem breaker`() {
        assertEquals("a", SlotOccupancy.tandemCandidate(1, listOf(breaker("a", 1)))?.id)
        assertNull(SlotOccupancy.tandemCandidate(2, listOf(breaker("a", 1))))
        assertNull(SlotOccupancy.tandemCandidate(1, listOf(breaker("a", 1, 3, poles = 2))))
        assertNull(SlotOccupancy.tandemCandidate(1, listOf(breaker("a", 1, tandem = 0), breaker("b", 1, tandem = 1))))
    }

    @Test fun `amps rule accepts blank and 1 to 400 only`() {
        assertTrue(AmpsRule.isValid(""))
        assertTrue(AmpsRule.isValid(" 20 "))
        assertEquals(false, AmpsRule.isValid("0"))
        assertEquals(false, AmpsRule.isValid("abc"))
        assertEquals(false, AmpsRule.isValid("401"))
        assertNull(AmpsRule.parse(""))
        assertEquals(15, AmpsRule.parse("15"))
    }
}
