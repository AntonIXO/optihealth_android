package org.devpins.pihs

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import org.devpins.pihs.background.HealthDataSyncWorker
import org.devpins.pihs.background.SoundLevelWorker // Added import
import org.devpins.pihs.background.UsageDataSyncWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Custom [Application] class for PIHS.
 * It implements [Configuration.Provider] to provide a custom [WorkManager] configuration,
 * enabling Hilt integration for workers.
 * It also schedules daily background synchronization tasks for health and usage data.
 */
@HiltAndroidApp
class PIHSApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val TAG = "PIHSApplication"

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO) // Optional: for WorkManager's own logs
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate - Scheduling daily workers")
        scheduleDailySyncWorkers()
    }

    private fun scheduleDailySyncWorkers() {
        val workManager = WorkManager.getInstance(applicationContext)

        // Constraints for the workers
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            // You could add other constraints like:
             .setRequiresBatteryNotLow(true)
             .setRequiresDeviceIdle(true) // For tasks that can wait until device is idle
            .build()

        // Schedule HealthDataSyncWorker
        val healthSyncRequest =
            PeriodicWorkRequestBuilder<HealthDataSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .addTag(HealthDataSyncWorker.WORK_NAME) // Optional: for easier identification
                .build()

        workManager.enqueueUniquePeriodicWork(
            HealthDataSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Or REPLACE if you want to update the worker if it changes
            healthSyncRequest
        )
        Log.d(TAG, "Enqueued ${HealthDataSyncWorker.WORK_NAME}")

        // Schedule UsageDataSyncWorker
        val usageSyncRequest =
            PeriodicWorkRequestBuilder<UsageDataSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS) // Use same delay or a slightly different one
                .addTag(UsageDataSyncWorker.WORK_NAME) // Optional
                .build()

        workManager.enqueueUniquePeriodicWork(
            UsageDataSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            usageSyncRequest
        )
        Log.d(TAG, "Enqueued ${UsageDataSyncWorker.WORK_NAME}")

        // Schedule SoundLevelWorker
        val soundWorkerConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            // .setRequiresBatteryNotLow(true) // Consider if needed
            .build()

        val soundLevelWorkerRequest =
            PeriodicWorkRequestBuilder<SoundLevelWorker>(30, TimeUnit.MINUTES)
                .setConstraints(soundWorkerConstraints)
                // No initial delay needed for frequent periodic task, unless specific timing is required.
                .addTag(SoundLevelWorker.WORK_NAME) // Optional
                .build()

        workManager.enqueueUniquePeriodicWork(
            SoundLevelWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Or REPLACE if worker definition changes often
            soundLevelWorkerRequest
        )
        Log.d(TAG, "Enqueued ${SoundLevelWorker.WORK_NAME}")
    }

    /**
     * Calculates the initial delay to schedule the worker for roughly 2-4 AM.
     */
    private fun calculateInitialDelay(): Long {
        val currentTimeMillis = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis
            // Set target time to 2 AM today
            set(Calendar.HOUR_OF_DAY, 2) // 2 AM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If 2 AM today has already passed, schedule for 2 AM tomorrow
        if (calendar.timeInMillis <= currentTimeMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Add a random component to spread out the load (e.g., between 2 AM and 4 AM)
        // This will be a random value between 0 and 2 hours (in milliseconds)
        val randomOffsetMillis = (Math.random() * 2 * 60 * 60 * 1000).toLong()
        
        val targetTimeMillis = calendar.timeInMillis + randomOffsetMillis
        val delayMillis = targetTimeMillis - currentTimeMillis

        Log.d(TAG, "Calculated initial delay: $delayMillis ms (approx ${(delayMillis / (1000.0 * 60.0 * 60.0))} hours)")
        return delayMillis
    }
}
