package com.kurtz.homerecord.core.billing

/** One Field Log plan on FL-Paywall; [price] is the store's own formatted monthly price, never hardcoded (SPEC §Pricing). */
data class CompanyPlanOffer(val id: String, val kind: FieldLogPlanKind, val price: String)

/**
 * Field Log purchases (AC-F56): the Owner buys Solo or Crew for the company. RevenueCat's app user id is
 * `company:<id>` so the webhook (AC-B50) credits the company, not the person; the plan in force is still read from
 * `GET /me` ([FieldLogPlan]), so a successful purchase is followed by a membership refresh.
 */
interface CompanyBilling {
    /** Solo then Crew, with store prices. */
    suspend fun plans(companyId: String): Result<List<CompanyPlanOffer>>

    /** Buys [planId] for [companyId]; succeeds once the store reports the matching entitlement active. */
    suspend fun purchase(companyId: String, planId: String): Result<FieldLogPlanKind>

    /** Restores the company's plan; fails with "Nothing to restore" when none is active. */
    suspend fun restore(companyId: String): Result<FieldLogPlanKind>
}

/** No Field Log billing key in this build (BLOCKERS: the Field Log RevenueCat app): the paywall shows its Retry state. */
object UnavailableCompanyBilling : CompanyBilling {
    private fun <T> none(): Result<T> = Result.failure(IllegalStateException("No billing key"))
    override suspend fun plans(companyId: String): Result<List<CompanyPlanOffer>> = none()
    override suspend fun purchase(companyId: String, planId: String): Result<FieldLogPlanKind> = none()
    override suspend fun restore(companyId: String): Result<FieldLogPlanKind> = none()
}

/** Store-side naming rules for Field Log billing, kept out of the SDK wrapper so they are unit-tested. */
object CompanyBillingRules {
    private const val PREFIX = "company:"

    /** RevenueCat app user id for a company; the backend parses the same shape (backend/src/routes/billing.ts). */
    fun appUserId(companyId: String): String = PREFIX + companyId

    /**
     * Which plan a store package sells, from its package or product identifier (`fieldlog_solo_monthly`,
     * `crew_monthly`, `$rc_monthly` with product `fieldlog_crew:monthly`...). Anything else is not a Field Log plan.
     */
    fun planOf(packageId: String, productId: String): FieldLogPlanKind? {
        val ids = listOf(packageId, productId).map { it.lowercase() }
        return when {
            ids.any { "crew" in it } -> FieldLogPlanKind.Crew
            ids.any { "solo" in it } -> FieldLogPlanKind.Solo
            else -> null
        }
    }

    /** The plan the store says is active from its active entitlement ids; Crew wins when both are (an upgrade mid-period). */
    fun activePlan(activeEntitlements: Set<String>): FieldLogPlanKind? = when {
        FieldLogPlan.CREW in activeEntitlements -> FieldLogPlanKind.Crew
        FieldLogPlan.SOLO in activeEntitlements -> FieldLogPlanKind.Solo
        else -> null
    }

    /** Solo first, then Crew; one offer per plan (the first package found wins). */
    fun order(offers: List<CompanyPlanOffer>): List<CompanyPlanOffer> =
        offers.distinctBy { it.kind }.sortedBy { it.kind.ordinal }
}
