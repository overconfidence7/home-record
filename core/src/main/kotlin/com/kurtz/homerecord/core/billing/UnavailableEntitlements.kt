package com.kurtz.homerecord.core.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Used until the store implementation (T36) is wired: free tier, no offers, never crashes. */
class UnavailableEntitlements : Entitlements {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun loadOffers(): OffersResult = OffersResult.Unavailable
    override suspend fun purchase(plan: Plan): PurchaseResult = PurchaseResult.Failed
}
