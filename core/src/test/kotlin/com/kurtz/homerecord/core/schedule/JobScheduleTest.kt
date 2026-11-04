package com.kurtz.homerecord.core.schedule

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

// AC-F41 (assign a job to a person and day, move it, take it off; each write syncs), AC-F43 (a second job the same day is kept)
@AcceptanceCriterion("AC-F41", "AC-F43")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobScheduleTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var schedule: JobSchedule
    private val changes = mutableListOf<String>()
    private val wed = LocalDate.of(2026, 11, 4)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        schedule = JobSchedule(db, now = { 5_000L }, onChange = { e, id -> changes += "$e/$id" })
        runBlocking {
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1))
            db.jobDao().upsert(JobEntity(id = "j2", companyId = "c1", name = "Smith deck", createdAt = 1, updatedAt = 1))
        }
    }

    @After fun tearDown() = db.close()

    @Test fun assignQueuesOneChangeAndIsNotDoubled() = runBlocking<Unit> {
        val id = checkNotNull(schedule.assign("j1", "m1", wed))
        assertThat(schedule.assign("j1", "m1", wed)).isEqualTo(id)
        assertThat(changes).containsExactly("assignment/$id")
        assertThat(schedule.observeForCompany("c1").first().single().date).isEqualTo(wed)
        assertThat(schedule.assign("missing", "m1", wed)).isNull()
    }

    @Test fun secondJobSameDayIsKept() = runBlocking<Unit> {
        schedule.assign("j1", "m1", wed)
        schedule.assign("j2", "m1", wed)
        assertThat(schedule.observeForCompany("c1").first().map { it.jobId }).containsExactly("j1", "j2").inOrder()
    }

    @Test fun moveChangesDayAndPersonAndMergesATwin() = runBlocking<Unit> {
        val a = checkNotNull(schedule.assign("j1", "m1", wed))
        assertThat(schedule.move(a, wed.plusDays(1), "m2")).isTrue()
        val moved = schedule.observeForCompany("c1").first().single()
        assertThat(moved.date).isEqualTo(wed.plusDays(1))
        assertThat(moved.memberId).isEqualTo("m2")
        assertThat(schedule.move(a, wed.plusDays(1), "m2")).isFalse()

        schedule.assign("j1", "m1", wed)
        val b = schedule.observeForCompany("c1").first().first { it.memberId == "m1" }.id
        assertThat(schedule.move(b, wed.plusDays(1), "m2")).isTrue()
        assertThat(schedule.observeForCompany("c1").first().map { it.id }).containsExactly(a)
    }

    @Test fun removeSoftDeletesOnce() = runBlocking<Unit> {
        val a = checkNotNull(schedule.assign("j1", "m1", wed))
        changes.clear()
        assertThat(schedule.remove(a)).isTrue()
        assertThat(schedule.remove(a)).isFalse()
        assertThat(changes).containsExactly("assignment/$a")
        assertThat(schedule.observeForCompany("c1").first()).isEmpty()
    }
}
