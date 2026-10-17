package com.kurtz.homerecord.housemanual.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kurtz.homerecord.core.reminders.ReminderChecker
import com.kurtz.homerecord.core.reminders.ReminderSchedule
import com.kurtz.homerecord.housemanual.HouseManualApp
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** AC-83: daily check that posts the due-tasks notification. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as HouseManualApp).container
        ReminderChecker(container.homeRepository, container.maintenanceRepository, AndroidReminderNotifier(applicationContext)).run()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily-reminders"

        /** Idempotent: keeps an existing schedule so the morning alignment is not reset on every launch. */
        fun schedule(context: Context) {
            val delay = ReminderSchedule.initialDelay(System.currentTimeMillis(), ZoneId.systemDefault())
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
