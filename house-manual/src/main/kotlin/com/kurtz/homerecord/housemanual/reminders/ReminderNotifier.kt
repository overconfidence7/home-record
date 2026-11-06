package com.kurtz.homerecord.housemanual.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.reminders.ReminderDigest
import com.kurtz.homerecord.housemodel.reminders.ReminderNotices

/** What the morning notification says, from strings.xml (voice.md). */
object ReminderNoticeText {
    fun title(digest: ReminderDigest, res: Resources): String =
        res.getQuantityString(R.plurals.reminder_notice_title, digest.count, digest.count)

    fun body(digest: ReminderDigest, res: Resources): String {
        val named = digest.titles.joinToString(res.getString(R.string.reminder_notice_join))
        return if (digest.more > 0) res.getQuantityString(R.plurals.reminder_notice_more, digest.more, named, digest.more) else named
    }
}

/**
 * Posts the one reminder notification on its own channel (AC-C50); tapping it opens HM-Reminders. Quietly does nothing
 * when the person has not allowed notifications.
 */
class ReminderNotifier(private val context: Context) : ReminderNotices {
    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.reminder_channel_desc)
            },
        )
    }

    override fun show(digest: ReminderDigest) {
        if (!canNotify()) return
        ensureChannel()
        val res = context.resources
        val body = ReminderNoticeText.body(digest, res)
        val open = PendingIntent.getActivity(
            context, ID, ReminderEntry.intent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ReminderNoticeText.title(digest, res))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setNumber(digest.count)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(TAG, ID, notification)
        } catch (e: SecurityException) {
            // Permission taken away between the check and the post: nothing to show.
        }
    }

    override fun clear() {
        NotificationManagerCompat.from(context).cancel(TAG, ID)
    }

    private fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL = "reminders"
        private const val TAG = "reminders"
        private const val ID = 8
    }
}
