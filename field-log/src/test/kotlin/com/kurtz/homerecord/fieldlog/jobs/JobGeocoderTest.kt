package com.kurtz.homerecord.fieldlog.jobs

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.geo.GeoPoint
import com.kurtz.homerecord.core.geo.Geocoder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F5: an address typed offline resolves to coordinates later, and is queued for sync once it does.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobGeocoderTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<Pair<String, String>>()
    private var answer: GeoPoint? = null
    private val geocoder = object : Geocoder {
        override suspend fun resolve(address: String) = answer
    }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun resolver() = JobGeocoder(db.jobDao(), geocoder, { e, id -> changes += e to id }, { 2000L })

    private fun job(id: String, address: String?, lat: Double? = null) =
        JobEntity(id = id, companyId = "c1", name = id, address = address, lat = lat, lng = lat, createdAt = 1L, updatedAt = 1L)

    @Test fun offlineLeavesTheJobAloneThenResolvesWhenBack(): Unit = runBlocking {
        db.jobDao().upsert(job("j1", "12 Oak St, Dayton OH"))
        assertThat(resolver().resolvePending("c1")).isEqualTo(0)
        assertThat(db.jobDao().get("j1")?.lat).isNull()
        assertThat(changes).isEmpty()

        answer = GeoPoint(39.76, -84.19)
        assertThat(resolver().resolvePending("c1")).isEqualTo(1)
        val saved = db.jobDao().get("j1")
        assertThat(saved?.lat).isEqualTo(39.76)
        assertThat(saved?.lng).isEqualTo(-84.19)
        assertThat(saved?.updatedAt).isEqualTo(2000L)
        assertThat(changes).containsExactly("job" to "j1")
    }

    @Test fun skipsJobsWithoutAnAddressOrAlreadyResolved(): Unit = runBlocking {
        answer = GeoPoint(1.0, 2.0)
        db.jobDao().upsert(job("none", null))
        db.jobDao().upsert(job("blank", "  "))
        db.jobDao().upsert(job("done", "5 Elm St", lat = 40.0))
        assertThat(resolver().resolvePending("c1")).isEqualTo(0)
        assertThat(db.jobDao().get("done")?.lat).isEqualTo(40.0)
        assertThat(changes).isEmpty()
    }
}
