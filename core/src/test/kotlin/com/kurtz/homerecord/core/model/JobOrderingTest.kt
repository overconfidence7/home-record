package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AC-F6
class JobOrderingTest {
    private val today = "2026-10-22"

    private fun job(
        name: String,
        status: JobStatus = JobStatus.ACTIVE,
        start: String? = null,
        end: String? = null,
        days: List<String> = emptyList(),
    ) = JobSummary(id = name, name = name, status = status, startDate = start, endDate = end, scheduledDays = days)

    @Test
    fun activeSortsByNextScheduledDayThenName() {
        val jobs = listOf(
            job("Zeta deck", days = listOf("2026-10-25")),
            job("Smith kitchen", days = listOf("2026-10-01", "2026-10-23")),
            job("Adams bath"),
            job("Baker porch", days = listOf("2026-10-23")),
        )
        val names = JobOrdering.group(jobs, today).active.map { it.name }
        assertThat(names).containsExactly("Baker porch", "Smith kitchen", "Zeta deck", "Adams bath").inOrder()
    }

    @Test
    fun pastScheduledDaysAreIgnoredForNextDay() {
        val j = job("Old", days = listOf("2026-10-01"))
        assertThat(JobOrdering.nextDay(j, today)).isNull()
        assertThat(JobOrdering.nextDay(job("Today", days = listOf(today)), today)).isEqualTo(today)
    }

    @Test
    fun upcomingSortsByStartDateUndatedLast() {
        val jobs = listOf(
            job("C", JobStatus.UPCOMING),
            job("B", JobStatus.UPCOMING, start = "2026-12-01"),
            job("A", JobStatus.UPCOMING, start = "2026-11-01"),
        )
        assertThat(JobOrdering.group(jobs, today).upcoming.map { it.name })
            .containsExactly("A", "B", "C").inOrder()
    }

    @Test
    fun doneSortsByEndDateNewestFirstUndatedLast() {
        val jobs = listOf(
            job("Old", JobStatus.DONE, end = "2026-01-10"),
            job("None", JobStatus.DONE),
            job("New", JobStatus.DONE, end = "2026-09-30"),
        )
        assertThat(JobOrdering.group(jobs, today).done.map { it.name })
            .containsExactly("New", "Old", "None").inOrder()
    }

    @Test
    fun archivedHiddenUnlessToggled() {
        val jobs = listOf(job("Gone", JobStatus.ARCHIVED), job("Here"))
        assertThat(JobOrdering.group(jobs, today).archived).isEmpty()
        assertThat(JobOrdering.group(jobs, today, showArchived = true).archived.map { it.name })
            .containsExactly("Gone")
    }
}
