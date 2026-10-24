package com.kurtz.homerecord.core.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitGateTest {
    @Test fun freeTierAllowsFifteenItemsNotSixteen() { // AC-H18
        assertTrue(LimitGate.canAddItem(pro = false, items = 14))
        assertFalse(LimitGate.canAddItem(pro = false, items = 15))
    }

    @Test fun freeTierAllowsOneHomeAndOnePanel() { // AC-H18
        assertTrue(LimitGate.canAddHome(false, 0))
        assertFalse(LimitGate.canAddHome(false, 1))
        assertTrue(LimitGate.canAddPanel(false, 0))
        assertFalse(LimitGate.canAddPanel(false, 1))
    }

    @Test fun freeTierHasNoCloudSync() { // AC-H18
        assertFalse(LimitGate.canSync(false))
        assertTrue(LimitGate.blockedBy(false, LimitedFeature.CloudSync))
    }

    @Test fun proHasNoLimits() { // AC-H18
        assertTrue(LimitGate.canAddItem(true, 500))
        assertTrue(LimitGate.canAddHome(true, 9))
        assertTrue(LimitGate.canAddPanel(true, 9))
        assertTrue(LimitGate.canSync(true))
    }

    @Test fun limitsLiftWithoutRestartWhenProFlips() { // AC-H18
        val ent = FakeEntitlements()
        assertTrue(LimitGate.blocked(ent, LimitedFeature.SixteenthItem, 15))
        ent.setPro(true)
        assertFalse(LimitGate.blocked(ent, LimitedFeature.SixteenthItem, 15))
    }

    @Test fun unavailableBillingStaysFree() { // AC-H18
        assertFalse(UnavailableEntitlements.isPro.value)
    }
}
