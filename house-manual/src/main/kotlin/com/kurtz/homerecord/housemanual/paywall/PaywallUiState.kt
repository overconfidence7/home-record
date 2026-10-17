package com.kurtz.homerecord.housemanual.paywall

import com.kurtz.homerecord.core.billing.Offer
import com.kurtz.homerecord.core.billing.Plan

data class PaywallUiState(
    val loading: Boolean = true,
    /** Empty with [loading] false means prices could not be loaded (AC-96). */
    val offers: List<Offer> = emptyList(),
    val purchasing: Boolean = false,
    val purchaseFailed: Boolean = false,
    val isPro: Boolean = false,
) {
    val pricesUnavailable get() = !loading && offers.isEmpty()
}

sealed interface PaywallEvent {
    data object Retry : PaywallEvent
    data class Buy(val plan: Plan) : PaywallEvent
    data object Close : PaywallEvent
}
