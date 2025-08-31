package org.devpins.pihs.background

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.devpins.pihs.health.HealthRepository
import org.devpins.pihs.settings.SettingsKeys

/**
 * A [CoroutineWorker] responsible for periodically syncing health data from Health Connect
 * via the [HealthRepository] to a remote backend.
 * This worker is managed by Hilt and is intended to be run daily.
 *
 * @param appContext The application context provided by Hilt.
 * @param workerParams Parameters to configure the worker, provided by Hilt.
 * @param healthRepository The repository used to access and sync health data.
 */
@HiltWorker
class HealthDataSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthRepository: HealthRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "HealthDataSyncWorker"
        const val WORK_NAME = "HealthDataDailySync"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val attempt = runAttemptCount
        Log.i(TAG, "doWork start | id=$id attempt=$attempt")
        return@withContext try {
            // Run sync; HealthRepository now throws on error so we only reach here on success.
            healthRepository.syncHealthData()
            // Record last successful sync time
            val prefs = applicationContext.getSharedPreferences(SettingsKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
            prefs.edit().putLong(SettingsKeys.KEY_LAST_HEALTH_SYNC_AT, System.currentTimeMillis()).apply()
            val elapsed = System.currentTimeMillis() - start
            Log.i(TAG, "doWork success | id=$id attempt=$attempt elapsedMs=$elapsed")
            Result.success()
        } catch (e: IllegalStateException) {
            val elapsed = System.currentTimeMillis() - start
            Log.w(TAG, "doWork permanent failure (illegal state) | id=$id attempt=$attempt elapsedMs=$elapsed: ${e.message}")
            // Do not retry on permanent conditions (e.g., user not logged in)
            Result.failure()
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            Log.e(TAG, "doWork transient error | id=$id attempt=$attempt elapsedMs=$elapsed", e)
            // Treat as transient to leverage backoff retries
            Result.retry()
        }
    }
}
