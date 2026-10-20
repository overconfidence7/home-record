package com.kurtz.homerecord.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeRecordDatabaseTest {
    private lateinit var db: HomeRecordDatabase

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun home(id: String = "h1") = HomeEntity(id = id, name = "Maple St", createdAt = 1, updatedAt = 1)

    @Test fun homeRoundTrips() = runTest {
        db.homeDao().upsert(home())
        assertThat(db.homeDao().get("h1")?.name).isEqualTo("Maple St")
    }

    @Test fun softDeleteHidesRowAndBumpsUpdatedAt() = runTest {
        db.homeDao().upsert(home())
        db.homeDao().softDelete("h1", at = 99)
        assertThat(db.homeDao().get("h1")).isNull()
        assertThat(db.homeDao().observeAll().first()).isEmpty()
    }

    @Test fun floorsListInOrder() = runTest {
        db.homeDao().upsert(home())
        db.floorDao().upsert(FloorEntity("f2", "h1", sortOrder = 1, name = "Upper", createdAt = 1, updatedAt = 1))
        db.floorDao().upsert(FloorEntity("f1", "h1", sortOrder = 0, name = "Main", createdAt = 2, updatedAt = 2))
        assertThat(db.floorDao().listFor("h1").map { it.name }).containsExactly("Main", "Upper").inOrder()
    }

    @Test fun upsertReplacesSameId() = runTest {
        db.homeDao().upsert(home())
        db.homeDao().upsert(home().copy(name = "Oak Ave", updatedAt = 5))
        assertThat(db.homeDao().observeAll().first()).hasSize(1)
        assertThat(db.homeDao().get("h1")?.name).isEqualTo("Oak Ave")
    }

    @Test fun outboxReturnsPendingInOrderAndMarksSent() = runTest {
        val dao = db.outboxDao()
        val a = dao.add(OutboxEntity(tenantKind = "home", tenantId = "h", entity = "home", entityId = "h1", op = "upsert", payload = "{}", updatedAt = 1, deviceId = "d"))
        val b = dao.add(OutboxEntity(tenantKind = "home", tenantId = "h", entity = "home", entityId = "h2", op = "upsert", payload = "{}", updatedAt = 2, deviceId = "d"))
        assertThat(dao.pending().map { it.seq }).containsExactly(a, b).inOrder()
        dao.markSent(listOf(a))
        assertThat(dao.pending().map { it.seq }).containsExactly(b)
    }

    @Test fun fieldLogEntitiesPersist() = runTest {
        db.companyDao().upsert(CompanyEntity("c1", "Kurtz Builders", createdAt = 1, updatedAt = 1))
        db.jobDao().upsert(JobEntity("j1", "c1", name = "Kitchen remodel", createdAt = 1, updatedAt = 1))
        assertThat(db.jobDao().listFor("c1").map { it.name }).containsExactly("Kitchen remodel")
    }
}
