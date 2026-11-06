package com.kurtz.homerecord.housemanual.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kurtz.homerecord.housemanual.HomeRecordApplication
import com.kurtz.homerecord.housemodel.reminders.DailyReminderCheck
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The daily reminder check (AC-C50): once a day, starting at the next 08:00 local, one summary notification of what is
 * due across the homes on this phone. WorkManager keeps it across reboots and process death.
 */
class ReminderCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? HomeRecordApplication ?: return Result.success()
        app.container.reminderCheck.run()
        return Result.success()
    }

    companion object {
        const val NAME = "reminder-check"

        /** Called at every app start; KEEP leaves a scheduled check alone, so the 08:00 slot does not move. */
        fun schedule(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
            val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(DailyReminderCheck.delayUntilNext(now).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
