package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.ShutoffKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShutoffRuleTest {
    @Test fun requiresKind() = assertFalse(ShutoffRule.canSave(null, "Turn clockwise"))
    @Test fun requiresInstructions() {
        assertFalse(ShutoffRule.canSave(ShutoffKind.WATER_MAIN, ""))
        assertFalse(ShutoffRule.canSave(ShutoffKind.WATER_MAIN, "   "))
    }
    @Test fun kindAndInstructionsSuffice() = assertTrue(ShutoffRule.canSave(ShutoffKind.GAS_MAIN, "Quarter turn"))
}
