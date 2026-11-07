package com.kurtz.homerecord.core.billing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchableProBillingTest {
    @Test fun qaSwitchUnlocksProWithoutRestart() = runTest(UnconfinedTestDispatcher()) { // AC-H18
        val forced = MutableStateFlow(false)
        val billing = SwitchableProBilling(UnavailableBilling, forced, backgroundScope)
        assertFalse(billing.isPro.value)
        assertTrue(LimitGate.blocked(billing, LimitedFeature.CloudSync, 0))
        forced.value = true
        assertTrue(billing.isPro.value)
        assertFalse(LimitGate.blocked(billing, LimitedFeature.CloudSync, 0))
    }

    @Test fun boughtProStaysProWithTheSwitchOff() = runTest(UnconfinedTestDispatcher()) { // AC-H18
        val bought = object : ProBilling by UnavailableBilling {
            override val isPro = MutableStateFlow(true)
        }
        val billing = SwitchableProBilling(bought, MutableStateFlow(false), backgroundScope)
        assertTrue(billing.isPro.value)
    }

    @Test fun storeCallsStillGoToTheRealBilling() = runTest(UnconfinedTestDispatcher()) {
        val billing = SwitchableProBilling(UnavailableBilling, MutableStateFlow(true), backgroundScope)
        assertTrue(billing.plans().isFailure)
    }
}
