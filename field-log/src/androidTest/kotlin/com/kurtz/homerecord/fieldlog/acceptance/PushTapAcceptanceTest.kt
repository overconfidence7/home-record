package com.kurtz.homerecord.fieldlog.acceptance

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.push.PushMessage
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.AppTab
import com.kurtz.homerecord.coreui.shell.HomeRecordApp
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.shell.PlaceholderKey
import com.kurtz.homerecord.coreui.shell.TabRequests
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.JobKey
import com.kurtz.homerecord.fieldlog.MainActivity
import com.kurtz.homerecord.fieldlog.TaskFormKey
import com.kurtz.homerecord.fieldlog.push.PushDestination
import com.kurtz.homerecord.fieldlog.push.PushIntents
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A push notification tap on a device (T047b2): the notification's intent (as PushNotifier builds it) goes back
 * through PushIntents and PushDestination into the real shell, which opens the screen on its tab. The FCM delivery
 * itself needs Firebase config (Needs Nate); the notification text and channel are PushNotifierTest on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class PushTapAcceptanceTest {
    @get:Rule val rule = createComposeRule()

    private val requests = TabRequests()

    private fun tap(message: PushMessage, data: Map<String, String>, gates: RoleGates) {
        val intent = PushIntents.put(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java), data)
        val parsed = PushIntents.messageOf(intent) ?: error("no push in the tap")
        check(parsed == message)
        val request = PushDestination.of(parsed, "c1", gates)
        requests.request(request.tabId, request.keys)
    }

    private fun setShell() = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "fl") {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                val tabs = listOf(
                    AppTab("today", "Today", Icons.Rounded.Today, Icons.Rounded.Today, PlaceholderKey("today")),
                    AppTab("jobs", "Jobs", Icons.Rounded.Work, Icons.Rounded.Work, PlaceholderKey("jobs")),
                    AppTab("clock", "Clock", Icons.Rounded.Schedule, Icons.Rounded.Schedule, PlaceholderKey("clock")),
                    AppTab("schedule", "Schedule", Icons.Rounded.CalendarMonth, Icons.Rounded.CalendarMonth, PlaceholderKey("schedule")),
                    AppTab("more", "More", Icons.Rounded.MoreHoriz, Icons.Rounded.MoreHoriz, PlaceholderKey("more")),
                )
                HomeRecordApp(tabs, tabRequests = requests.flow, entries = {
                    entry<PlaceholderKey> { Text("Root ${it.title}") }
                    entry<JobKey> { Text("Job ${it.jobId} on ${it.tab}") }
                    entry<TaskFormKey> { Text("Task form ${it.taskId}") }
                })
            }
        }
    }

    @AcceptanceCriterion("AC-F36")
    @Test fun aTaskNotificationOpensTheJobsTasks() {
        setShell()
        val data = mapOf("type" to "task_assigned", "companyId" to "c1", "taskId" to "t1", "jobId" to "j-hale", "title" to "Set the kitchen outlets", "jobName" to "Hale kitchen")
        tap(PushMessage.TaskAssigned("c1", "j-hale", "Hale kitchen", "t1", "Set the kitchen outlets"), data, RoleGates(CompanyRole.Crew))
        rule.onNodeWithText("Job j-hale on TASKS").assertIsDisplayed()
        rule.onNodeWithTag("fl_tab_jobs").assertIsSelected()
    }

    @AcceptanceCriterion("AC-F42")
    @Test fun aScheduleNotificationOpensTheSchedule() {
        setShell()
        val data = mapOf("type" to "schedule_changed", "companyId" to "c1", "date" to "2026-11-02", "jobId" to "j-hale", "jobName" to "Hale kitchen")
        tap(PushMessage.ScheduleChanged("c1", java.time.LocalDate.of(2026, 11, 2), "j-hale", "Hale kitchen"), data, RoleGates(CompanyRole.Crew))
        rule.onNodeWithText("Root schedule").assertIsDisplayed()
        rule.onNodeWithTag("fl_tab_schedule").assertIsSelected()
    }
}
