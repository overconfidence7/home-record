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


/** One plan on the paywall; [price] is the store's own formatted price, never hardcoded. */
data class ProPlan(val id: String, val kind: ProPlanKind, val price: String)

enum class ProPlanKind { Annual, Lifetime }

/** What the paywall needs from the store: the plans, buying one, and restoring. */
interface ProBilling : Entitlements {
    suspend fun plans(): Result<List<ProPlan>>
    suspend fun purchase(planId: String): Result<Unit>
    suspend fun restore(): Result<Unit>
}

/** No billing key in this build: the paywall shows its Retry state and the free tier stays. */
object UnavailableBilling : ProBilling {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun plans(): Result<List<ProPlan>> = Result.failure(IllegalStateException("No billing key"))
    override suspend fun purchase(planId: String): Result<Unit> = Result.failure(IllegalStateException("No billing key"))
    override suspend fun restore(): Result<Unit> = Result.failure(IllegalStateException("No billing key"))
}

/** Test and preview billing: plans are fixed, a purchase or restore flips Pro like the Test Store does. */
class FakeProBilling(
    var offered: Result<List<ProPlan>> = Result.success(
        listOf(ProPlan("pro_annual", ProPlanKind.Annual, "$19.99 / year"), ProPlan("pro_lifetime", ProPlanKind.Lifetime, "$49.99")),
    ),
    var purchaseResult: Result<Unit> = Result.success(Unit),
    private val restoreFindsPro: Boolean = true,
) : ProBilling {
    private val state = MutableStateFlow(false)
    override val isPro: StateFlow<Boolean> = state
    override suspend fun plans() = offered
    override suspend fun purchase(planId: String): Result<Unit> = purchaseResult.onSuccess { state.value = true }
    override suspend fun restore(): Result<Unit> = if (restoreFindsPro) { state.value = true; Result.success(Unit) } else Result.failure(NoSuchElementException("Nothing to restore"))
}
