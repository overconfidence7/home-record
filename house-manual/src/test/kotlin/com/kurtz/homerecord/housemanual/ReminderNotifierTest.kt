package com.kurtz.homerecord.housemanual

import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.reminders.ReminderEntry
import com.kurtz.homerecord.housemanual.reminders.ReminderNoticeText
import com.kurtz.homerecord.housemanual.reminders.ReminderNotifier
import com.kurtz.homerecord.housemodel.reminders.ReminderDigest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// T072c (AC-C50, AC-H12): the one 08:00 summary notification and its deep link to HM-Reminders.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReminderNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Test @AcceptanceCriterion("AC-C50")
    fun theSummaryNamesUpToThreeAndCountsTheRest() {
        val res = context.resources
        val one = ReminderDigest(1, listOf("Replace the furnace filter"))
        assertThat(ReminderNoticeText.title(one, res)).isEqualTo("1 reminder due")
        assertThat(ReminderNoticeText.body(one, res)).isEqualTo("Replace the furnace filter")
        val five = ReminderDigest(5, listOf("Replace the furnace filter", "Clean the dryer vent", "Flush the water heater"))
        assertThat(ReminderNoticeText.title(five, res)).isEqualTo("5 reminders due")
        assertThat(ReminderNoticeText.body(five, res))
            .isEqualTo("Replace the furnace filter, Clean the dryer vent, Flush the water heater and 2 more")
    }

    @Test @AcceptanceCriterion("AC-H12")
    fun theNotificationOpensReminders() {
        shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val notifier = ReminderNotifier(context)
        notifier.show(ReminderDigest(2, listOf("Replace the furnace filter", "Test smoke alarms")))

        val posted = shadowOf(manager).allNotifications
        assertThat(posted).hasSize(1)
        val notification = posted.single()
        assertThat(notification.channelId).isEqualTo(ReminderNotifier.CHANNEL)
        val opened: Intent = shadowOf(notification.contentIntent).savedIntent
        assertThat(opened.component?.className).isEqualTo(MainActivity::class.java.name)
        assertThat(ReminderEntry.wants(opened)).isTrue()

        // A second morning replaces it rather than stacking; nothing due clears it
        notifier.show(ReminderDigest(1, listOf("Test smoke alarms")))
        assertThat(shadowOf(manager).allNotifications).hasSize(1)
        notifier.clear()
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun withoutThePermissionNothingIsPosted() {
        shadowOf(context).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        ReminderNotifier(context).show(ReminderDigest(1, listOf("Test smoke alarms")))
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun aMaestroStringArgumentAlsoOpensReminders() {
        assertThat(ReminderEntry.wants(Intent().putExtra(ReminderEntry.EXTRA, "true"))).isTrue()
        assertThat(ReminderEntry.wants(Intent())).isFalse()
    }
}
