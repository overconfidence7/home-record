package com.kurtz.homerecord.fieldlog.push

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.push.PushMessage
import com.kurtz.homerecord.coreui.shell.TabRequest
import com.kurtz.homerecord.fieldlog.JobKey
import com.kurtz.homerecord.fieldlog.TaskFormKey
import com.kurtz.homerecord.fieldlog.TimesheetsKey
import java.time.LocalDate
import org.junit.Test

// AC-F36 (a task notification opens the task), AC-F42 (a schedule notification opens the day): T047b2 deep links.
class PushDestinationTest {
    private val crew = RoleGates(CompanyRole.Crew)
    private val owner = RoleGates(CompanyRole.Owner)
    private val lead = RoleGates(CompanyRole.Lead, leadJobs = setOf("j-hale"))
    private val task = PushMessage.TaskAssigned("c1", "j-hale", "Hale kitchen", "t1", "Set the kitchen outlets")

    @Test fun crewOpenTheJobsTasksTab() {
        assertThat(PushDestination.of(task, "c1", crew)).isEqualTo(TabRequest("jobs", listOf(JobKey("j-hale", "TASKS"))))
    }

    @Test fun whoeverManagesTheTasksLandsInTheTaskForm() {
        val expected = TabRequest("jobs", listOf(JobKey("j-hale", "TASKS"), TaskFormKey("j-hale", "t1")))
        assertThat(PushDestination.of(task, "c1", lead)).isEqualTo(expected)
        assertThat(PushDestination.of(task, "c1", owner)).isEqualTo(expected)
        // A Lead on another job is crew here.
        assertThat(PushDestination.of(task.copy(jobId = "j-other"), "c1", lead).keys).containsExactly(JobKey("j-other", "TASKS"))
    }

    @Test fun scheduleOpensTheScheduleTab() {
        val day = PushMessage.ScheduleChanged("c1", LocalDate.of(2026, 11, 2), "j-hale", "Hale kitchen")
        assertThat(PushDestination.of(day, "c1", crew)).isEqualTo(TabRequest("schedule"))
    }

    @Test fun changeOrderOpensTheJobsChangesTab() {
        val co = PushMessage.ChangeOrderSigned("c1", "j-hale", "Hale kitchen", "co1", "Add a circuit", "Dana Hale")
        assertThat(PushDestination.of(co, "c1", owner)).isEqualTo(TabRequest("jobs", listOf(JobKey("j-hale", "CHANGES"))))
    }

    @Test fun timesheetOpensTheWeekForManagersAndClockForCrew() {
        val week = PushMessage.TimesheetApproved("c1", LocalDate.of(2026, 10, 26))
        assertThat(PushDestination.of(week, "c1", owner)).isEqualTo(TabRequest("more", listOf(TimesheetsKey("2026-10-26"))))
        assertThat(PushDestination.of(week, "c1", crew)).isEqualTo(TabRequest("clock"))
    }

    @Test fun anotherCompanyOrNoJobOpensToday() {
        assertThat(PushDestination.of(task, "c2", owner)).isEqualTo(TabRequest("today"))
        assertThat(PushDestination.of(task.copy(jobId = ""), "c1", owner)).isEqualTo(TabRequest("today"))
    }
}
