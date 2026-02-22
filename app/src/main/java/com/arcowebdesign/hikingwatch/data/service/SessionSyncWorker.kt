package com.arcowebdesign.hikingwatch.data.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.arcowebdesign.hikingwatch.data.repository.HikingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SessionSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val hikingRepository: HikingRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Export recent completed sessions as GPX if not done yet.
            // In a full implementation this would sync to a companion phone app
            // via the Wearable Data Layer API. Gracefully degrades if phone is offline.
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "session_sync_work"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SessionSyncWorker>(
                repeatInterval = 30,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
