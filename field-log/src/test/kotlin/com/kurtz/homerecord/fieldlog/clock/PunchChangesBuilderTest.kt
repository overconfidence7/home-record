package com.kurtz.homerecord.fieldlog.clock

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.punch.PunchEdit
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// AC-F14: the audit entry (who, when, before/after, why) is visible on the crew's own punch in My timesheet.
class PunchChangesBuilderTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val monday = LocalDate.of(2026, 10, 19)
    private fun at(day: Int, h: Int, m: Int = 0) = monday.plusDays(day.toLong()).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private val jobs = mapOf("j1" to "Garage subpanel", "j2" to "Ortiz kitchen remodel")

    private fun punch(id: String, kind: String, at: Long, vararg edits: PunchEdit, deletedAt: Long? = null) = PunchEntity(
        id = id, companyId = "c1", memberId = "m1", jobId = edits.lastOrNull()?.afterJobId ?: "j1", kind = kind, at = at,
        editsJson = Json.encodeToString(edits.toList()), createdAt = 1, updatedAt = 1, deletedAt = deletedAt,
    )

    @Test fun showsWhoWhenBeforeAfterAndWhyNewestFirst() {
        val older = PunchEdit("o1", "Dana Ruiz", at(1, 16, 10), "Forgot to clock in", at(1, 7, 30), at(1, 6, 45), "j1", "j1")
        val newer = PunchEdit("o1", "Dana Ruiz", at(2, 8, 5), "Was on the Ortiz job", at(0, 16, 30), at(0, 16, 30), "j1", "j2")
        val changes = PunchChangesBuilder.build(
            listOf(punch("p1", PunchKind.IN, at(1, 6, 45), older), punch("p2", PunchKind.OUT, at(0, 16, 30), newer), punch("p3", PunchKind.IN, at(0, 7))),
            jobs, monday, zone,
        )
        assertThat(changes.map { it.punchId }).containsExactly("p2", "p1").inOrder()
        val time = changes[1]
        assertThat(time.kind).isEqualTo(PunchKind.IN)
        assertThat(time.day).isEqualTo(LocalDate.of(2026, 10, 20))
        assertThat(time.before).isEqualTo(LocalTime.of(7, 30))
        assertThat(time.after).isEqualTo(LocalTime.of(6, 45))
        assertThat(time.byName).isEqualTo("Dana Ruiz")
        assertThat(time.changedAt).isEqualTo(LocalDateTime.of(2026, 10, 20, 16, 10))
        assertThat(time.reason).isEqualTo("Forgot to clock in")
        assertThat(time.timeChanged).isTrue()
        assertThat(time.jobChanged).isFalse()
        val job = changes[0]
        assertThat(job.timeChanged).isFalse()
        assertThat(job.jobChanged).isTrue()
        assertThat(job.beforeJob).isEqualTo("Garage subpanel")
        assertThat(job.afterJob).isEqualTo("Ortiz kitchen remodel")
    }

    @Test fun everyEditOnAPunchIsItsOwnLine() {
        val first = PunchEdit("o1", "Dana Ruiz", at(1, 9), "Wrong time", at(1, 7), at(1, 6), "j1", "j1")
        val second = PunchEdit("o2", "Sam Kurtz", at(1, 10), "Back to the real time", at(1, 6), at(1, 6, 30), "j1", "j1")
        val changes = PunchChangesBuilder.build(listOf(punch("p1", PunchKind.IN, at(1, 6, 30), first, second)), jobs, monday, zone)
        assertThat(changes.map { it.byName }).containsExactly("Sam Kurtz", "Dana Ruiz").inOrder()
    }

    @Test fun otherWeeksDeletedAndUneditedPunchesAreLeftOut() {
        val lastWeek = PunchEdit("o1", "Dana Ruiz", at(-6, 9), "Late", at(-6, 7), at(-6, 6), "j1", "j1")
        val deleted = PunchEdit("o1", "Dana Ruiz", at(1, 9), "Typo", at(1, 7), at(1, 6), "j1", "j1")
        val changes = PunchChangesBuilder.build(
            listOf(punch("p1", PunchKind.IN, at(-6, 6), lastWeek), punch("p2", PunchKind.IN, at(1, 6), deleted, deletedAt = 5), punch("p3", PunchKind.IN, at(1, 7))),
            jobs, monday, zone,
        )
        assertThat(changes).isEmpty()
    }

    @Test fun aPunchMovedIntoThisWeekCountsAndAnUnknownJobReadsAsNull() {
        val moved = PunchEdit("o1", "Dana Ruiz", at(0, 9), "Wrong day", at(-1, 7), at(0, 7), null, "gone")
        val changes = PunchChangesBuilder.build(listOf(punch("p1", PunchKind.IN, at(0, 7), moved)), jobs, monday, zone)
        assertThat(changes.single().jobChanged).isTrue()
        assertThat(changes.single().beforeJob).isNull()
        assertThat(changes.single().afterJob).isNull()
    }
}
