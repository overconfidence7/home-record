package com.kurtz.homerecord.core.location

import android.Manifest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocationPermissionsTest {
    private val fine = Manifest.permission.ACCESS_FINE_LOCATION
    private val coarse = Manifest.permission.ACCESS_COARSE_LOCATION

    // AC-F8: Android 12+ drops a request for precise location alone with no dialog, so both are asked together.
    @Test fun asksForPreciseAndApproximateTogether() {
        assertThat(LocationPermissions.ALL.toList()).containsExactly(fine, coarse)
    }

    // AC-F8, AC-F18: picking "Approximate" in the system dialog still counts as allowed.
    @Test fun approximateOnlyCountsAsAllowed() {
        assertThat(LocationPermissions.allowed(mapOf(fine to false, coarse to true))).isTrue()
        assertThat(LocationPermissions.allowed(mapOf(fine to true, coarse to true))).isTrue()
    }

    // AC-F18: denying both, or a dismissed dialog with no answers, takes the no-location path.
    @Test fun nothingGrantedIsDenied() {
        assertThat(LocationPermissions.allowed(mapOf(fine to false, coarse to false))).isFalse()
        assertThat(LocationPermissions.allowed(emptyMap())).isFalse()
    }
}
