package com.kurtz.homerecord.fieldlog.jobs

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.geo.GeoPoint
import org.junit.Test

// AC-F5, AC-F9: an address typed as coordinates is the job's location at once, offline, so the far-from-job check works.
class CoordinateAddressTest {
    @Test fun parsesALatLngPairAsAMapAppCopiesIt() {
        assertThat(CoordinateAddress.parse("40.0379, -75.3433")).isEqualTo(GeoPoint(40.0379, -75.3433))
        assertThat(CoordinateAddress.parse(" -33.8,151.2 ")).isEqualTo(GeoPoint(-33.8, 151.2))
        assertThat(CoordinateAddress.parse("40,-75")).isEqualTo(GeoPoint(40.0, -75.0))
    }

    @Test fun streetAddressesAndOutOfRangeValuesAreNotCoordinates() {
        assertThat(CoordinateAddress.parse("12 Oak St, Dayton OH")).isNull()
        assertThat(CoordinateAddress.parse("12, 34 Oak St")).isNull()
        assertThat(CoordinateAddress.parse("91.0, 10.0")).isNull()
        assertThat(CoordinateAddress.parse("45.0, 181.0")).isNull()
        assertThat(CoordinateAddress.parse("")).isNull()
        assertThat(CoordinateAddress.parse(null)).isNull()
    }

    @Test fun savingADraftWithCoordinatesStoresThem() {
        val e = JobDraft(name = "Lot 7", address = "40.0379, -75.3433").toEntity(null, "j1", "c1", null, "2026-10-29", 1L)
        assertThat(e.lat).isEqualTo(40.0379)
        assertThat(e.lng).isEqualTo(-75.3433)
        assertThat(e.address).isEqualTo("40.0379, -75.3433")
    }

    @Test fun aStreetAddressStillWaitsForTheGeocoderAndAChangeClearsOldCoordinates() {
        val old = JobEntity(id = "j1", companyId = "c1", name = "Lot 7", address = "40.0, -75.0", lat = 40.0, lng = -75.0, createdAt = 1L, updatedAt = 1L)
        val e = JobDraft(name = "Lot 7", address = "12 Oak St").toEntity(old, "j1", "c1", null, "2026-10-29", 2L)
        assertThat(e.lat).isNull()
        assertThat(e.lng).isNull()
    }
}
