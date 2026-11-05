package com.kurtz.homerecord.core.billing

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.company.CompanyRole
import org.junit.Test

// AC-F56: trial, Solo, Crew and expired, worked out the same way as backend/src/billing/plan.ts (AC-B50)
class FieldLogPlanTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    @Test fun theTrialIsEverythingOnWithDaysLeftRoundedUp() {
        val plan = FieldLogPlan.of(null, null, trialEndsAt = now + 13 * day + 1, now = now)
        assertThat(plan).isEqualTo(FieldLogPlan(FieldLogPlanKind.Trial, 14, 10))
        assertThat(FieldLogPlan.of(null, null, now + 1, now).trialDaysLeft).isEqualTo(1)
        assertThat(plan.access(CompanyRole.Crew)).isEqualTo(PlanAccess.Full)
    }

    @Test fun soloIsTheOwnerAloneAndCrewIsTen() {
        val solo = FieldLogPlan.of("fieldlog_solo", now + day, trialEndsAt = now - 1, now = now)
        assertThat(solo.kind).isEqualTo(FieldLogPlanKind.Solo)
        assertThat(solo.canAddMember(1)).isFalse()
        val crew = FieldLogPlan.of("fieldlog_crew", null, trialEndsAt = now + day, now = now)
        assertThat(crew.kind).isEqualTo(FieldLogPlanKind.Crew)
        assertThat(crew.canAddMember(9)).isTrue()
        assertThat(crew.canAddMember(10)).isFalse()
    }

    @Test fun aLapsedPlanSendsTheOwnerToThePaywallAndCrewToReadOnly() {
        val expired = FieldLogPlan.of("fieldlog_crew", entitlementExpiresAt = now - 1, trialEndsAt = now - day, now = now)
        assertThat(expired.kind).isEqualTo(FieldLogPlanKind.Expired)
        assertThat(expired.canAddMember(1)).isFalse()
        assertThat(expired.access(CompanyRole.Owner)).isEqualTo(PlanAccess.OwnerPaywall)
        for (role in listOf(CompanyRole.Office, CompanyRole.Lead, CompanyRole.Crew, CompanyRole.Sub)) {
            assertThat(expired.access(role)).isEqualTo(PlanAccess.CrewReadOnly)
        }
        assertThat(FieldLogPlan.of(null, null, null, now).kind).isEqualTo(FieldLogPlanKind.Expired)
    }

    @Test fun aCompanyThatMovedToSoloKeepsItsPeopleAndShowsHowManyAreOver() {
        val solo = FieldLogPlan.of("fieldlog_solo", null, null, now)
        assertThat(solo.overLimitBy(4)).isEqualTo(3)
        assertThat(solo.overLimitBy(1)).isEqualTo(0)
    }

    @Test fun readsFromTheMembership() {
        val m = CompanyMembership("c1", "Hale Electric", CompanyRole.Owner, trialEndsAt = now - 1, entitlement = "fieldlog_solo", entitlementExpiresAt = now + day)
        assertThat(m.plan(now).kind).isEqualTo(FieldLogPlanKind.Solo)
        assertThat(m.plan(now + 2 * day).access(CompanyRole.Owner)).isEqualTo(PlanAccess.OwnerPaywall)
    }
}
