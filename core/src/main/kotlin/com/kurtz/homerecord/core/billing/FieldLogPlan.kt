package com.kurtz.homerecord.core.billing

import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.company.CompanyRole

/** Field Log's plans (AC-F56): the 14-day trial, Solo, Crew, or nothing in force. */
enum class FieldLogPlanKind { Trial, Solo, Crew, Expired }

/** What a phone may do under the company's plan. */
enum class PlanAccess {
    /** Trial or a paid plan: everything on. */
    Full,

    /** The plan lapsed and this is the Owner: FL-Paywall. */
    OwnerPaywall,

    /** The plan lapsed and this is anyone else: "Ask your owner to renew" and their own timesheet, read-only. */
    CrewReadOnly,
}

/**
 * The company's plan as the backend reports it on `GET /me` (AC-B50), worked out the same way as
 * backend/src/billing/plan.ts so the phone and the server agree offline too: a paid entitlement in force wins, the
 * trial counts as Crew (everything on, up to 10 members), Solo is the Owner alone, and an expired company takes
 * nobody new.
 */
data class FieldLogPlan(val kind: FieldLogPlanKind, val trialDaysLeft: Int?, val memberLimit: Int) {
    val isActive: Boolean get() = kind != FieldLogPlanKind.Expired

    fun access(role: CompanyRole): PlanAccess = when {
        isActive -> PlanAccess.Full
        role == CompanyRole.Owner -> PlanAccess.OwnerPaywall
        else -> PlanAccess.CrewReadOnly
    }

    /** Whether one more person may join a company that has [members] now (Owner included). */
    fun canAddMember(members: Int): Boolean = members < memberLimit

    /** How many current members are past the limit (a Crew company that moved to Solo keeps them; the Owner sees it). */
    fun overLimitBy(members: Int): Int = (members - memberLimit).coerceAtLeast(0).takeIf { isActive } ?: 0

    companion object {
        const val SOLO = "fieldlog_solo"
        const val CREW = "fieldlog_crew"
        const val SOLO_LIMIT = 1
        const val CREW_LIMIT = 10
        private const val DAY_MS = 86_400_000L

        fun of(entitlement: String?, entitlementExpiresAt: Long?, trialEndsAt: Long?, now: Long): FieldLogPlan {
            val inForce = entitlement?.takeIf { entitlementExpiresAt == null || entitlementExpiresAt > now }
            return when {
                inForce == CREW -> FieldLogPlan(FieldLogPlanKind.Crew, null, CREW_LIMIT)
                inForce == SOLO -> FieldLogPlan(FieldLogPlanKind.Solo, null, SOLO_LIMIT)
                trialEndsAt != null && trialEndsAt > now ->
                    FieldLogPlan(FieldLogPlanKind.Trial, ((trialEndsAt - now + DAY_MS - 1) / DAY_MS).toInt(), CREW_LIMIT)
                else -> FieldLogPlan(FieldLogPlanKind.Expired, null, 0)
            }
        }
    }
}

/** The plan of this membership at [now]. */
fun CompanyMembership.plan(now: Long): FieldLogPlan = FieldLogPlan.of(entitlement, entitlementExpiresAt, trialEndsAt, now)
