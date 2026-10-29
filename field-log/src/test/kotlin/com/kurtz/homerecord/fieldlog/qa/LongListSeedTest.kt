package com.kurtz.homerecord.fieldlog.qa

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class LongListSeedTest {
    // AC-U11: the jank stage scrolls a 200-row Jobs list (S7)
    @Test fun twoHundredActiveJobsWithDistinctNamesInCreationOrder() {
        val jobs = LongListSeed.jobs("qa-company", LocalDate.of(2026, 10, 29), now = 1_000L)
        assertThat(jobs).hasSize(LongListSeed.JOBS)
        assertThat(jobs.map { it.name }.toSet()).hasSize(LongListSeed.JOBS)
        assertThat(jobs.map { it.id }.toSet()).hasSize(LongListSeed.JOBS)
        assertThat(jobs.map { it.companyId }.toSet()).containsExactly("qa-company")
        assertThat(jobs.map { it.status }.toSet()).containsExactly("ACTIVE")
        assertThat(jobs.map { it.createdAt }).isInStrictOrder()
        assertThat(jobs.first().name).isEqualTo("Alvarez panel upgrade")
        assertThat(jobs.all { !it.address.isNullOrBlank() }).isTrue()
    }

    @Test fun moreJobsThanCombinationsStayDistinct() {
        val jobs = LongListSeed.jobs("qa-company", LocalDate.of(2026, 10, 29), now = 0L, count = 450)
        assertThat(jobs.map { it.name }.toSet()).hasSize(450)
    }
}
