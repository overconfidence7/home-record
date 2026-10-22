package com.kurtz.homerecord.fieldlog.jobs

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.AssignmentEntity
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.JobEntity
import org.junit.Test

class JobListMapperTest {
    private val today = "2026-10-22"
    private fun job(id: String, name: String, status: String = "ACTIVE", clientId: String? = null, address: String? = null, start: String? = null) =
        JobEntity(id, "c1", clientId = clientId, name = name, address = address, status = status, startDate = start, createdAt = 1, updatedAt = 1)
    private val clients = listOf(ClientEntity("k1", "c1", "Rivera family", createdAt = 1, updatedAt = 1))
    private fun day(job: String, date: String) = AssignmentEntity("a-$job-$date", job, "m1", date, 1, 1)

    private val jobs = listOf(
        job("j1", "Panel upgrade", clientId = "k1", address = "418 Maple St"),
        job("j2", "Garage subpanel"),
        job("j3", "Basement finish", status = "UPCOMING", start = "2026-11-09"),
        job("j4", "Old rewire", status = "DONE"),
        job("j5", "Shed", status = "ARCHIVED"),
        job("j6", "Mystery", status = "BOGUS"),
    )
    private val rows = JobListMapper.rows(jobs, clients, listOf(day("j2", "2026-10-21"), day("j2", "2026-10-25"), day("j1", "2026-10-30")), today)

    // AC-F6
    @Test fun groupsActiveByNextScheduledDayThenUnscheduled() {
        val g = JobListMapper.group(rows, "", today, showArchived = false)
        assertThat(g.active.map { it.summary.id }).containsExactly("j2", "j1", "j6").inOrder()
        assertThat(g.upcoming.map { it.summary.id }).containsExactly("j3")
        assertThat(g.done.map { it.summary.id }).containsExactly("j4")
        assertThat(g.archived).isEmpty()
    }

    // AC-F6
    @Test fun nextDaySkipsPastDaysAndClientNameJoins() {
        val j2 = rows.first { it.summary.id == "j2" }
        assertThat(j2.nextDay).isEqualTo("2026-10-25")
        assertThat(rows.first { it.summary.id == "j1" }.clientName).isEqualTo("Rivera family")
    }

    @Test fun archivedOnlyWhenAsked() {
        assertThat(JobListMapper.group(rows, "", today, true).archived.map { it.summary.id }).containsExactly("j5")
    }

    @Test fun searchMatchesNameClientAndAddressIgnoringCase() {
        assertThat(JobListMapper.group(rows, "RIVERA", today, false).active.map { it.summary.id }).containsExactly("j1")
        assertThat(JobListMapper.group(rows, "maple", today, false).active.map { it.summary.id }).containsExactly("j1")
        assertThat(JobListMapper.group(rows, "garage", today, false).active.map { it.summary.id }).containsExactly("j2")
        assertThat(JobListMapper.group(rows, "zzz", today, false).isEmpty).isTrue()
    }
}
