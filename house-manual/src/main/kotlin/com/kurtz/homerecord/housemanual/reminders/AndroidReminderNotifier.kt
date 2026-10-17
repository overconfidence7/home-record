package com.kurtz.homerecord.housemanual.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kurtz.homerecord.core.model.MaintenanceTask
import com.kurtz.homerecord.core.reminders.ReminderNotifier
import com.kurtz.homerecord.housemanual.MainActivity
import com.kurtz.homerecord.housemanual.R

/** AC-83: posts one notification for the due tasks; tapping it opens HM-Reminders on the first one. */
class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {
    override fun notifyDue(tasks: List<MaintenanceTask>) {
        val first = tasks.firstOrNull() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_REMINDERS)
            .putExtra(MainActivity.EXTRA_TASK_ID, first.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (tasks.size == 1) first.title else context.getString(R.string.reminders_notification_many, first.title, tasks.size - 1)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_emergency)
            .setContentTitle(context.getString(R.string.reminders_notification_title))
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "reminders"
        const val NOTIFICATION_ID = 1001
    }
}
