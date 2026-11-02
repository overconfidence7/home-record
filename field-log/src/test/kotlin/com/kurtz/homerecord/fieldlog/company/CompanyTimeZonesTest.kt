package com.kurtz.homerecord.fieldlog.company

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanySettings
import org.junit.Test
import java.util.TimeZone

// AC-F42 (R09f1): the zones FL-Company offers for settings.timeZone
class CompanyTimeZonesTest {
    @Test fun unsetMeansTheBackendDefaultEastern() {
        assertThat(CompanyTimeZones.effective(null)).isEqualTo(CompanySettings.DEFAULT_TIME_ZONE)
        assertThat(CompanyTimeZones.effective(" ")).isEqualTo("America/New_York")
        assertThat(CompanyTimeZones.choiceFor(null).label).isEqualTo(com.kurtz.homerecord.fieldlog.R.string.company_tz_eastern)
    }

    @Test fun offersTheUsZonesEasternFirstAndEveryOneIsReal() {
        val ids = CompanyTimeZones.us.map { it.id }
        assertThat(ids.first()).isEqualTo("America/New_York")
        assertThat(ids).containsAtLeast("America/Chicago", "America/Denver", "America/Phoenix", "America/Los_Angeles", "America/Anchorage", "Pacific/Honolulu")
        assertThat(ids).containsNoDuplicates()
        ids.forEach { assertThat(TimeZone.getAvailableIDs().toList()).contains(it) }
    }

    @Test fun aZoneSetElsewhereIsKeptAndListedLast() {
        val choices = CompanyTimeZones.choices("Europe/Dublin")
        assertThat(choices.last()).isEqualTo(TimeZoneChoice("Europe/Dublin", null))
        assertThat(choices).hasSize(CompanyTimeZones.us.size + 1)
        assertThat(CompanyTimeZones.choices("America/Chicago")).isEqualTo(CompanyTimeZones.us)
        assertThat(CompanyTimeZones.choiceFor("Europe/Dublin").label).isNull()
    }

    @Test fun tagsDropTheSlash() {
        assertThat(timeZoneTag("America/Los_Angeles")).isEqualTo("america_los_angeles")
    }
}
