package org.devpins.pihs.health

import android.util.Log
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class MetricSource(
    val id: Long,
    val user_id: String,
    val source_identifier: String,
    val source_name: String,
    val source_type: String,
    val is_active: Boolean,
    val last_synced_at: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Singleton
class HealthRepository @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val healthDataTransformer: HealthDataTransformer,
    private val postgrest: Postgrest,
    private val auth: Auth,
    private val dataUploaderService: org.devpins.pihs.data.remote.DataUploaderService // Added DataUploaderService
) {
    // State flow to track sync status
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // State flow to track last sync time
    private val _lastSyncTime = MutableStateFlow<String?>(null)
    val lastSyncTime: StateFlow<String?> = _lastSyncTime.asStateFlow()

    // Flag to track if sync should be cancelled
    private var syncCancelled = false

    // State flow to track Health Connect availability
    val healthConnectAvailability: StateFlow<HealthConnectAvailability> = healthConnectManager.availability

    // State flow to track permissions
    val permissionsGranted: StateFlow<Boolean> = healthConnectManager.permissionsGranted

    // Initialize the repository
    suspend fun initialize() {
        Log.d("HealthConnect", "HealthRepository: Initializing")
        healthConnectManager.initialize()
        Log.d("HealthConnect", "HealthRepository: Initialized, availability: ${healthConnectAvailability.value}, permissions: ${permissionsGranted.value}")

        // Try to fetch the last sync time
        fetchLastSyncTime()
    }

    // Fetch the last sync time from the database
    private suspend fun fetchLastSyncTime() {
        try {
            val userId = auth.currentUserOrNull()?.id
            if (userId != null) {
                val metricSourceInfo = getMetricSourceInfo(userId)
                _lastSyncTime.value = metricSourceInfo.lastSyncedAt
                Log.d("HealthConnect", "HealthRepository: Fetched last sync time: ${_lastSyncTime.value}")
            }
        } catch (e: Exception) {
            Log.e("HealthConnect", "HealthRepository: Error fetching last sync time", e)
        }
    }

    // Get permission request contract
    fun getPermissionRequestContract(): androidx.activity.result.contract.ActivityResultContract<Set<String>, Set<String>> {
        Log.d("HealthConnect", "HealthRepository: Getting permission request contract")
        return healthConnectManager.getPermissionRequestContract()
    }

    // Get permissions to request
    fun getPermissionsToRequest(): Set<String> {
        Log.d("HealthConnect", "HealthRepository: Getting permissions to request")
        val permissions = healthConnectManager.getPermissionsToRequest()
        Log.d("HealthConnect", "HealthRepository: Permissions to request: $permissions")
        return permissions
    }

    // Handle permission result
    suspend fun handlePermissionResult(grantedPermissions: Set<String>) {
        Log.d("HealthConnect", "HealthRepository: Handling permission result: $grantedPermissions")
        healthConnectManager.handlePermissionResult(grantedPermissions)
        Log.d("HealthConnect", "HealthRepository: Permission result handled, permissions granted: ${permissionsGranted.value}")
    }

    // Get intent to open Health Connect settings
    fun getHealthConnectSettingsIntent(): android.content.Intent {
        Log.d("HealthConnect", "HealthRepository: Getting Health Connect settings intent")
        val intent = healthConnectManager.getHealthConnectSettingsIntent()
        Log.d("HealthConnect", "HealthRepository: Health Connect settings intent: $intent")
        return intent
    }

    // Function to cancel ongoing sync
    fun cancelSync() {
        if (_syncStatus.value is SyncStatus.Syncing) {
            Log.d("HealthConnect", "HealthRepository: Cancelling sync")
            syncCancelled = true
        }
    }

    // Sync health data with Supabase
    suspend fun syncHealthData() {
        try {
            Log.d("HealthConnect", "HealthRepository: Starting health data sync")
            _syncStatus.value = SyncStatus.Syncing
            syncCancelled = false

            // Check if Health Connect is available and permissions are granted
            Log.d("HealthConnect", "HealthRepository: Health Connect availability: ${healthConnectAvailability.value}")
            Log.d("HealthConnect", "HealthRepository: Permissions granted: ${permissionsGranted.value}")

            // Get the current user ID
            val userId = auth.currentUserOrNull()?.id
            if (userId == null) {
                // If we can't get the user ID, we should not proceed with the sync
                Log.e("HealthConnect", "HealthRepository: User ID is null, cannot proceed with sync")
                throw IllegalStateException("User not authenticated. Cannot sync health data.")
            }

            // Check if sync was cancelled
            if (syncCancelled) {
                Log.d("HealthConnect", "HealthRepository: Sync cancelled")
                _syncStatus.value = SyncStatus.Idle
                return
            }

            // Get information about the metric source to determine last sync time
            val metricSourceInfo = getMetricSourceInfo(userId)

            // Determine the start time for data fetching based on last sync time
            val startTime = if (metricSourceInfo.lastSyncedAt != null) {
                try {
                    // Parse the last synced timestamp
                    Instant.parse(metricSourceInfo.lastSyncedAt)
                } catch (e: Exception) {
                    Log.e("HealthConnect", "HealthRepository: Error parsing last_synced_at timestamp, using default", e)
                    Instant.now().minusSeconds(30 * 24 * 60 * 60L) // Default to 30 days ago
                }
            } else {
                // If no last sync time, default to 30 days ago
                Log.d("HealthConnect", "HealthRepository: No last sync time found, using default")
                Instant.now().minusSeconds(30 * 24 * 60 * 60L) // 30 days ago
            }

            Log.d("HealthConnect", "HealthRepository: Fetching data since $startTime")

            // Check if sync was cancelled
            if (syncCancelled) {
                Log.d("HealthConnect", "HealthRepository: Sync cancelled")
                _syncStatus.value = SyncStatus.Idle
                return
            }

            // Get health data from Health Connect since the last sync
            Log.d("HealthConnect", "HealthRepository: Getting health data from Health Connect")
            val healthData = healthConnectManager.getHealthDataSince(startTime)
            Log.d("HealthConnect", "HealthRepository: Got health data from Health Connect")

            // Check if sync was cancelled
            if (syncCancelled) {
                Log.d("HealthConnect", "HealthRepository: Sync cancelled")
                _syncStatus.value = SyncStatus.Idle
                return
            }

            // Transform health data to PIHS format
            Log.d("HealthConnect", "HealthRepository: Transforming health data to PIHS format")
            val pihsHealthData = healthDataTransformer.transformHealthData(healthData)
            Log.d("HealthConnect", "HealthRepository: Transformed health data to PIHS format")

            // Check if sync was cancelled
            if (syncCancelled) {
                Log.d("HealthConnect", "HealthRepository: Sync cancelled")
                _syncStatus.value = SyncStatus.Idle
                return
            }

            // Upload to Supabase
            Log.d("HealthConnect", "HealthRepository: Uploading health data to Supabase")
            // Pass the metricSourceInfo to the updated uploadToSupabase method
            uploadToSupabase(pihsHealthData, metricSourceInfo)
            Log.d("HealthConnect", "HealthRepository: Uploaded health data to Supabase")

            // Check if sync was cancelled
            if (syncCancelled) {
                Log.d("HealthConnect", "HealthRepository: Sync cancelled")
                _syncStatus.value = SyncStatus.Idle
                return
            }

            // Update the last synced timestamp
            updateLastSyncedTimestamp(metricSourceInfo.id)

            _syncStatus.value = SyncStatus.Success
            Log.d("HealthConnect", "HealthRepository: Health data sync completed successfully")
        } catch (e: Exception) {
            Log.e("HealthConnect", "HealthRepository: Error syncing health data", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
        } finally {
            syncCancelled = false
        }
    }

    // Sync health data with Supabase for a specific date range
    suspend fun syncHealthDataInRange(startTime: Instant, endTime: Instant) {
        try {
            Log.d("HealthConnect", "HealthRepository: Starting health data sync for range $startTime to $endTime")
            _syncStatus.value = SyncStatus.Syncing
            syncCancelled = false

            // Check if Health Connect is available and permissions are granted
            Log.d("HealthConnect", "HealthRepository: Health Connect availability: ${healthConnectAvailability.value}")
            Log.d("HealthConnect", "HealthRepository: Permissions granted: ${permissionsGranted.value}")

            // Get the current user ID
            val userId = auth.currentUserOrNull()?.id
            if (userId == null) {
                // If we can't get the user ID, we should not proceed with the sync
                Log.e("HealthConnect", "HealthRepository: User ID is null, cannot proceed with sync")
                throw IllegalStateException("User not authenticated. Cannot sync health data.")
            }

            // Check if sync was cancelled
            if (syncCancelled) {
                Log.d("HealthConnect", "HealthRepository: Sync cancelled")
                _syncStatus.value = SyncStatus.Idle
                return
            }

            // Get information about the metric source
            val metricSourceInfo = getMetricSourceInfo(userId)

            Log.d("HealthConnect", "HealthRepository: Fetching data from $startTime to $endTime")

            // Check if sync was cancelled
            if (syncCancelled) {
                Log.d("HealthConnect", "HealthRepository: Sync cancelled")
                _syncStatus.value = SyncStatus.Idle
                return
            }

            // Get health data from Health Connect for the specified range
            Log.d("HealthConnect", "HealthRepository: Getting health data from Health Connect")
            val healthData = healthConnectManager.getHealthDataBetween(startTime, endTime)
            Log.d("HealthConnect", "HealthRepository: Got health data from Health Connect")

            // Check if sync was cancelled
            if (syncCancelled) {
                Log.d("HealthConnect", "HealthRepository: Sync cancelled")
                _syncStatus.value = SyncStatus.Idle
                return
            }

            // Transform health data to PIHS format
            Log.d("HealthConnect", "HealthRepository: Transforming health data to PIHS format")
            val pihsHealthData = healthDataTransformer.transformHealthData(healthData)
            Log.d("HealthConnect", "HealthRepository: Transformed health data to PIHS format")

            // Check if sync was cancelled
            if (syncCancelled) {
                Log.d("HealthConnect", "HealthRepository: Sync cancelled")
                _syncStatus.value = SyncStatus.Idle
                return
            }

            // Upload to Supabase
            Log.d("HealthConnect", "HealthRepository: Uploading health data to Supabase")
            // Pass the metricSourceInfo to the updated uploadToSupabase method
            uploadToSupabase(pihsHealthData, metricSourceInfo)
            Log.d("HealthConnect", "HealthRepository: Uploaded health data to Supabase")

            // Check if sync was cancelled
            if (syncCancelled) {
                Log.d("HealthConnect", "HealthRepository: Sync cancelled")
                _syncStatus.value = SyncStatus.Idle
                return
            }

            // Update the last synced timestamp if the end time is now
            if (endTime.epochSecond >= Instant.now().minusSeconds(60).epochSecond) {
                updateLastSyncedTimestamp(metricSourceInfo.id)
            }

            _syncStatus.value = SyncStatus.Success
            Log.d("HealthConnect", "HealthRepository: Health data sync for range completed successfully")
        } catch (e: Exception) {
            Log.e("HealthConnect", "HealthRepository: Error syncing health data for range", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
        } finally {
            syncCancelled = false
        }
    }

    // Get information about the metric source
    private suspend fun getMetricSourceInfo(userId: String): MetricSourceInfo {
        try {
            // Try to find an existing Health Connect metric source
            val existingSources = postgrest["metric_sources"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("source_identifier", "health_connect_android")
                    }
                }
                .decodeList<MetricSource>()

            // If a source exists, return its info
            if (existingSources.isNotEmpty()) {
                val source = existingSources.first()
                Log.d("HealthConnect", "HealthRepository: Found existing metric source with ID: ${source.id}, last synced at: ${source.last_synced_at}")
                return MetricSourceInfo(source.id, source.last_synced_at)
            }

            // Otherwise, create a new metric source
            Log.d("HealthConnect", "HealthRepository: Creating new metric source for Health Connect")
            val newSource = buildJsonObject {
                put("user_id", userId)
                put("source_identifier", "health_connect_android")
                put("source_name", "Android Health Connect")
                put("source_type", "mobile_sync")
                put("is_active", true)
                put("last_synced_at", null)
            }

            val result = postgrest["metric_sources"].insert(newSource).decodeSingle<MetricSource>()
            Log.d("HealthConnect", "HealthRepository: Created new metric source with ID: ${result.id}")
            return MetricSourceInfo(result.id, null)
        } catch (e: Exception) {
            Log.e("HealthConnect", "HealthRepository: Error getting or creating metric source", e)
            throw e
        }
    }

    // Update the last synced timestamp for a metric source
    private suspend fun updateLastSyncedTimestamp(metricSourceId: Long) {
        try {
            val currentTime = Instant.now().toString()
            Log.d("HealthConnect", "HealthRepository: Updating last_synced_at to $currentTime for metric source $metricSourceId")

            val updateData = buildJsonObject {
                put("last_synced_at", currentTime)
            }

            postgrest["metric_sources"]
                .update(updateData) {
                    filter {
                        eq("id", metricSourceId)
                    }
                }

            // Update the last sync time in the StateFlow
            _lastSyncTime.value = currentTime

            Log.d("HealthConnect", "HealthRepository: Updated last_synced_at timestamp")
        } catch (e: Exception) {
            Log.e("HealthConnect", "HealthRepository: Error updating last_synced_at timestamp", e)
            // Don't throw the exception here, as we don't want to fail the sync if just the timestamp update fails
        }
    }

    // Data class to hold metric source information
    private data class MetricSourceInfo(
        val id: Long,
        val lastSyncedAt: String?
    )

    // Upload health data to Supabase
    private suspend fun uploadToSupabase(
        pihsHealthData: PIHSHealthData,
        metricSourceInfo: MetricSourceInfo // Changed to pass MetricSourceInfo
    ) {
        val dataPointsToUpload = mutableListOf<org.devpins.pihs.data.model.DataPoint>()

        // Transform applicable PIHSHealthData into DataPoint objects
        dataPointsToUpload.addAll(transformPihsToDataPoints(pihsHealthData, metricSourceInfo.id))

        if (dataPointsToUpload.isNotEmpty()) {
            Log.d("HealthConnect", "HealthRepository: Attempting to upload ${dataPointsToUpload.size} data points via DataUploaderService.")
            when (val result = dataUploaderService.uploadDataPoints(dataPointsToUpload)) {
                is org.devpins.pihs.data.remote.UploadResult.Success -> {
                    Log.i(
                        "HealthConnect",
                        "DataUploaderService success: ${result.response.message}, " +
                                "Received: ${result.response.receivedCount}, " +
                                "Inserted: ${result.response.insertedCount}"
                    )
                    // Potentially update UI or log specific counts if needed
                }
                is org.devpins.pihs.data.remote.UploadResult.Failure -> {
                    Log.e("HealthConnect", "DataUploaderService failure: ${result.errorMessage}", result.exception)
                    // Throw an exception to be caught by the calling sync function,
                    // which will set SyncStatus.Error
                    throw result.exception ?: RuntimeException("DataUploaderService failed: ${result.errorMessage}")
                }
            }
        } else {
            Log.d("HealthConnect", "HealthRepository: No new data points to upload via DataUploaderService.")
        }

        // --- Handle data types that go to the 'events' table (Exercise, Nutrition) using existing Postgrest logic ---
        // This part remains similar to the original uploadToSupabase, but only for event-like data.
        // The user_id and metric_source_id are still needed here.
        val userId = auth.currentUserOrNull()?.id
        if (userId == null) {
            Log.e("HealthConnect", "HealthRepository: User ID is null, cannot proceed with event data upload.")
            throw IllegalStateException("User not authenticated. Cannot sync event data.")
        }
        // Note: metricSourceInfo.id is the metric_source_id for health_connect_android.
        // If events need a different source_id, this needs adjustment. For now, assume they relate to the same source.

        // Upload exercise data to events table
        if (pihsHealthData.exercise.isNotEmpty()) {
            Log.d("HealthConnect", "HealthRepository: Uploading ${pihsHealthData.exercise.size} exercise records to events table (Postgrest)")
            pihsHealthData.exercise.forEach { exerciseRecord ->
                // Calculate duration in minutes
                val startInstant = Instant.parse(exerciseRecord.startTime)
                val endInstant = Instant.parse(exerciseRecord.endTime)
                val durationMinutes = (endInstant.epochSecond - startInstant.epochSecond) / 60

                // Create properties JSON object
                val properties = buildJsonObject {
                    put("calories", exerciseRecord.calories)
                    put("exerciseType", exerciseRecord.exerciseType)
                    // Add other relevant exercise properties if needed
                }

                val event = buildJsonObject {
                    put("user_id", userId)
                    put("event_name", exerciseRecord.title.ifEmpty { exerciseRecord.exerciseType })
                    put("start_timestamp", exerciseRecord.startTime)
                    put("end_timestamp", exerciseRecord.endTime)
                    put("duration_minutes", durationMinutes)
                    put("description", exerciseRecord.notes)
                    put("category", "exercise")
                    put("properties", properties)
                    // Consider adding metric_source_id if your 'events' table schema supports it
                    // put("metric_source_id", metricSourceInfo.id)
                }
                try {
                    postgrest["events"].insert(event)
                } catch (e: Exception) {
                    Log.e("HealthConnect", "HealthRepository: Error uploading exercise event for user $userId, event: $event", e)
                    // Continue with the next record
                }
            }
            Log.d("HealthConnect", "HealthRepository: Uploaded exercise records to events table (Postgrest)")
        }

        // Upload nutrition data to events table
        pihsHealthData.nutrition.forEach { nutritionRecord ->
            if (nutritionRecord.calories > 0 || nutritionRecord.protein > 0 || nutritionRecord.fat > 0 || nutritionRecord.carbs > 0) {
                val startInstant = Instant.parse(nutritionRecord.startTime)
                val endInstant = Instant.parse(nutritionRecord.endTime)
                val durationMinutes = (endInstant.epochSecond - startInstant.epochSecond) / 60

                val properties = buildJsonObject {
                    put("calories", nutritionRecord.calories)
                    put("protein", nutritionRecord.protein)
                    put("fat", nutritionRecord.fat)
                    put("carbs", nutritionRecord.carbs)
                }

                val event = buildJsonObject {
                    put("user_id", userId)
                    put("event_name", "Meal - ${nutritionRecord.name.ifEmpty { "Unspecified" }}")
                    put("start_timestamp", nutritionRecord.startTime)
                    put("end_timestamp", nutritionRecord.endTime)
                    put("duration_minutes", durationMinutes)
                    put("category", "diet")
                    put("properties", properties)
                     // Consider adding metric_source_id if your 'events' table schema supports it
                    // put("metric_source_id", metricSourceInfo.id)
                }
                try {
                    postgrest["events"].insert(event)
                } catch (e: Exception) {
                    Log.e("HealthConnect", "HealthRepository: Error uploading nutrition event for user $userId, event: $event", e)
                }
            }
        }
        Log.d("HealthConnect", "HealthRepository: Processed nutrition records for events table (Postgrest)")


        // Original content of uploadToSupabase is largely replaced or moved to transformPihsToDataPoints
        // and the event handling part above.
        try {
            Log.d("HealthConnect", "HealthRepository: Health data processing (points and events) complete.")

            // Get the current user ID
            val userId = auth.currentUserOrNull()?.id
            if (userId == null) {
                // If we can't get the user ID, we should not proceed with the sync
                Log.e("HealthConnect", "HealthRepository: User ID is null, cannot proceed with sync")
                throw IllegalStateException("User not authenticated. Cannot sync health data.")
            }
            Log.d("HealthConnect", "HealthRepository: User ID: $userId")

            // Get or create a metric source for Health Connect
            val metricSource = getOrCreateMetricSource(userId)
            val metricSourceId = metricSource.id
            Log.d("HealthConnect", "HealthRepository: Metric Source ID: $metricSourceId")

            // Upload each data type separately to appropriate tables

            // Upload weight data to data_points table
            if (pihsHealthData.weight.isNotEmpty()) {
                Log.d("HealthConnect", "HealthRepository: Uploading ${pihsHealthData.weight.size} weight records")
                pihsHealthData.weight.forEach { weightRecord ->
                    // Skip if weight is 0
                    if (weightRecord.weight > 0) {
                        val dataPoint = buildJsonObject {
                            put("user_id", userId)
                            put("metric_source_id", metricSourceId)
                            put("timestamp", weightRecord.time)
                            put("metric_name", "body_weight_mass_kg")
                            put("value_numeric", weightRecord.weight)
                            put("unit", "kg")
                        }
                        try {
                            postgrest["data_points"].insert(dataPoint)
                        } catch (e: Exception) {
                            Log.e("HealthConnect", "HealthRepository: Error uploading weight record for user $userId, data: $dataPoint", e)
                            // Continue with the next record
                        }
                    } else {
                        Log.d("HealthConnect", "HealthRepository: Skipping weight record with value 0")
                    }
                }
                Log.d("HealthConnect", "HealthRepository: Uploaded weight records to data_points table")
            }

            // Upload exercise data to events table
            if (pihsHealthData.exercise.isNotEmpty()) {
                Log.d("HealthConnect", "HealthRepository: Uploading ${pihsHealthData.exercise.size} exercise records")
                pihsHealthData.exercise.forEach { exerciseRecord ->
                    // Calculate duration in minutes
                    val startInstant = Instant.parse(exerciseRecord.startTime)
                    val endInstant = Instant.parse(exerciseRecord.endTime)
                    val durationMinutes = (endInstant.epochSecond - startInstant.epochSecond) / 60

                    // Create properties JSON object
                    val properties = buildJsonObject {
                        put("calories", exerciseRecord.calories)
                        put("exerciseType", exerciseRecord.exerciseType)
                    }

                    val event = buildJsonObject {
                        put("user_id", userId)
                        put("event_name", exerciseRecord.title.ifEmpty { exerciseRecord.exerciseType })
                        put("start_timestamp", exerciseRecord.startTime)
                        put("end_timestamp", exerciseRecord.endTime)
                        put("duration_minutes", durationMinutes)
                        put("description", exerciseRecord.notes)
                        put("category", "exercise")
                        put("properties", properties)
                    }
                    try {
                        postgrest["events"].insert(event)
                    } catch (e: Exception) {
                        Log.e("HealthConnect", "HealthRepository: Error uploading exercise event for user $userId, event: $event", e)
                        // Continue with the next record
                    }
                }
                Log.d("HealthConnect", "HealthRepository: Uploaded exercise records to events table")
            }

            // Upload other health metrics to data_points table
            uploadMetricsToDataPoints(pihsHealthData, userId, metricSourceId)

            Log.d("HealthConnect", "HealthRepository: Completed uploading health data to Supabase")
        } catch (e: Exception) {
            Log.e("HealthConnect", "HealthRepository: Error uploading to Supabase", e)
            throw e
        }
    }

    // New method to transform PIHSHealthData to List<DataPoint>
sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    object Success : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}
