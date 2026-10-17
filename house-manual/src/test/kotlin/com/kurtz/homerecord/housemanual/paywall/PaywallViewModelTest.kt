package com.kurtz.homerecord.housemanual.paywall

import com.kurtz.homerecord.core.billing.Plan
import com.kurtz.homerecord.core.billing.PurchaseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {
    private val entitlements = FakeEntitlements()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `AC-94 offers yearly and lifetime`() {
        val state = PaywallViewModel(entitlements).state.value
        assertEquals(listOf(Plan.YEARLY, Plan.LIFETIME), state.offers.map { it.plan })
        assertFalse(state.pricesUnavailable)
    }

    @Test fun `AC-94 purchase turns on the entitlement immediately`() {
        val vm = PaywallViewModel(entitlements)
        vm.onEvent(PaywallEvent.Buy(Plan.LIFETIME))
        assertTrue(vm.state.value.isPro)
        assertEquals(listOf(Plan.LIFETIME), entitlements.purchases)
    }

    @Test fun `AC-96 no prices shows unavailable state and Retry recovers`() {
        entitlements.offersAvailable = false
        val vm = PaywallViewModel(entitlements)
        assertTrue(vm.state.value.pricesUnavailable)
        entitlements.offersAvailable = true
        vm.onEvent(PaywallEvent.Retry)
        assertFalse(vm.state.value.pricesUnavailable)
    }

    @Test fun `failed purchase is reported without entitlement`() {
        entitlements.nextPurchase = PurchaseResult.Failed
        val vm = PaywallViewModel(entitlements)
        vm.onEvent(PaywallEvent.Buy(Plan.YEARLY))
        assertTrue(vm.state.value.purchaseFailed)
        assertFalse(vm.state.value.isPro)
    }
}
