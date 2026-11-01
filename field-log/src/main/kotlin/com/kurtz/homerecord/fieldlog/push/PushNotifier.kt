package com.kurtz.homerecord.fieldlog.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kurtz.homerecord.core.push.PushChannel
import com.kurtz.homerecord.core.push.PushDay
import com.kurtz.homerecord.core.push.PushMessage
import com.kurtz.homerecord.core.push.pushDay
import com.kurtz.homerecord.fieldlog.MainActivity
import com.kurtz.homerecord.fieldlog.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What a notification says; built from strings.xml so every word follows voice.md. */
data class PushText(val title: String, val body: String)

object PushTexts {
    fun of(message: PushMessage, today: LocalDate, res: Resources): PushText = when (message) {
        is PushMessage.TaskAssigned -> PushText(
            if (message.jobName.isBlank()) res.getString(R.string.push_task_title_no_job) else res.getString(R.string.push_task_title, message.jobName),
            message.title.ifBlank { res.getString(R.string.push_task_body_untitled) },
        )
        is PushMessage.ScheduleChanged -> PushText(
            when (pushDay(message.date, today)) {
                PushDay.Today -> res.getString(R.string.push_schedule_title_today)
                PushDay.Tomorrow -> res.getString(R.string.push_schedule_title_tomorrow)
                PushDay.Other -> res.getString(R.string.push_schedule_title_date, date(message.date))
            },
            res.getString(R.string.push_schedule_body),
        )
        is PushMessage.ChangeOrderSigned -> PushText(
            if (message.signedBy.isBlank()) res.getString(R.string.push_change_order_title)
            else res.getString(R.string.push_change_order_title_by, message.signedBy),
            when {
                message.title.isBlank() && message.jobName.isBlank() -> res.getString(R.string.push_change_order_body_bare)
                message.jobName.isBlank() -> message.title
                message.title.isBlank() -> message.jobName
                else -> res.getString(R.string.push_change_order_body, message.title, message.jobName)
            },
        )
        is PushMessage.TimesheetApproved -> PushText(
            res.getString(R.string.push_timesheet_title),
            res.getString(R.string.push_timesheet_body, date(message.weekStart)),
        )
    }

    private fun date(day: LocalDate): String = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

/**
 * Shows a push as a notification on its own channel (AC-F36, AC-F42). Quietly does nothing when the person has not
 * allowed notifications. Tapping it opens Field Log with the message's data as extras; [PushDestination] picks the screen.
 */
class PushNotifier(private val context: Context, private val today: () -> LocalDate = LocalDate::now) {
    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            PushChannel.entries.map { channel ->
                val (name, description) = channelText(channel)
                NotificationChannel(channel.id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    this.description = context.getString(description)
                }
            },
        )
    }

    /** Returns true when the notification was posted. */
    fun show(message: PushMessage, data: Map<String, String>): Boolean {
        if (!canNotify()) return false
        ensureChannels()
        val text = PushTexts.of(message, today(), context.resources)
        val open = PushIntents.put(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            data,
        )
        val id = message.key.hashCode()
        val pending = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, message.channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(TAG, id, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun channelText(channel: PushChannel): Pair<Int, Int> = when (channel) {
        PushChannel.Tasks -> R.string.push_channel_tasks to R.string.push_channel_tasks_desc
        PushChannel.Schedule -> R.string.push_channel_schedule to R.string.push_channel_schedule_desc
        PushChannel.ChangeOrders -> R.string.push_channel_change_orders to R.string.push_channel_change_orders_desc
        PushChannel.Timesheets -> R.string.push_channel_timesheets to R.string.push_channel_timesheets_desc
    }

    companion object {
        const val ACTION_OPEN_PUSH = "com.kurtz.homerecord.fieldlog.OPEN_PUSH"
        const val EXTRA_PREFIX = "push."
        private const val TAG = "push"
    }
}
