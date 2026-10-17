package com.kurtz.homerecord.housemanual.paywall

import com.kurtz.homerecord.core.billing.Entitlements
import com.kurtz.homerecord.core.billing.Offer
import com.kurtz.homerecord.core.billing.OffersResult
import com.kurtz.homerecord.core.billing.Plan
import com.kurtz.homerecord.core.billing.PurchaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeEntitlements : Entitlements {
    private val pro = MutableStateFlow(false)
    override val isPro: StateFlow<Boolean> = pro

    var offersAvailable = true
    var nextPurchase: PurchaseResult = PurchaseResult.Success
    val purchases = mutableListOf<Plan>()

    fun setPro(value: Boolean) { pro.value = value }

    override suspend fun loadOffers(): OffersResult =
        if (offersAvailable) OffersResult.Loaded(listOf(Offer(Plan.YEARLY, "$19.99"), Offer(Plan.LIFETIME, "$49.99")))
        else OffersResult.Unavailable

    override suspend fun purchase(plan: Plan): PurchaseResult {
        purchases += plan
        if (nextPurchase == PurchaseResult.Success) pro.value = true
        return nextPurchase
    }
}
