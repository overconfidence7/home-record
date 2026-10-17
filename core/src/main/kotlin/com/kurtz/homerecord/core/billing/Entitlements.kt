package com.kurtz.homerecord.core.billing

import kotlinx.coroutines.flow.StateFlow

enum class Plan { YEARLY, LIFETIME }

/** A purchasable plan with its store-formatted price. */
data class Offer(val plan: Plan, val price: String)

sealed interface OffersResult {
    data class Loaded(val offers: List<Offer>) : OffersResult
    /** No network or no store: the paywall shows "can't load prices" and Retry (AC-96). */
    data object Unavailable : OffersResult
}

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object Cancelled : PurchaseResult
    data object Failed : PurchaseResult
}

/**
 * Billing boundary (AC-95). The RevenueCat implementation is the only class that imports the SDK;
 * every other class, and every test, talks to this interface. Implementations must never throw.
 */
interface Entitlements {
    /** True while the Pro entitlement is active; the limits lift as soon as this flips. */
    val isPro: StateFlow<Boolean>

    suspend fun loadOffers(): OffersResult

    suspend fun purchase(plan: Plan): PurchaseResult
}
