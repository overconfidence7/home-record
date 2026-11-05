package com.kurtz.homerecord.fieldlog.billing

import com.kurtz.homerecord.core.billing.CompanyPlanOffer
import com.kurtz.homerecord.core.billing.FieldLogPlan
import com.kurtz.homerecord.core.billing.FieldLogPlanKind

/**
 * FL-Paywall (AC-F56). [plan] is the company's plan in force from `/me`; [lapsed] means it ran out and this screen is
 * the Owner's whole app (no back arrow, Sign out instead) until a plan is bought or restored.
 */
data class PlanUiState(
    val plan: FieldLogPlan,
    val lapsed: Boolean = false,
    val loading: Boolean = true,
    val offers: List<CompanyPlanOffer> = emptyList(),
    /** Store prices failed to load (or no billing key in this build); shows Retry. */
    val loadFailed: Boolean = false,
    val busy: Boolean = false,
    val actionFailed: PlanFailure? = null,
    /** The store took the purchase or restore; the backend turns it on through the webhook and `/me`. */
    val bought: FieldLogPlanKind? = null,
)

enum class PlanFailure { Purchase, NothingToRestore }

sealed interface PlanEvent {
    data object Back : PlanEvent
    data object Retry : PlanEvent
    data class Buy(val planId: String) : PlanEvent
    data object Restore : PlanEvent
    data object SignOut : PlanEvent
}
