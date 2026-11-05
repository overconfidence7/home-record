package com.kurtz.homerecord.core.billing

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// AC-F56: Field Log purchases are made for the company (`company:<id>`), Solo and Crew from the store's offering.
class CompanyBillingRulesTest {
    @Test
    fun appUserIdIsTheCompanyIdTheWebhookExpects() {
        assertEquals("company:c-42", CompanyBillingRules.appUserId("c-42"))
    }

    @Test
    fun packagesMapToSoloOrCrewByIdentifier() {
        assertEquals(FieldLogPlanKind.Solo, CompanyBillingRules.planOf("fieldlog_solo_monthly", "fieldlog_solo:monthly"))
        assertEquals(FieldLogPlanKind.Crew, CompanyBillingRules.planOf("\$rc_monthly", "FieldLog_Crew:monthly"))
        assertNull(CompanyBillingRules.planOf("\$rc_annual", "pro_annual"))
    }

    @Test
    fun activeEntitlementPrefersCrew() {
        assertEquals(FieldLogPlanKind.Crew, CompanyBillingRules.activePlan(setOf(FieldLogPlan.SOLO, FieldLogPlan.CREW)))
        assertEquals(FieldLogPlanKind.Solo, CompanyBillingRules.activePlan(setOf(FieldLogPlan.SOLO, "pro")))
        assertNull(CompanyBillingRules.activePlan(setOf("pro")))
    }

    @Test
    fun offersAreSoloThenCrewOncePerPlan() {
        val crew = CompanyPlanOffer("crew", FieldLogPlanKind.Crew, "$79.00")
        val solo = CompanyPlanOffer("solo", FieldLogPlanKind.Solo, "$39.00")
        val crewAgain = CompanyPlanOffer("crew2", FieldLogPlanKind.Crew, "$80.00")
        assertEquals(listOf(solo, crew), CompanyBillingRules.order(listOf(crew, solo, crewAgain)))
    }

    @Test
    fun unavailableBillingFailsEveryCall() = runTest {
        assertTrue(UnavailableCompanyBilling.plans("c").isFailure)
        assertTrue(UnavailableCompanyBilling.purchase("c", "solo").isFailure)
        assertTrue(UnavailableCompanyBilling.restore("c").isFailure)
    }
}
