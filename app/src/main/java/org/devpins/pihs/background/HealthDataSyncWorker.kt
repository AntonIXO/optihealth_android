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
        return@withContext try {
            Log.d(TAG, "Starting health data sync.")
            // Ensure repository is initialized if it's not done elsewhere on app start
            // healthRepository.initialize() // Consider if this is needed or done in Application class
            healthRepository.syncHealthData() // This function should handle its own errors internally if possible
            Log.d(TAG, "Health data sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during health data sync.", e)
            Result.failure()
        }
    }
}
