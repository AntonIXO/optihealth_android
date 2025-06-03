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
    private val auth: Auth
) {
    // State flow to track sync status
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // State flow to track last sync time
    private val _lastSyncTime = MutableStateFlow<String?>(null)
    val lastSyncTime: StateFlow<String?> = _lastSyncTime.asStateFlow()

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

    // Sync health data with Supabase
    suspend fun syncHealthData() {
        try {
            Log.d("HealthConnect", "HealthRepository: Starting health data sync")
            _syncStatus.value = SyncStatus.Syncing

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

            // Get health data from Health Connect since the last sync
            Log.d("HealthConnect", "HealthRepository: Getting health data from Health Connect")
            val healthData = healthConnectManager.getHealthDataSince(startTime)
            Log.d("HealthConnect", "HealthRepository: Got health data from Health Connect")

            // Transform health data to PIHS format
            Log.d("HealthConnect", "HealthRepository: Transforming health data to PIHS format")
            val pihsHealthData = healthDataTransformer.transformHealthData(healthData)
            Log.d("HealthConnect", "HealthRepository: Transformed health data to PIHS format")

            // Upload to Supabase
            Log.d("HealthConnect", "HealthRepository: Uploading health data to Supabase")
            uploadToSupabase(pihsHealthData)
            Log.d("HealthConnect", "HealthRepository: Uploaded health data to Supabase")

            // Update the last synced timestamp
            updateLastSyncedTimestamp(metricSourceInfo.id)

            _syncStatus.value = SyncStatus.Success
            Log.d("HealthConnect", "HealthRepository: Health data sync completed successfully")
        } catch (e: Exception) {
            Log.e("HealthConnect", "HealthRepository: Error syncing health data", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
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
    private suspend fun uploadToSupabase(pihsHealthData: PIHSHealthData) {
        try {
            Log.d("HealthConnect", "HealthRepository: Uploading health data to Supabase")

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
                    val dataPoint = buildJsonObject {
                        put("user_id", userId)
                        put("metric_source_id", metricSourceId)
                        put("timestamp", weightRecord.time)
                        put("metric_name", "body_weight_mass_kg")
                        put("value_numeric", weightRecord.weight)
                        put("unit", "kg")
                    }
                    postgrest["data_points"].insert(dataPoint)
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
                    postgrest["events"].insert(event)
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

    // Get or create a metric source for Health Connect
    private suspend fun getOrCreateMetricSource(userId: String): MetricSource {
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

            // If a source exists, return it
            if (existingSources.isNotEmpty()) {
                val source = existingSources.first()
                Log.d("HealthConnect", "HealthRepository: Found existing metric source with ID: ${source.id}, last synced at: ${source.last_synced_at}")
                return source
            }

            // Otherwise, create a new metric source
            Log.d("HealthConnect", "HealthRepository: Creating new metric source for Health Connect")
            val currentTime = Instant.now().toString()
            val newSource = buildJsonObject {
                put("user_id", userId)
                put("source_identifier", "health_connect_android")
                put("source_name", "Android Health Connect")
                put("source_type", "mobile_sync")
                put("is_active", true)
                put("last_synced_at", currentTime)
            }

            val result = postgrest["metric_sources"].insert(newSource).decodeSingle<MetricSource>()
            Log.d("HealthConnect", "HealthRepository: Created new metric source with ID: ${result.id}, last synced at: ${result.last_synced_at}")
            return result
        } catch (e: Exception) {
            Log.e("HealthConnect", "HealthRepository: Error getting or creating metric source", e)
            throw e
        }
    }

    // Upload other health metrics to data_points table
    private suspend fun uploadMetricsToDataPoints(pihsHealthData: PIHSHealthData, userId: String, metricSourceId: Long) {
        // Upload heart rate data
        pihsHealthData.heartRate.forEach { hrRecord ->
            hrRecord.samples.forEach { sample ->
                val dataPoint = buildJsonObject {
                    put("user_id", userId)
                    put("metric_source_id", metricSourceId)
                    put("timestamp", sample.time)
                    put("metric_name", "heartrate_timeseries_bpm")
                    put("value_numeric", sample.beatsPerMinute.toDouble())
                    put("unit", "bpm")
                }
                postgrest["data_points"].insert(dataPoint)
            }
        }

        // Upload blood pressure data
        pihsHealthData.bloodPressure.forEach { bpRecord ->
            // Systolic
            val systolicPoint = buildJsonObject {
                put("user_id", userId)
                put("metric_source_id", metricSourceId)
                put("timestamp", bpRecord.time)
                put("metric_name", "bloodpressure_systolic_mmhg")
                put("value_numeric", bpRecord.systolic)
                put("unit", "mmHg")
            }
            postgrest["data_points"].insert(systolicPoint)

            // Diastolic
            val diastolicPoint = buildJsonObject {
                put("user_id", userId)
                put("metric_source_id", metricSourceId)
                put("timestamp", bpRecord.time)
                put("metric_name", "bloodpressure_diastolic_mmhg")
                put("value_numeric", bpRecord.diastolic)
                put("unit", "mmHg")
            }
            postgrest["data_points"].insert(diastolicPoint)
        }

        // Upload blood glucose data
        pihsHealthData.bloodGlucose.forEach { bgRecord ->
            val dataPoint = buildJsonObject {
                put("user_id", userId)
                put("metric_source_id", metricSourceId)
                put("timestamp", bgRecord.time)
                put("metric_name", "bloodglucose_level_mmolL")
                put("value_numeric", bgRecord.level)
                put("unit", "mmol/L")
                put("value_json", buildJsonObject {
                    put("specimen_source", bgRecord.specimenSource)
                    put("meal_type", bgRecord.mealType)
                })
            }
            postgrest["data_points"].insert(dataPoint)
        }

        // Upload body temperature data
        pihsHealthData.bodyTemperature.forEach { tempRecord ->
            val dataPoint = buildJsonObject {
                put("user_id", userId)
                put("metric_source_id", metricSourceId)
                put("timestamp", tempRecord.time)
                put("metric_name", "bodytemperature_reading_celsius")
                put("value_numeric", tempRecord.temperature)
                put("unit", "celsius")
                put("value_json", buildJsonObject {
                    put("measurement_location", tempRecord.measurementLocation)
                })
            }
            postgrest["data_points"].insert(dataPoint)
        }

        // Upload oxygen saturation data
        pihsHealthData.oxygenSaturation.forEach { o2Record ->
            val dataPoint = buildJsonObject {
                put("user_id", userId)
                put("metric_source_id", metricSourceId)
                put("timestamp", o2Record.time)
                put("metric_name", "spo2_reading_percentage")
                put("value_numeric", o2Record.saturation)
                put("unit", "percent")
            }
            postgrest["data_points"].insert(dataPoint)
        }

        // Upload respiratory rate data
        pihsHealthData.respiratoryRate.forEach { rrRecord ->
            val dataPoint = buildJsonObject {
                put("user_id", userId)
                put("metric_source_id", metricSourceId)
                put("timestamp", rrRecord.time)
                put("metric_name", "respiratoryrate_reading_bpm")
                put("value_numeric", rrRecord.rate)
                put("unit", "breaths_per_minute")
            }
            postgrest["data_points"].insert(dataPoint)
        }

        // Upload steps data
        pihsHealthData.steps.forEach { stepsRecord ->
            val dataPoint = buildJsonObject {
                put("user_id", userId)
                put("metric_source_id", metricSourceId)
                put("timestamp", stepsRecord.endTime)
                put("metric_name", "activity_steps_total_count")
                put("value_numeric", stepsRecord.count.toDouble())
                put("unit", "count")
            }
            postgrest["data_points"].insert(dataPoint)
        }

        // Upload nutrition data to events table
        pihsHealthData.nutrition.forEach { nutritionRecord ->
            // Calculate duration in minutes
            val startInstant = Instant.parse(nutritionRecord.startTime)
            val endInstant = Instant.parse(nutritionRecord.endTime)
            val durationMinutes = (endInstant.epochSecond - startInstant.epochSecond) / 60

            // Create properties JSON object with nutrition details
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
            }
            postgrest["events"].insert(event)
        }

        // Upload hydration data to data_points table
        pihsHealthData.hydration.forEach { hydrationRecord ->
            val dataPoint = buildJsonObject {
                put("user_id", userId)
                put("metric_source_id", metricSourceId)
                put("timestamp", hydrationRecord.endTime)
                put("metric_name", "hydration_intake_liters")
                put("value_numeric", hydrationRecord.volume)
                put("unit", "L")
            }
            postgrest["data_points"].insert(dataPoint)
        }
    }
}

// Sync status
sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    object Success : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}
