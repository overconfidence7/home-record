package com.kurtz.homerecord.fieldlog.jobs

import com.kurtz.homerecord.core.data.AssignmentEntity
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.JobCrewEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JobOverviewMapperTest {
    private val job = JobEntity(id = "j1", companyId = "c", clientId = "cl", name = "Panel", address = "1 Main", lat = 1.0, lng = 2.0, phasesJson = "[\"Demo\",\"Finish\"]", createdAt = 1, updatedAt = 1)
    private fun task(id: String, done: Boolean) = TaskEntity(id = id, jobId = "j1", title = id, done = done, createdAt = 1, updatedAt = 1)
    private fun member(id: String, name: String) = MemberEntity(id = id, companyId = "c", name = name, createdAt = 1, updatedAt = 1)

    private fun build(assignments: List<AssignmentEntity> = emptyList(), worked: Long = 0) = JobOverviewMapper.build(
        job, ClientEntity(id = "cl", companyId = "c", name = "Rivera", createdAt = 1, updatedAt = 1),
        listOf(JobCrewEntity("x1", "j1", "m2", 1, 1), JobCrewEntity("x2", "j1", "m1", 1, 1), JobCrewEntity("x3", "j1", "gone", 1, 1)),
        listOf(member("m1", "Dana"), member("m2", "Bell")),
        assignments, listOf(task("a", false), task("b", true), task("c", false)), worked, 7, "2026-10-22",
    )

    // AC-F7
    @Test fun totalsCrewAndPhases() {
        val s = build(worked = 5_400_000)
        assertEquals(1.5, s.hoursThisWeek, 0.0)
        assertEquals(7, s.photoCount)
        assertEquals(2, s.openTasks)
        assertEquals(listOf("Bell", "Dana"), s.crew)
        assertEquals(listOf("Demo", "Finish"), s.phases)
        assertEquals("Rivera", s.clientName)
    }

    // AC-F7
    @Test fun nextDaySkipsThePastAndIsNullWhenNothingAhead() {
        val past = AssignmentEntity("a1", "j1", "m1", "2026-10-20", 1, 1)
        val future = AssignmentEntity("a2", "j1", "m1", "2026-10-25", 1, 1)
        assertEquals("2026-10-25", build(listOf(past, future)).nextDay)
        assertNull(build(listOf(past)).nextDay)
    }
}
