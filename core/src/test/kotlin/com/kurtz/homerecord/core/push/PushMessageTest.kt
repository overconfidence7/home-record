package com.kurtz.homerecord.core.push

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

// AC-B30, AC-F36, AC-F42: the data-only messages the Worker sends (backend/src/push/events.ts) are read on the phone.
class PushMessageTest {
    @Test
    fun taskAssignedReadsTheTaskAndJob() {
        val m = PushMessage.parse(
            mapOf("type" to "task_assigned", "companyId" to "c1", "taskId" to "t1", "jobId" to "j1", "title" to "Set the kitchen outlets", "jobName" to "Hale kitchen"),
        )
        assertThat(m).isEqualTo(PushMessage.TaskAssigned("c1", "j1", "Hale kitchen", "t1", "Set the kitchen outlets"))
        assertThat(m?.channel).isEqualTo(PushChannel.Tasks)
        assertThat(m?.key).isEqualTo("task:t1")
    }

    @Test
    fun scheduleChangedReadsTheDay() {
        val m = PushMessage.parse(mapOf("type" to "schedule_changed", "companyId" to "c1", "date" to "2026-11-02", "jobId" to "j1", "jobName" to "Hale kitchen"))
        assertThat(m).isEqualTo(PushMessage.ScheduleChanged("c1", LocalDate.of(2026, 11, 2), "j1", "Hale kitchen"))
        assertThat(m?.channel).isEqualTo(PushChannel.Schedule)
        assertThat(m?.key).isEqualTo("schedule:2026-11-02")
    }

    @Test
    fun changeOrderSignedAndTimesheetApproved() {
        val co = PushMessage.parse(
            mapOf("type" to "change_order_signed", "companyId" to "c1", "changeOrderId" to "co1", "jobId" to "j1", "title" to "Add a circuit", "signedBy" to "Dana Hale"),
        )
        assertThat(co).isEqualTo(PushMessage.ChangeOrderSigned("c1", "j1", "", "co1", "Add a circuit", "Dana Hale"))
        assertThat(co?.channel).isEqualTo(PushChannel.ChangeOrders)
        val ts = PushMessage.parse(mapOf("type" to "timesheet_approved", "companyId" to "c1", "weekStart" to "2026-10-26"))
        assertThat(ts).isEqualTo(PushMessage.TimesheetApproved("c1", LocalDate.of(2026, 10, 26)))
        assertThat(ts?.channel).isEqualTo(PushChannel.Timesheets)
    }

    @Test
    fun unknownTypeOrMissingIdsAreDropped() {
        assertThat(PushMessage.parse(mapOf("type" to "invoice_paid", "companyId" to "c1"))).isNull()
        assertThat(PushMessage.parse(mapOf("type" to "task_assigned", "companyId" to "c1"))).isNull()
        assertThat(PushMessage.parse(mapOf("type" to "task_assigned", "taskId" to "t1"))).isNull()
        assertThat(PushMessage.parse(mapOf("type" to "schedule_changed", "companyId" to "c1", "date" to "tomorrow"))).isNull()
        assertThat(PushMessage.parse(emptyMap())).isNull()
    }

    @Test
    fun scheduleDayIsTodayTomorrowOrADate() {
        val today = LocalDate.of(2026, 11, 1)
        assertThat(pushDay(today, today)).isEqualTo(PushDay.Today)
        assertThat(pushDay(today.plusDays(1), today)).isEqualTo(PushDay.Tomorrow)
        assertThat(pushDay(today.plusDays(2), today)).isEqualTo(PushDay.Other)
    }
}
