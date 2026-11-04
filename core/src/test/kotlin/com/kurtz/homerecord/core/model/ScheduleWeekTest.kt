package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test
import java.time.LocalDate

// AC-F41 (week view with crew rows, crew's own next day), AC-F43 (two jobs on one day flagged, not refused)
@AcceptanceCriterion("AC-F41", "AC-F43")
class ScheduleWeekTest {
    private val wed = LocalDate.of(2026, 11, 4)
    private val monday = LocalDate.of(2026, 11, 2)
    private val members = listOf("m1" to "Dana Ortiz", "m2" to "Luis Romero")
    private val jobs = mapOf("j1" to "Hale kitchen", "j2" to "Smith deck")

    @Test fun weekStartsMonday() {
        assertThat(ScheduleWeek.weekStart(wed)).isEqualTo(monday)
        assertThat(ScheduleWeek.weekStart(monday)).isEqualTo(monday)
        assertThat(ScheduleWeek.weekStart(LocalDate.of(2026, 11, 8))).isEqualTo(monday)
        assertThat(ScheduleWeek.days(monday).last()).isEqualTo(LocalDate.of(2026, 11, 8))
    }

    @Test fun rowsPerMemberWithSevenCells() {
        val rows = ScheduleWeek.build(
            monday, members,
            listOf(
                ScheduleEntry("a1", "j1", "m1", wed),
                ScheduleEntry("a2", "j2", "m2", monday),
                ScheduleEntry("a3", "j1", "m1", monday.plusDays(7)), // next week
                ScheduleEntry("a4", "gone", "m1", wed), // deleted job
            ),
            jobs,
        )
        assertThat(rows.map { it.name }).containsExactly("Dana Ortiz", "Luis Romero").inOrder()
        assertThat(rows.all { it.cells.size == 7 }).isTrue()
        assertThat(rows[0].cells[2].jobs.map { it.jobName }).containsExactly("Hale kitchen")
        assertThat(rows[0].cells.sumOf { it.jobs.size }).isEqualTo(1)
        assertThat(rows[1].cells[0].jobs.single().assignmentId).isEqualTo("a2")
    }

    @Test fun twoJobsOnOneDayIsAConflictNotARefusal() {
        val rows = ScheduleWeek.build(
            monday, members,
            listOf(ScheduleEntry("a1", "j1", "m1", wed), ScheduleEntry("a2", "j2", "m1", wed), ScheduleEntry("a3", "j1", "m1", wed)),
            jobs,
        )
        val cell = rows[0].cells[2]
        assertThat(cell.jobs.map { it.jobName }).containsExactly("Hale kitchen", "Smith deck").inOrder()
        assertThat(cell.conflict).isTrue()
        assertThat(rows[0].conflicts).isEqualTo(1)
        assertThat(rows[1].conflicts).isEqualTo(0)
    }

    @Test fun nextUpIsTomorrowOrTheNextScheduledDay() {
        val entries = listOf(
            ScheduleEntry("a1", "j2", "m1", wed.plusDays(1)),
            ScheduleEntry("a2", "j1", "m1", wed.plusDays(3)),
            ScheduleEntry("a3", "j1", "m1", wed), // today is not "next"
            ScheduleEntry("a4", "j1", "m2", wed.plusDays(5)),
        )
        assertThat(ScheduleWeek.nextUp("m1", entries, jobs, wed)).isEqualTo(NextScheduledDay(wed.plusDays(1), listOf("Smith deck")))
        assertThat(ScheduleWeek.nextUp("m2", entries, jobs, wed)).isEqualTo(NextScheduledDay(wed.plusDays(5), listOf("Hale kitchen")))
        assertThat(ScheduleWeek.nextUp("m2", entries, jobs, wed, lookAheadDays = 3)).isNull()
        assertThat(ScheduleWeek.nextUp("m3", entries, jobs, wed)).isNull()
    }
}
