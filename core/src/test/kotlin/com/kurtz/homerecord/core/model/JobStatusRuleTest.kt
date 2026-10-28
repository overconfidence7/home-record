package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.JobEntity
import org.junit.Test

// AC-F6, AC-F8
class JobStatusRuleTest {
    private fun job(status: String, start: String? = null, end: String? = null) =
        JobEntity(id = "j", companyId = "c", name = "Basement finish", status = status, startDate = start, endDate = end, createdAt = 1, updatedAt = 1)

    @Test
    fun anUpcomingJobBecomesActiveOnItsStartDate() {
        val saved = job("UPCOMING", start = "2026-11-09", end = "2026-11-20")
        assertThat(saved.effectiveStatus("2026-11-08")).isEqualTo(JobStatus.UPCOMING)
        assertThat(saved.effectiveStatus("2026-11-09")).isEqualTo(JobStatus.ACTIVE)
    }

    @Test
    fun anActiveJobIsDoneTheDayAfterItsEndDate() {
        val saved = job("ACTIVE", start = "2026-10-01", end = "2026-10-27")
        assertThat(saved.effectiveStatus("2026-10-27")).isEqualTo(JobStatus.ACTIVE)
        assertThat(saved.effectiveStatus("2026-10-28")).isEqualTo(JobStatus.DONE)
    }

    @Test
    fun aDoneJobWhoseEndDateMovedLaterIsActiveAgain() {
        assertThat(job("DONE", end = "2026-11-02").effectiveStatus("2026-10-28")).isEqualTo(JobStatus.ACTIVE)
    }

    @Test
    fun aDoneJobWithNoEndDateStaysDone() {
        assertThat(job("DONE").effectiveStatus("2026-10-28")).isEqualTo(JobStatus.DONE)
    }

    @Test
    fun archivedStaysArchivedWhateverTheDates() {
        assertThat(job("ARCHIVED", start = "2026-10-01", end = "2026-12-01").effectiveStatus("2026-10-28")).isEqualTo(JobStatus.ARCHIVED)
        assertThat(job("ARCHIVED").effectiveStatus("2026-10-28")).isEqualTo(JobStatus.ARCHIVED)
    }

    @Test
    fun undatedOrUnknownStatusIsActive() {
        assertThat(job("ACTIVE").effectiveStatus("2026-10-28")).isEqualTo(JobStatus.ACTIVE)
        assertThat(job("BOGUS").effectiveStatus("2026-10-28")).isEqualTo(JobStatus.ACTIVE)
    }
}
