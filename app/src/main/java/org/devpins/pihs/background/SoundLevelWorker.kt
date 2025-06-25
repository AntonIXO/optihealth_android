package org.devpins.pihs.background

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest // For potential direct logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.devpins.pihs.sound.SoundLevelManager
import org.devpins.pihs.stats.PihsDataPoint // Ensure this path is correct
import java.util.UUID
import io.github.jan.supabase.postgrest.from

@HiltWorker
class SoundLevelWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val supabaseClient: SupabaseClient,
    private val auth: Auth
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SoundLevelWorker starting.")

        val userId = auth.currentUserOrNull()?.id
        if (userId == null) {
            Log.e(TAG, "User not authenticated. Cannot perform sound level work.")
            return Result.failure()
        }

        if (!SoundLevelManager.hasRecordAudioPermission(appContext)) {
            Log.w(TAG, "RECORD_AUDIO permission not granted. SoundLevelWorker cannot proceed.")
            // Depending on policy, this could be Result.success() to avoid retries if permission is permanently denied.
            // For now, failure indicates the work could not be completed as expected.
            return Result.failure()
        }

        // Placeholder: Get or create metric source ID
        val metricSourceId = getOrCreatePeriodicSoundMetricSource(userId, supabaseClient)
        if (metricSourceId == null) {
            Log.e(TAG, "Failed to get or create a metric source ID for sound level.")
            return Result.failure()
        }
        Log.d(TAG, "Using sound metric source ID: $metricSourceId")

        var success = false
        // Perform sound level measurement using a callback and withContext for suspension
        withContext(Dispatchers.IO) { // Ensure measureSoundLevel's callback logic is handled correctly
            SoundLevelManager.measureSoundLevel(appContext) { soundLevel ->
                if (soundLevel != null) {
                    Log.i(TAG, "Successfully measured sound level: $soundLevel dB")
                    val timestamp = SoundLevelManager.getCurrentUTCTimestamp()
                    val dataPoint = SoundLevelManager.formatSoundData(
                        timestamp = timestamp,
                        soundLevel = soundLevel,
                        eventType = SoundLevelManager.METRIC_NAME_PERIODIC, // Using periodic metric name
                        userId = userId,
                        metricSourceId = metricSourceId
                    )

                    // Placeholder: Log data to Supabase
//                    logDataToSupabase(dataPoint, supabaseClient.from("pihs_data_points")) // Pass Postgrest for example
                    success = true
                } else {
                    Log.e(TAG, "Failed to measure sound level.")
                    success = false
                }
            }
        }

        // The callback nature of measureSoundLevel means 'success' might not be set immediately
        // This is a simplification. A more robust way would be to use suspendCancellableCoroutine
        // or ensure measureSoundLevel itself is a suspend function.
        // For this placeholder, we'll assume the callback completes before this check.
        // This part of the code needs careful review if measureSoundLevel is truly async with a callback.
        // However, the current SoundLevelManager.measureSoundLevel calls callback(0.0) synchronously.

        return if (success) {
            Log.d(TAG, "SoundLevelWorker completed successfully.")
            Result.success()
        } else {
            Log.e(TAG, "SoundLevelWorker failed to complete its task (measurement or logging).")
            Result.failure()
        }
    }

    // Placeholder function to get/create metric source ID
    private suspend fun getOrCreatePeriodicSoundMetricSource(userId: String, client: SupabaseClient): String? {
        // In a real implementation, this would query Supabase for an existing source
        // with SOUND_LEVEL_SOURCE_IDENTIFIER for this user. If not found, create it.
        // For now, returning a dummy or newly generated UUID.
        Log.d(TAG, "Placeholder: getOrCreatePeriodicSoundMetricSource for user $userId with identifier $SOUND_LEVEL_SOURCE_IDENTIFIER and name $SOUND_LEVEL_SOURCE_NAME")
        // Example:
        // val existing = client.from("metric_sources").select { filter { eq("user_id", userId); eq("source_identifier", SOUND_LEVEL_SOURCE_IDENTIFIER) } }.decodeList<MetricSource>().firstOrNull()
        // if (existing != null) return existing.id
        // val newSource = client.from("metric_sources").insert(MetricSource(user_id = userId, source_identifier = SOUND_LEVEL_SOURCE_IDENTIFIER, source_name = SOUND_LEVEL_SOURCE_NAME, ...)).decodeSingle<MetricSource>()
        // return newSource.id
        return UUID.randomUUID().toString() // Placeholder
    }

    // Placeholder function to log data to Supabase
    private suspend fun logDataToSupabase(dataPoint: PihsDataPoint, table: Postgrest) {
//        try {
//            Log.i(TAG, "Logging PihsDataPoint to Supabase: ${dataPoint.metricName} - ${dataPoint.metricValue}")
//            // table.insert(dataPoint) // Actual call
//            Log.d(TAG, "Placeholder: Supabase logging complete for ${dataPoint.metricName}.")
//        } catch (e: Exception) {
//            Log.e(TAG, "Error logging data to Supabase: ${e.message}", e)
//            // Rethrow or handle as per error policy for the worker
//        }
        Log.i(TAG, "zaglishka")
    }

    companion object {
        const val TAG = "SoundLevelWorker"
        const val WORK_NAME = "SoundLevelPeriodicSync" // Unique name for this worker
        const val SOUND_LEVEL_SOURCE_IDENTIFIER = "android_sound_level_periodic_v1"
        const val SOUND_LEVEL_SOURCE_NAME = "Android Sound Level (Periodic)"
    }
}
