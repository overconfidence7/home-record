package com.kurtz.homerecord.core.billing

import org.junit.Assert.assertEquals
import org.junit.Test

class FreeLimitsTest {
    private fun check(action: LimitedAction, pro: Boolean = false, panels: Int = 1, equipment: Int = 0, labeled: Int = 1) =
        FreeLimits.check(action, pro, panels, equipment, labeled)

    @Test fun `tenth equipment item is allowed and eleventh shows paywall`() {
        assertEquals(LimitResult.ALLOWED, check(LimitedAction.ADD_EQUIPMENT, equipment = 9))
        assertEquals(LimitResult.SHOW_PAYWALL, check(LimitedAction.ADD_EQUIPMENT, equipment = 10))
    }

    @Test fun `second panel shows paywall but first does not`() {
        assertEquals(LimitResult.ALLOWED, check(LimitedAction.ADD_PANEL, panels = 0))
        assertEquals(LimitResult.SHOW_PAYWALL, check(LimitedAction.ADD_PANEL, panels = 1))
    }

    @Test fun `paywall never shows before a labeled breaker exists`() {
        assertEquals(LimitResult.ALLOWED, check(LimitedAction.ADD_EQUIPMENT, equipment = 25, labeled = 0))
        assertEquals(LimitResult.ALLOWED, check(LimitedAction.ADD_PANEL, panels = 3, labeled = 0))
    }

    @Test fun `pro entitlement lifts every limit`() {
        assertEquals(LimitResult.ALLOWED, check(LimitedAction.ADD_EQUIPMENT, pro = true, equipment = 99))
        assertEquals(LimitResult.ALLOWED, check(LimitedAction.ADD_PANEL, pro = true, panels = 5))
    }
}
