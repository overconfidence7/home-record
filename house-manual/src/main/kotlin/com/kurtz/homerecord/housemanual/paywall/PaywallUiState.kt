package com.kurtz.homerecord.housemanual.paywall

import com.kurtz.homerecord.core.billing.LimitedFeature
import com.kurtz.homerecord.core.billing.ProPlan

data class PaywallUiState(
    val feature: LimitedFeature? = null,
    val loading: Boolean = true,
    val plans: List<ProPlan> = emptyList(),
    /** Prices failed to load; shows Retry. */
    val loadFailed: Boolean = false,
    val busy: Boolean = false,
    /** A purchase or restore failed; shown under the plans. */
    val actionFailed: PaywallFailure? = null,
    val isPro: Boolean = false,
)

enum class PaywallFailure { Purchase, NothingToRestore }

sealed interface PaywallEvent {
    data object Back : PaywallEvent
    data object Retry : PaywallEvent
    data class Buy(val planId: String) : PaywallEvent
    data object Restore : PaywallEvent
}
