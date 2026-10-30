package com.kurtz.homerecord.housemanual.items

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.billing.LimitedFeature
import com.kurtz.homerecord.housemanual.FakeProBilling
import com.kurtz.homerecord.housemanual.ItemEditKey
import com.kurtz.homerecord.housemanual.PaywallKey
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AC-H18: the 16th free item opens HM-Paywall; Pro adds without a limit.
class AddItemGateTest {
    @Test fun underTheLimitOpensTheFormWithItsPresets() = runTest {
        val gate = AddItemGate({ 14 }, FakeProBilling())
        assertThat(gate.destination("home", roomId = "kitchen", subtypeId = "water_main", scanPlate = true))
            .isEqualTo(ItemEditKey(roomId = "kitchen", subtypeId = "water_main", scanPlate = true))
    }

    @Test fun fifteenItemsOnFreeOpensThePaywall() = runTest {
        val gate = AddItemGate({ 15 }, FakeProBilling())
        assertThat(gate.destination("home")).isEqualTo(PaywallKey(LimitedFeature.SixteenthItem))
    }

    @Test fun proHasNoLimitAndCountsTheRightHome() = runTest {
        val billing = FakeProBilling().apply { restore() }
        val asked = mutableListOf<String>()
        val gate = AddItemGate({ asked += it; 40 }, billing)
        assertThat(gate.destination("maple")).isEqualTo(ItemEditKey())
        assertThat(asked).containsExactly("maple")
    }
}
