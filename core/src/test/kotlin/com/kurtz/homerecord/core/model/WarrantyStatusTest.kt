package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AC-C33
class WarrantyStatusTest {
    private val today = "2026-10-23"

    @Test
    fun missingOrUnreadableEndIsUnknown() {
        assertThat(WarrantyStatus.of(null, today)).isEqualTo(WarrantyStatus.Unknown)
        assertThat(WarrantyStatus.of("", today)).isEqualTo(WarrantyStatus.Unknown)
        assertThat(WarrantyStatus.of("soon", today)).isEqualTo(WarrantyStatus.Unknown)
    }

    @Test
    fun pastEndIsExpired() {
        assertThat(WarrantyStatus.of("2026-10-22", today)).isEqualTo(WarrantyStatus.Expired("2026-10-22"))
    }

    @Test
    fun endsTodayIsExpiringWithZeroDays() {
        assertThat(WarrantyStatus.of("2026-10-23", today)).isEqualTo(WarrantyStatus.Expiring("2026-10-23", 0))
    }

    @Test
    fun sixtyDaysOutIsExpiringAndSixtyOneIsActive() {
        assertThat(WarrantyStatus.of("2026-12-22", today)).isEqualTo(WarrantyStatus.Expiring("2026-12-22", 60))
        assertThat(WarrantyStatus.of("2026-12-23", today)).isEqualTo(WarrantyStatus.Active("2026-12-23"))
    }

    @Test
    fun endsOnUsesLongestTermFromInServiceDate() {
        assertThat(WarrantyStatus.endsOn("2024-01-31", 60, 12)).isEqualTo("2029-01-31")
        assertThat(WarrantyStatus.endsOn("2024-01-31", null, 1)).isEqualTo("2024-02-29")
    }

    @Test
    fun endsOnIsNullWithoutDateOrTerm() {
        assertThat(WarrantyStatus.endsOn(null, 60, 12)).isNull()
        assertThat(WarrantyStatus.endsOn("2024-01-31", null, null)).isNull()
        assertThat(WarrantyStatus.endsOn("2024-01-31", 0, 0)).isNull()
    }
}
