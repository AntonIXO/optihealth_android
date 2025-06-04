package org.devpins.pihs.background

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.devpins.pihs.stats.UsageStatsHelper
import java.time.LocalDate

@HiltWorker
class UsageDataSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val supabaseClient: SupabaseClient
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "UsageDataSyncWorker"
        const val WORK_NAME = "UsageDataDailySync"
        const val METRIC_SOURCE_ID_USAGE = "health_connect_android" // IMPORTANT: Replace with your actual ID
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting usage data sync.")

        // 1. Check for Usage Stats Permission
        if (!UsageStatsHelper.hasUsageStatsPermission(appContext)) {
            Log.w(TAG, "Usage stats permission not granted. Cannot sync.")
            // Optionally, send a notification to the user to grant permission
            // For a daily background task, direct UI interaction like opening settings isn't feasible.
            // The user must grant it manually when they open the app.
            return@withContext Result.failure() // Or Result.success() if you don't want retries for this reason
        }

        // 2. Get Supabase User ID
        val userId = supabaseClient.auth.currentUserOrNull()?.id
        if (userId == null) {
            Log.w(TAG, "User not logged in. Cannot sync usage data.")
            return@withContext Result.failure() // Or Result.success()
        }

        // 3. Get and Process Usage Stats for Yesterday
        // Syncing for "yesterday" ensures a full day's data is available when the worker runs (e.g., early morning)
        val dateToSync = LocalDate.now().minusDays(1)
        val dataPoints = UsageStatsHelper.getAndProcessUsageStats(
            context = appContext,
            date = dateToSync,
            userId = userId,
            metricSourceId = METRIC_SOURCE_ID_USAGE
        )

        if (dataPoints.isEmpty()) {
            Log.i(TAG, "No usage data points to log for $dateToSync.")
            return@withContext Result.success()
        }

        // 4. Log Data to Supabase
        return@withContext try {
            UsageStatsHelper.logDataToSupabase(dataPoints, supabaseClient)
            Log.d(TAG, "Successfully logged ${dataPoints.size} usage data points for $dateToSync.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error logging usage data to Supabase for $dateToSync.", e)
            Result.failure()
        }
    }
}
