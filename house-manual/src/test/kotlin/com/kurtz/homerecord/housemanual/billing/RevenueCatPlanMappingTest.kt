package com.kurtz.homerecord.housemanual.billing

import com.kurtz.homerecord.core.billing.Plan
import com.revenuecat.purchases.PackageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RevenueCatPlanMappingTest {
    @Test fun annualPackageMapsToAnnualPlan() {
        assertEquals(Plan.YEARLY, RevenueCatEntitlements.planFor(PackageType.ANNUAL))
    }

    @Test fun lifetimePackageMapsToLifetimePlan() {
        assertEquals(Plan.LIFETIME, RevenueCatEntitlements.planFor(PackageType.LIFETIME))
    }

    @Test fun otherPackagesAreIgnored() {
        listOf(PackageType.MONTHLY, PackageType.WEEKLY, PackageType.CUSTOM, PackageType.UNKNOWN)
            .forEach { assertNull(it.name, RevenueCatEntitlements.planFor(it)) }
    }

    @Test fun entitlementIdIsPro() {
        assertEquals("pro", RevenueCatEntitlements.PRO_ENTITLEMENT)
    }
}
