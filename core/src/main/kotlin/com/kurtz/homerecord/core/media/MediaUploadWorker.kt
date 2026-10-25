package com.kurtz.homerecord.core.media

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Runs the upload queue when the network is up; WorkManager restarts it after process death and reboots (AC-F54). */
class MediaUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uploader = provider?.invoke() ?: return Result.success()
        uploader.recover()
        val pass = uploader.runPending()
        return if (pass.retryAt != null) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "media-upload"

        /** Set by the app container before anything is queued. */
        @Volatile var provider: (() -> MediaUploader)? = null

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<MediaUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
