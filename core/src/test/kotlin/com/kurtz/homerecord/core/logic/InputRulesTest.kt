package com.kurtz.homerecord.core.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputRulesTest {
    @Test
    fun amps_blankIsValidButParsesToNull() {
        assertTrue(AmpsRule.isValid(" "))
        assertNull(AmpsRule.parse(""))
    }

    @Test
    fun amps_rangeAndGarbage() {
        assertEquals(20, AmpsRule.parse(" 20 "))
        assertFalse(AmpsRule.isValid("0"))
        assertFalse(AmpsRule.isValid("401"))
        assertFalse(AmpsRule.isValid("abc"))
    }

    @Test
    fun slotCount_bounds() {
        assertNull(SlotCountRule.parse("1"))
        assertEquals(2, SlotCountRule.parse("2"))
        assertEquals(84, SlotCountRule.parse("84"))
        assertNull(SlotCountRule.parse("85"))
        assertNull(SlotCountRule.parse(""))
    }
}
