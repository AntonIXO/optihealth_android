package org.devpins.pihs.background

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit
import org.devpins.pihs.settings.SettingsKeys

object BackgroundSyncController {
    private const val TAG = "BackgroundSyncController"

    fun enable(context: Context) {
        Log.d(TAG, "Enabling background sync: scheduling workers")
        schedulePeriodicSyncWorkers(context)
    }

    fun disable(context: Context) {
        Log.d(TAG, "Disabling background sync: canceling workers")
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(HealthDataSyncWorker.WORK_NAME)
        wm.cancelUniqueWork(UsageDataSyncWorker.WORK_NAME)
    }

    /**
     * Schedule periodic sync workers based on the user-configured interval (in minutes).
     * Ensures minimum interval of 15 minutes as required by WorkManager.
     *
     * @param context The application context.
     * @param policy The conflict policy to use (default REPLACE).
     *               Use [ExistingPeriodicWorkPolicy.REPLACE] when settings change.
     *               Use [ExistingPeriodicWorkPolicy.KEEP] on app startup to avoid resetting the schedule.
     */
    fun schedulePeriodicSyncWorkers(
        context: Context,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE
    ) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val prefs = context.getSharedPreferences(SettingsKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val configuredMinutes = prefs.getInt(SettingsKeys.KEY_SYNC_INTERVAL_MINUTES, 24 * 60)
        val repeatMinutes = configuredMinutes.coerceAtLeast(15) // WorkManager minimum

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            // Do NOT require device idle; this severely restricts execution and often prevents runs
            .build()

        val initialDelayMillis = calculateInitialDelay(repeatMinutes)
        Log.i(
            TAG,
            "Scheduling periodic sync | configured=$configuredMinutes min, repeat=$repeatMinutes min, initialDelay=${initialDelayMillis}ms, constraints=[network=CONNECTED,batteryNotLow=true]"
        )

        val healthSyncRequest = PeriodicWorkRequestBuilder<HealthDataSyncWorker>(repeatMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(HealthDataSyncWorker.WORK_NAME)
            .build()
        Log.d(TAG, "Enqueueing HealthDataSyncWorker id=${healthSyncRequest.id} policy=REPLACE name=${HealthDataSyncWorker.WORK_NAME}")

        workManager.enqueueUniquePeriodicWork(
            HealthDataSyncWorker.WORK_NAME,
            policy,
            healthSyncRequest
        )

        val usageSyncRequest = PeriodicWorkRequestBuilder<UsageDataSyncWorker>(repeatMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(UsageDataSyncWorker.WORK_NAME)
            .build()
        Log.d(TAG, "Enqueueing UsageDataSyncWorker id=${usageSyncRequest.id} policy=REPLACE name=${UsageDataSyncWorker.WORK_NAME}")

        workManager.enqueueUniquePeriodicWork(
            UsageDataSyncWorker.WORK_NAME,
            policy,
            usageSyncRequest
        )
    }

    private fun calculateInitialDelay(intervalMinutes: Int): Long {
        val currentTimeMillis = System.currentTimeMillis()
        // For daily or longer intervals, schedule around 2 AM with some randomization (existing behavior)
        if (intervalMinutes >= 24 * 60) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = currentTimeMillis
                set(Calendar.HOUR_OF_DAY, 2)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (calendar.timeInMillis <= currentTimeMillis) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            val randomOffsetMillis = (Math.random() * 2 * 60 * 60 * 1000).toLong() // up to +2h
            val targetTimeMillis = calendar.timeInMillis + randomOffsetMillis
            return targetTimeMillis - currentTimeMillis
        }
        // For shorter intervals, randomize the initial delay up to 10% of the interval to avoid thundering herd
        val maxSkewMillis = (intervalMinutes * 60_000L) / 10L
        return (Math.random() * maxSkewMillis).toLong()
    }
}
