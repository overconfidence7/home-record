package com.kurtz.homerecord.housemanual.items

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.billing.LimitedFeature
import com.kurtz.homerecord.housemanual.FakeProBilling
import com.kurtz.homerecord.housemanual.ItemEditKey
import com.kurtz.homerecord.housemanual.PaywallKey
import com.kurtz.homerecord.housemodel.items.ItemDraft
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AC-H18: the 16th free item or the second free panel opens HM-Paywall; Pro adds without a limit.
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

    @Test fun aSecondPanelOnFreeOpensThePaywallButOtherItemsStillAdd() = runTest {
        val gate = AddItemGate({ 3 }, FakeProBilling(), panels = { _, _ -> 1 })
        assertThat(gate.destination("home", subtypeId = "breaker_panel")).isEqualTo(PaywallKey(LimitedFeature.SecondPanel))
        assertThat(gate.destination("home", subtypeId = "sub_panel")).isEqualTo(PaywallKey(LimitedFeature.SecondPanel))
        assertThat(gate.destination("home", subtypeId = "furnace")).isEqualTo(ItemEditKey(subtypeId = "furnace"))
    }

    @Test fun theFirstPanelAddsAndProAddsMore() = runTest {
        assertThat(AddItemGate({ 3 }, FakeProBilling(), panels = { _, _ -> 0 }).destination("home", subtypeId = "breaker_panel"))
            .isEqualTo(ItemEditKey(subtypeId = "breaker_panel"))
        val pro = FakeProBilling().apply { restore() }
        assertThat(AddItemGate({ 3 }, pro, panels = { _, _ -> 4 }).destination("home", subtypeId = "breaker_panel"))
            .isEqualTo(ItemEditKey(subtypeId = "breaker_panel"))
    }

    @Test fun saveRefusesTurningAnotherItemIntoASecondPanelButTheOnePanelStillSaves() = runTest {
        val existing = mapOf("main" to "breaker_panel")
        val gate = AddItemGate({ 3 }, FakeProBilling(), panels = { _, except -> existing.keys.count { it != except } })
        assertThat(gate.saveBlocked("home", ItemDraft(id = null, subtypeId = "sub_panel"))).isTrue()
        assertThat(gate.saveBlocked("home", ItemDraft(id = "fridge", subtypeId = "sub_panel"))).isTrue()
        assertThat(gate.saveBlocked("home", ItemDraft(id = "main", subtypeId = "breaker_panel"))).isFalse()
        assertThat(gate.saveBlocked("home", ItemDraft(id = null, subtypeId = "furnace"))).isFalse()
    }
}
