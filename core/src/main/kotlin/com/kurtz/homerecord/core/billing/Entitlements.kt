package com.kurtz.homerecord.core.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the person has House Manual Pro. Billing SDK details stay behind this. */
interface Entitlements {
    val isPro: StateFlow<Boolean>
}

/** Used when no billing key is configured: everyone stays on the free tier. */
object UnavailableEntitlements : Entitlements {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(false)
}

/** Test and debug stand-in whose Pro state can be flipped. */
class FakeEntitlements(pro: Boolean = false) : Entitlements {
    private val state = MutableStateFlow(pro)
    override val isPro: StateFlow<Boolean> = state
    fun setPro(value: Boolean) { state.value = value }
}

/** What the free tier can't do; each maps to an HM-Paywall opening. */
enum class LimitedFeature { SecondHome, SixteenthItem, SecondPanel, CloudSync }

/** Free-tier limits: one home, 15 items, one panel, no cloud sync. Pro has none. */
object LimitGate {
    const val FREE_HOMES = 1
    const val FREE_ITEMS = 15
    const val FREE_PANELS = 1

    fun canAddHome(pro: Boolean, homes: Int): Boolean = pro || homes < FREE_HOMES
    fun canAddItem(pro: Boolean, items: Int): Boolean = pro || items < FREE_ITEMS
    fun canAddPanel(pro: Boolean, panels: Int): Boolean = pro || panels < FREE_PANELS
    fun canSync(pro: Boolean): Boolean = pro

    fun blockedBy(pro: Boolean, feature: LimitedFeature, current: Int = 0): Boolean = when (feature) {
        LimitedFeature.SecondHome -> !canAddHome(pro, current)
        LimitedFeature.SixteenthItem -> !canAddItem(pro, current)
        LimitedFeature.SecondPanel -> !canAddPanel(pro, current)
        LimitedFeature.CloudSync -> !canSync(pro)
    }

    /** Same checks as [blockedBy], re-evaluated whenever Pro changes so limits lift without a restart. */
    fun blocked(entitlements: Entitlements, feature: LimitedFeature, current: Int): Boolean =
        blockedBy(entitlements.isPro.value, feature, current)
}

