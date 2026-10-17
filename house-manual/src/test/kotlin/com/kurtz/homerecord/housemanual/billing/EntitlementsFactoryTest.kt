package com.kurtz.homerecord.housemanual.billing

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.billing.OffersResult
import com.kurtz.homerecord.core.billing.UnavailableEntitlements
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EntitlementsFactoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun blankKeyGivesFreeTierWithUnavailableOffers() = runTest {
        val e = EntitlementsFactory.create(context, "  ")
        assertTrue(e is UnavailableEntitlements)
        assertFalse(e.isPro.value)
        assertEquals(OffersResult.Unavailable, e.loadOffers())
    }
}
