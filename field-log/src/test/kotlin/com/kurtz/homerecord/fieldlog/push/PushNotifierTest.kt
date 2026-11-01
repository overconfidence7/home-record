package com.kurtz.homerecord.fieldlog.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.push.PushMessage
import java.time.LocalDate
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// AC-F36 (task assigned notifies the assignee), AC-F42 (tomorrow's change notifies the crew): the phone side of AC-B30.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PushNotifierTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 11, 1)
    private val notifier = PushNotifier(app) { today }
    private val manager = app.getSystemService(NotificationManager::class.java)

    @Before
    fun allow() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun texts(message: PushMessage) = PushTexts.of(message, today, app.resources)

    @Test
    fun taskAssignedShowsTheTaskOnTheTasksChannel() {
        val data = mapOf("type" to "task_assigned", "companyId" to "c1", "taskId" to "t1", "jobId" to "j1", "title" to "Set the kitchen outlets", "jobName" to "Hale kitchen")
        val message = PushMessage.parse(data) ?: error("parse")
        assertThat(notifier.show(message, data)).isTrue()
        val posted = shadowOf(manager).allNotifications.single()
        assertThat(posted.channelId).isEqualTo("tasks")
        assertThat(posted.extras.getString(NotificationCompat.EXTRA_TITLE)).isEqualTo("New task on Hale kitchen")
        assertThat(posted.extras.getString(NotificationCompat.EXTRA_TEXT)).isEqualTo("Set the kitchen outlets")
        assertThat(manager.getNotificationChannel("schedule").name.toString()).isEqualTo("Schedule")
    }

    // T047b2: tapping the notification opens Field Log with the message back, for PushDestination.
    @Test
    fun tappingTheNotificationCarriesTheMessageBackToTheApp() {
        val data = mapOf("type" to "task_assigned", "companyId" to "c1", "taskId" to "t1", "jobId" to "j1", "title" to "Set the kitchen outlets", "jobName" to "Hale kitchen")
        val message = PushMessage.parse(data) ?: error("parse")
        notifier.show(message, data)
        val tap = shadowOf(shadowOf(manager).allNotifications.single().contentIntent).savedIntent
        assertThat(tap.component?.className).isEqualTo("com.kurtz.homerecord.fieldlog.MainActivity")
        assertThat(PushIntents.messageOf(tap)).isEqualTo(message)
        assertThat(PushIntents.messageOf(android.content.Intent(android.content.Intent.ACTION_MAIN))).isNull()
    }

    @Test
    fun aSecondPushAboutTheSameDayReplacesTheFirst() {
        val data = mapOf("type" to "schedule_changed", "companyId" to "c1", "date" to "2026-11-02", "jobId" to "j1", "jobName" to "Hale kitchen")
        val message = PushMessage.parse(data) ?: error("parse")
        notifier.show(message, data)
        notifier.show(message, data)
        val posted = shadowOf(manager).allNotifications.single()
        assertThat(posted.channelId).isEqualTo("schedule")
        assertThat(posted.extras.getString(NotificationCompat.EXTRA_TITLE)).isEqualTo("Tomorrow's schedule changed")
    }

    @Test
    fun nothingIsShownWithoutPermission() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val message = PushMessage.TimesheetApproved("c1", LocalDate.of(2026, 10, 26))
        assertThat(notifier.show(message, emptyMap())).isFalse()
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun wordingForEachKind() {
        assertThat(texts(PushMessage.TaskAssigned("c1", "", "", "t1", "")))
            .isEqualTo(PushText("New task for you", "Open Tasks to see it."))
        assertThat(texts(PushMessage.ScheduleChanged("c1", today, "j1", "Hale kitchen")).title).isEqualTo("Today's schedule changed")
        assertThat(texts(PushMessage.ScheduleChanged("c1", today.plusDays(3), "j1", "")).title).startsWith("Your schedule for Nov 4")
        assertThat(texts(PushMessage.ChangeOrderSigned("c1", "j1", "Hale kitchen", "co1", "Add a circuit", "Dana Hale")))
            .isEqualTo(PushText("Dana Hale signed a change order", "Add a circuit on Hale kitchen"))
        assertThat(texts(PushMessage.ChangeOrderSigned("c1", "j1", "", "co1", "", "")))
            .isEqualTo(PushText("Change order signed", "Open the job to see it."))
        assertThat(texts(PushMessage.TimesheetApproved("c1", LocalDate.of(2026, 10, 26))).body)
            .isEqualTo("Your hours for the week of Oct 26, 2026 are approved.")
    }
}
