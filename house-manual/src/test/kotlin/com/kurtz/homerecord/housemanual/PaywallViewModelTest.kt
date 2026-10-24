package com.kurtz.homerecord.housemanual

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.billing.FakeProBilling
import com.kurtz.homerecord.core.billing.LimitGate
import com.kurtz.homerecord.core.billing.LimitedFeature
import com.kurtz.homerecord.housemanual.paywall.PaywallEvent
import com.kurtz.homerecord.housemanual.paywall.PaywallFailure
import com.kurtz.homerecord.housemanual.paywall.PaywallViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    // AC-H18
    @Test fun showsBothPlansWithStorePrices() {
        val vm = PaywallViewModel(FakeProBilling(), LimitedFeature.SixteenthItem)
        val s = vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.plans.map { it.price }).containsExactly("$19.99 / year", "$49.99").inOrder()
        assertThat(s.feature).isEqualTo(LimitedFeature.SixteenthItem)
    }

    // AC-H18
    @Test fun priceFailureShowsRetryAndRetryRecovers() {
        val billing = FakeProBilling(offered = Result.failure(IllegalStateException("offline")))
        val vm = PaywallViewModel(billing, null)
        assertThat(vm.state.value.loadFailed).isTrue()
        billing.offered = FakeProBilling().offered
        vm.onEvent(PaywallEvent.Retry)
        assertThat(vm.state.value.loadFailed).isFalse()
        assertThat(vm.state.value.plans).hasSize(2)
    }

    // AC-H18
    @Test fun purchaseUnlocksProAndLiftsTheItemLimitWithoutRestart() {
        val billing = FakeProBilling()
        val vm = PaywallViewModel(billing, LimitedFeature.SixteenthItem)
        assertThat(LimitGate.blocked(billing, LimitedFeature.SixteenthItem, 15)).isTrue()
        vm.onEvent(PaywallEvent.Buy("pro_annual"))
        assertThat(vm.state.value.isPro).isTrue()
        assertThat(LimitGate.blocked(billing, LimitedFeature.SixteenthItem, 15)).isFalse()
    }

    // AC-H18
    @Test fun failedPurchaseKeepsFreeTierAndSaysSo() {
        val billing = FakeProBilling(purchaseResult = Result.failure(IllegalStateException("cancelled")))
        val vm = PaywallViewModel(billing, null)
        vm.onEvent(PaywallEvent.Buy("pro_annual"))
        assertThat(vm.state.value.isPro).isFalse()
        assertThat(vm.state.value.actionFailed).isEqualTo(PaywallFailure.Purchase)
        assertThat(vm.state.value.busy).isFalse()
    }

    // AC-H18
    @Test fun restoreWithNothingFoundSaysSo() {
        val vm = PaywallViewModel(FakeProBilling(restoreFindsPro = false), null)
        vm.onEvent(PaywallEvent.Restore)
        assertThat(vm.state.value.actionFailed).isEqualTo(PaywallFailure.NothingToRestore)
        assertThat(vm.state.value.isPro).isFalse()
    }

    // AC-H18
    @Test fun restoreFindingProUnlocks() {
        val vm = PaywallViewModel(FakeProBilling(), null)
        vm.onEvent(PaywallEvent.Restore)
        assertThat(vm.state.value.isPro).isTrue()
    }
}
