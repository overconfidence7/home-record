package com.kurtz.homerecord.housemanual

import com.kurtz.homerecord.core.billing.ProBilling
import com.kurtz.homerecord.core.billing.ProPlan
import com.kurtz.homerecord.core.billing.ProPlanKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Test billing at the SPEC §11 prices: plans are fixed, a purchase or restore flips Pro like the Test Store does. */
class FakeProBilling(
    var offered: Result<List<ProPlan>> = Result.success(
        listOf(ProPlan("pro_annual", ProPlanKind.Annual, "$24.99 / year"), ProPlan("pro_lifetime", ProPlanKind.Lifetime, "$59.00")),
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
