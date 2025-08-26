package org.devpins.pihs.background

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.devpins.pihs.stats.UsageStatsHelper
import org.devpins.pihs.settings.SettingsKeys
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Represents a metric source entry from the Supabase database.
 * This data class is used to decode responses when querying or creating metric sources.
 *
 * @property id The unique identifier of the metric source.
 * @property user_id The ID of the user this metric source belongs to.
 * @property source_identifier A unique string identifying the type of source (e.g., "android_app_usage_daily_v1").
 * @property source_name A human-readable name for the source (e.g., "Android App Usage (Daily)").
 * @property is_active Boolean indicating if this source is currently active.
 * @property last_synced_at Optional string representing the last date ("YYYY-MM-DD") data was synced for this source.
 * @property created_at Optional string representing the creation timestamp of this record.
 * @property updated_at Optional string representing the last update timestamp of this record.
 */
@Serializable
 data class MetricSource(
    val id: Long,
    val user_id: String,
    val source_identifier: String,
    val source_name: String,
    val is_active: Boolean,
    val last_synced_at: String? = null, // Stores date as "YYYY-MM-DD"
    val created_at: String? = null,
    val updated_at: String? = null
)

/**
 * A [CoroutineWorker] that periodically syncs daily app usage data to Supabase.
 * It ensures that usage statistics permission is granted, retrieves data using [UsageStatsHelper],
 * and manages a `metric_sources` entry in Supabase to track the last sync date for this data type.
 * This worker is managed by Hilt and intended for daily execution.
 *
 * @param appContext The application context, injected by Hilt.
 * @param workerParams Parameters for configuring the worker, injected by Hilt.
 * @param supabaseClient The [SupabaseClient] for interacting with the backend.
 */
@HiltWorker
class UsageDataSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val supabaseClient: SupabaseClient
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "UsageDataSyncWorker"
        const val WORK_NAME = "UsageDataDailySync"
        // IMPORTANT: This is a fixed identifier for this specific data source type
        private const val USAGE_SOURCE_IDENTIFIER = "android_app_usage_daily_v1"
        private const val USAGE_SOURCE_NAME = "Android App Usage (Daily)"
        // This METRIC_SOURCE_ID_USAGE is now effectively replaced by the DB ID of the metric_source row
        // but UsageStatsHelper still needs a string. We will use the DB ID as a string.
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val attempt = runAttemptCount
        Log.i(TAG, "doWork start | id=$id attempt=$attempt")

        if (!UsageStatsHelper.hasUsageStatsPermission(appContext)) {
            Log.w(TAG, "Usage stats permission not granted. Cannot sync.")
            return@withContext Result.failure()
        }

        val userId = supabaseClient.auth.currentUserOrNull()?.id
        if (userId == null) {
            Log.w(TAG, "User not logged in. Cannot sync usage data.")
            return@withContext Result.failure()
        }

        val (metricSourceDbId, lastEffectivelySyncedDate) = getOrCreateUsageMetricSource(userId, supabaseClient)
        if (metricSourceDbId == -1L) {
            Log.e(TAG, "Failed to get or create metric source for user $userId. Will retry later.")
            return@withContext Result.retry()
        }

        val dateToSync = LocalDate.now().minusDays(1)

        if (lastEffectivelySyncedDate != null && !lastEffectivelySyncedDate.isBefore(dateToSync)) {
            Log.i(TAG, "Usage data for $dateToSync (or newer) already synced on $lastEffectivelySyncedDate. Skipping.")
            return@withContext Result.success()
        }

        Log.d(TAG, "Proceeding to sync usage data for $dateToSync. Last synced date: $lastEffectivelySyncedDate")

        val dataPoints = UsageStatsHelper.getAndProcessUsageStats(
            context = appContext,
            date = dateToSync,
            userId = userId,
            metricSourceId = metricSourceDbId.toString() // Use the DB ID as the metricSourceId string
        )

        if (dataPoints.isEmpty()) {
            Log.i(TAG, "No usage data points to log for $dateToSync.")
            // Still update last_synced_at for this date to avoid re-checking an empty day
            updateLastUsageSyncedTimestamp(metricSourceDbId, dateToSync, supabaseClient)
            // Record last successful sync time even if there was nothing to upload
            val prefs = applicationContext.getSharedPreferences(SettingsKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
            prefs.edit().putLong(SettingsKeys.KEY_LAST_USAGE_SYNC_AT, System.currentTimeMillis()).apply()
            val elapsed = System.currentTimeMillis() - start
            Log.i(TAG, "doWork success (empty set) | id=$id attempt=$attempt elapsedMs=$elapsed")
            return@withContext Result.success()
        }

        return@withContext try {
            UsageStatsHelper.logDataToSupabase(dataPoints, supabaseClient)
            Log.d(TAG, "Successfully logged ${dataPoints.size} usage data points for $dateToSync.")
            updateLastUsageSyncedTimestamp(metricSourceDbId, dateToSync, supabaseClient)
            val prefs = applicationContext.getSharedPreferences(SettingsKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
            prefs.edit().putLong(SettingsKeys.KEY_LAST_USAGE_SYNC_AT, System.currentTimeMillis()).apply()
            val elapsed = System.currentTimeMillis() - start
            Log.i(TAG, "doWork success | id=$id attempt=$attempt elapsedMs=$elapsed points=${dataPoints.size}")
            Result.success()
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            Log.e(TAG, "Error logging usage data to Supabase for $dateToSync | id=$id attempt=$attempt elapsedMs=$elapsed", e)
            Result.retry()
        }
    }

    private suspend fun getOrCreateUsageMetricSource(userId: String, client: SupabaseClient): Pair<Long, LocalDate?> {
        try {
            val existingSources = client.postgrest.from("metric_sources")
                .select(Columns.list("id", "last_synced_at")) {
                    filter {
                        eq("user_id", userId)
                        eq("source_identifier", USAGE_SOURCE_IDENTIFIER)
                    }
                }.decodeList<MetricSource>() // Expecting a simplified MetricSource for this decode

            if (existingSources.isNotEmpty()) {
                val source = existingSources.first()
                val lastSyncedDate = source.last_synced_at?.let {
                    try {
                        LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) // Expects "YYYY-MM-DD"
                    } catch (e: DateTimeParseException) {
                        Log.w(TAG, "Failed to parse last_synced_at date: ${source.last_synced_at}", e)
                        null
                    }
                }
                Log.d(TAG, "Found existing metric source. ID: ${source.id}, Last Synced At String: ${source.last_synced_at}, Parsed Date: $lastSyncedDate")
                return Pair(source.id, lastSyncedDate)
            } else {
                Log.d(TAG, "No existing metric source found for $USAGE_SOURCE_IDENTIFIER. Creating new one.")
                val newSource = buildJsonObject {
                        put("user_id", userId)
                        put("source_identifier", USAGE_SOURCE_IDENTIFIER)
                        put("source_name", USAGE_SOURCE_NAME)
                        put("is_active", true)
                        put("last_synced_at", null as String?) // Explicitly null
                    }
                // Use the local MetricSource data class for decoding
                val result = client.postgrest["metric_sources"].insert(newSource).decodeSingle<MetricSource>()
                Log.d(TAG, "Created new metric source with ID: ${result.id}")
                return Pair(result.id, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getOrCreateUsageMetricSource for user $userId", e)
            return Pair(-1L, null) // Indicate error (transient or server issue). Caller will retry.
        }
    }

    private suspend fun updateLastUsageSyncedTimestamp(metricSourceDbId: Long, dateSynced: LocalDate, client: SupabaseClient) {
        try {
            val dateString = dateSynced.format(DateTimeFormatter.ISO_LOCAL_DATE) // "YYYY-MM-DD"
            client.postgrest.from("metric_sources")
                .update(
                    value = buildJsonObject { put("last_synced_at", dateString) }
                ) {
                    filter { eq("id", metricSourceDbId) }
                }
            Log.d(TAG, "Successfully updated last_synced_at to $dateString for metric source ID $metricSourceDbId")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last_synced_at for metric source ID $metricSourceDbId", e)
        }
    }
}
