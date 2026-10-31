package com.kurtz.homerecord.core.company

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.AssignmentEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobCrewEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F3 (cr:fl-role-gates): the app hides what backend/src/sync/policy.ts would refuse (AC-B3)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoleGatesTest {
    @Test fun ownerAndOfficeDoEverythingOnEveryJob() {
        for (role in listOf(CompanyRole.Owner, CompanyRole.Office)) {
            val g = RoleGates(role)
            assertThat(g.canEditJobs).isTrue()
            assertThat(g.canManageFiles("j1")).isTrue()
            assertThat(g.canManageTasks("j1")).isTrue()
            assertThat(g.canDeletePhoto("j1")).isTrue()
            assertThat(g.canClockCrew("j1")).isTrue()
            assertThat(g.canClockCrew(null)).isTrue()
        }
    }

    @Test fun leadManagesOnlyTheJobsTheyAreOn() {
        val g = RoleGates(CompanyRole.Lead, setOf("j1"))
        assertThat(g.canEditJobs).isFalse()
        assertThat(g.canManageFiles("j1")).isTrue()
        assertThat(g.canDeletePhoto("j1")).isTrue()
        assertThat(g.canClockCrew("j1")).isTrue()
        assertThat(g.canManageFiles("j2")).isFalse()
        assertThat(g.canManageTasks("j1")).isTrue()
        assertThat(g.canManageTasks("j2")).isFalse()
        assertThat(g.canDeletePhoto("j2")).isFalse()
        assertThat(g.canClockCrew("j2")).isFalse()
        assertThat(g.canClockCrew(null)).isFalse()
    }

    @Test fun crewAndSubOnlyView() {
        for (role in listOf(CompanyRole.Crew, CompanyRole.Sub)) {
            // A job list never widens Crew or Sub, even if one were passed.
            val g = RoleGates(role, setOf("j1"))
            assertThat(g.canEditJobs).isFalse()
            assertThat(g.canManageFiles("j1")).isFalse()
            assertThat(g.canManageTasks("j1")).isFalse()
            assertThat(g.canDeletePhoto("j1")).isFalse()
            assertThat(g.canClockCrew("j1")).isFalse()
        }
    }

    @Test fun leadJobsComeFromJobCrewAndScheduledDaysLive() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val gates = RoleGates.observe(CompanyRole.Lead, "m1", db.jobCrewDao(), db.assignmentDao())
            assertThat(withTimeout(5_000) { gates.first() }.leadJobs).isEmpty()
            db.jobCrewDao().upsert(JobCrewEntity("c1", "j1", "m1", 1, 1))
            db.jobCrewDao().upsert(JobCrewEntity("c2", "j9", "m2", 1, 1))
            db.assignmentDao().upsert(AssignmentEntity("a1", "j2", "m1", "2026-10-28", 1, 1))
            db.assignmentDao().upsert(AssignmentEntity("a2", "j3", "m1", "2026-10-29", 1, 1, deletedAt = 2))
            assertThat(withTimeout(5_000) { gates.first { it.leadJobs.size == 2 } }.leadJobs).containsExactly("j1", "j2")
        } finally {
            db.close()
        }
    }

    @Test fun nonLeadsNeverReadTheJobLists() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            db.jobCrewDao().upsert(JobCrewEntity("c1", "j1", "m1", 1, 1))
            assertThat(RoleGates.observe(CompanyRole.Crew, "m1", db.jobCrewDao(), db.assignmentDao()).first()).isEqualTo(RoleGates(CompanyRole.Crew))
            assertThat(RoleGates.observe(CompanyRole.Lead, null, db.jobCrewDao(), db.assignmentDao()).first().leadJobs).isEmpty()
        } finally {
            db.close()
        }
    }
}
