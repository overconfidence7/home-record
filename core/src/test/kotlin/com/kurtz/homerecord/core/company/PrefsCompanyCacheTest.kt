package com.kurtz.homerecord.core.company

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F18 (cr:fl-settings-source): the clock rules survive a restart with no network, and sign out drops them (AC-F4)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsCompanyCacheTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun keepsTheClockRulesWithTheMembership() {
        val rules = CompanySettings(OvertimeRule.Weekly40, breakRule = true, requireGps = true, punchDistanceFt = 750, photoOnPunch = true)
        val membership = CompanyMembership("c1", "Kurtz Builders", CompanyRole.Lead, 5, "pro", rules)
        PrefsCompanyCache(context).write(membership)
        assertThat(PrefsCompanyCache(context).read()).isEqualTo(membership)
        PrefsCompanyCache(context).write(null)
        assertThat(PrefsCompanyCache(context).read()).isNull()
    }

    @Test fun aCacheFromBeforeTheRulesReadsTheDefaults() {
        context.getSharedPreferences("company_cache", Context.MODE_PRIVATE).edit()
            .putString("id", "c1").putString("name", "Kurtz Builders").putString("role", "crew").commit()
        assertThat(PrefsCompanyCache(context).read()?.settings).isEqualTo(CompanySettings())
    }
}
