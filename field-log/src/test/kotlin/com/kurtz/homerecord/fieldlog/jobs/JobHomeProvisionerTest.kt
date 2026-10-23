package com.kurtz.homerecord.fieldlog.jobs

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F48: the job's House tab works on its own home, created once with a Main floor and queued for sync.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobHomeProvisionerTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<Pair<String, String>>()
    private var ids = 0

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun provisioner() = JobHomeProvisioner(db.jobDao(), db.homeDao(), db.floorDao(), { e, id -> changes += e to id }, { 5000L }, { "id${++ids}" })

    private fun job(homeId: String? = null) =
        JobEntity(id = "j1", companyId = "c1", name = "Kitchen remodel", address = "12 Oak St", homeId = homeId, createdAt = 1L, updatedAt = 1L)

    // AC-F48
    @Test fun createsTheHomeWithAMainFloorAndLinksTheJob() { runBlocking {
        db.jobDao().upsert(job())
        val homeId = provisioner().ensure("j1")
        assertThat(homeId).isEqualTo("id1")
        assertThat(db.jobDao().get("j1")?.homeId).isEqualTo("id1")
        assertThat(db.homeDao().get("id1")?.name).isEqualTo("Kitchen remodel")
        assertThat(db.floorDao().listFor("id1").map { it.name }).containsExactly("Main")
        assertThat(changes).containsExactly("home" to "id1", "floor" to "id2", "job" to "j1")
    } }

    // AC-F48
    @Test fun anExistingHomeIsReturnedUntouched() { runBlocking {
        db.jobDao().upsert(job(homeId = "h9"))
        assertThat(provisioner().ensure("j1")).isEqualTo("h9")
        assertThat(changes).isEmpty()
    } }

    // AC-F48
    @Test fun aMissingJobGivesNull() { runBlocking {
        assertThat(provisioner().ensure("nope")).isNull()
        assertThat(changes).isEmpty()
    } }
}
