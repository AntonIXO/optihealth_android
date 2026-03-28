package org.devpins.pihs.health

import android.util.Log
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
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

            // Wait for Supabase Auth to initialize and restore session from storage
            Log.d("HealthConnect", "HealthRepository: Waiting for auth initialization...")
            auth.awaitInitialization()
            Log.d("HealthConnect", "HealthRepository: Auth initialization complete")

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

            // Check if sync was canceled
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
            // Propagate the exception so WorkManager can apply backoff/retry logic
            throw e
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

            // Wait for Supabase Auth to initialize and restore session from storage
            Log.d("HealthConnect", "HealthRepository: Waiting for auth initialization...")
            auth.awaitInitialization()
            Log.d("HealthConnect", "HealthRepository: Auth initialization complete")

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
    @OptIn(ExperimentalSerializationApi::class)
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
            val chunkSize = 500
            var uploadedChunks = 0
            Log.d("HealthConnect", "HealthRepository: Preparing to upload ${dataPointsToUpload.size} data points in chunks of $chunkSize via DataUploaderService.")
            for (chunk in dataPointsToUpload.chunked(chunkSize)) {
                Log.d("HealthConnect", "HealthRepository: Uploading chunk ${uploadedChunks + 1} with ${chunk.size} items.")
                when (val result = dataUploaderService.uploadDataPoints(chunk)) {
                    is org.devpins.pihs.data.remote.UploadResult.Success -> {
                        uploadedChunks++
                        Log.i(
                            "HealthConnect",
                            "Chunk $uploadedChunks uploaded. Message: ${result.response.message}. Client sent: ${chunk.size} items."
                        )
                    }
                    is org.devpins.pihs.data.remote.UploadResult.Failure -> {
                        Log.e("HealthConnect", "DataUploaderService failure on chunk ${uploadedChunks + 1}: ${result.errorMessage}", result.exception)
                        // Throw an exception to be caught by the calling sync function,
                        // which will set SyncStatus.Error
                        throw result.exception ?: RuntimeException("DataUploaderService failed: ${result.errorMessage}")
                    }
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
                    put("description", exerciseRecord.notes)
                    put("properties", properties)
                    put("created_at", Instant.now().toString())
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

        // Upload sleep events using DataUploaderService
        val eventsToUpload = transformPihsToEvents(pihsHealthData)
        if (eventsToUpload.isNotEmpty()) {
            Log.d("HealthConnect", "HealthRepository: Preparing to upload ${eventsToUpload.size} events via DataUploaderService.")
            when (val result = dataUploaderService.uploadEvents(eventsToUpload)) {
                is org.devpins.pihs.data.remote.UploadResult.Success -> {
                    Log.i(
                        "HealthConnect",
                        "Events uploaded successfully. Message: ${result.response.message}. Client sent: ${eventsToUpload.size} items."
                    )
                }
                is org.devpins.pihs.data.remote.UploadResult.Failure -> {
                    Log.e("HealthConnect", "DataUploaderService failure on events upload: ${result.errorMessage}", result.exception)
                    // Throw an exception to be caught by the calling sync function
                    throw result.exception ?: RuntimeException("DataUploaderService failed: ${result.errorMessage}")
                }
            }
        } else {
            Log.d("HealthConnect", "HealthRepository: No events to upload via DataUploaderService.")
        }

        // This try-catch block and its contents were remnants of the old upload logic and are now redundant.
        // The calls to getOrCreateMetricSource and uploadMetricsToDataPoints were causing errors as these methods were deleted.
        // Data point uploads are handled by DataUploaderService, and event uploads are handled above this section.
        // Log.d("HealthConnect", "HealthRepository: Health data processing (points and events) complete via new method.")
    } // This closes uploadToSupabase method

    // New method to transform PIHSHealthData to List<DataPoint>
private fun transformPihsToDataPoints(
    pihsHealthData: PIHSHealthData,
    metricSourceId: Long
): List<org.devpins.pihs.data.model.DataPoint> {
    // Pre-allocate approximate capacity to reduce list resizing
    // Each sleep record generates ~10 data points, exercise ~7, heart rate samples vary
    val estimatedCapacity = pihsHealthData.steps.size +
        pihsHealthData.weight.size +
        pihsHealthData.heartRate.sumOf { it.samples.size } +
        pihsHealthData.heartRateVariability.size +
        pihsHealthData.restingHeartRate.size +
        (pihsHealthData.bloodPressure.size * 2) + // 2 data points per reading
        pihsHealthData.bloodGlucose.size +
        pihsHealthData.bodyTemperature.size +
        pihsHealthData.oxygenSaturation.size +
        pihsHealthData.respiratoryRate.size +
        pihsHealthData.hydration.size +
        (pihsHealthData.sleep.size * 10) + // Approximate data points per sleep session
        (pihsHealthData.exercise.size * 7) // Approximate data points per exercise

    val dataPoints = ArrayList<org.devpins.pihs.data.model.DataPoint>(estimatedCapacity)
    // val currentTimestampForTags = org.devpins.pihs.data.model.DataPoint( // This was a placeholder, not actively used.
    // metricSourceId = null, timestamp = "", metricName = "", valueNumeric = null, valueText = null, valueJson = null, unit = null, tags = null
    // ).timestamp

    // Steps - use optimized filtering and mapping
    dataPoints.addAll(
        pihsHealthData.steps
            .filter { it.count > 0 }
            .map { stepData ->
                org.devpins.pihs.data.model.DataPoint(
                    metricSourceId = metricSourceId,
                    timestamp = stepData.endTime,
                    metricName = "activity_steps",
                    valueNumeric = stepData.count.toDouble(),
                    valueText = null,
                    valueJson = null,
                    unit = "count",
                    tags = null,
                    valueGeography = null
                )
            }
    )

    // Weight - use optimized filtering and mapping
    dataPoints.addAll(
        pihsHealthData.weight
            .filter { it.weight > 0 }
            .map { weightData ->
                org.devpins.pihs.data.model.DataPoint(
                    metricSourceId = metricSourceId,
                    timestamp = weightData.time,
                    metricName = "body_weight_mass_kg",
                    valueNumeric = weightData.weight,
                    valueText = null,
                    valueJson = null,
                    unit = "kg",
                    tags = null,
                    valueGeography = null
                )
            }
    )

    // Heart Rate Samples - flatten nested structure and filter in one pass
    dataPoints.addAll(
        pihsHealthData.heartRate.flatMap { hrData ->
            hrData.samples
                .filter { it.beatsPerMinute > 0 }
                .map { sample ->
                    org.devpins.pihs.data.model.DataPoint(
                        metricSourceId = metricSourceId,
                        timestamp = sample.time,
                        metricName = "hr",
                        valueNumeric = sample.beatsPerMinute.toDouble(),
                        valueText = null,
                        valueJson = null,
                        unit = "bpm",
                        tags = null,
                        valueGeography = null
                    )
                }
        }
    )

    // Heart Rate Variability (RMSSD)
    pihsHealthData.heartRateVariability.forEach { hrvData ->
        if (hrvData.rmssdMillis > 0) {
            val tags = if (hrvData.zoneOffset.isNotEmpty()) {
                buildJsonObject {
                    put("zone_offset", hrvData.zoneOffset)
                }
            } else {
                null
            }

            dataPoints.add(
                org.devpins.pihs.data.model.DataPoint(
                    metricSourceId = metricSourceId,
                    timestamp = hrvData.time,
                    metricName = "hrv_rmssd",
                    valueNumeric = hrvData.rmssdMillis,
                    valueText = null,
                    valueJson = null,
                    unit = "ms",
                    tags = tags,
                    valueGeography = null
                )
            )
        }
    }

    // Resting Heart Rate
    pihsHealthData.restingHeartRate.forEach { restingHrData ->
        if (restingHrData.beatsPerMinute > 0) {
            val tags = if (restingHrData.zoneOffset.isNotEmpty()) {
                buildJsonObject {
                    put("zone_offset", restingHrData.zoneOffset)
                }
            } else {
                null
            }

            dataPoints.add(
                org.devpins.pihs.data.model.DataPoint(
                    metricSourceId = metricSourceId,
                    timestamp = restingHrData.time,
                    metricName = "hr_resting",
                    valueNumeric = restingHrData.beatsPerMinute.toDouble(),
                    valueText = null,
                    valueJson = null,
                    unit = "bpm",
                    tags = tags,
                    valueGeography = null
                )
            )
        }
    }

    // Blood Pressure
    pihsHealthData.bloodPressure.forEach { bpData ->
        if (bpData.systolic > 0 && bpData.diastolic > 0) {
            dataPoints.add(
                org.devpins.pihs.data.model.DataPoint(
                    metricSourceId = metricSourceId,
                    timestamp = bpData.time,
                    metricName = "bloodpressure_systolic_mmhg",
                    valueNumeric = bpData.systolic,
                    valueText = null,
                    valueJson = null,
                    unit = "mmHg",
                    tags = null,
                    valueGeography = null
                )
            )
            dataPoints.add(
                org.devpins.pihs.data.model.DataPoint(
                    metricSourceId = metricSourceId,
                    timestamp = bpData.time,
                    metricName = "bloodpressure_diastolic_mmhg",
                    valueNumeric = bpData.diastolic,
                    valueText = null,
                    valueJson = null,
                    unit = "mmHg",
                    tags = null,
                    valueGeography = null
                )
            )
        }
    }

    // Blood Glucose
    pihsHealthData.bloodGlucose.forEach { bgData ->
        if (bgData.level > 0) {
            dataPoints.add(
                org.devpins.pihs.data.model.DataPoint(
                    metricSourceId = metricSourceId,
                    timestamp = bgData.time,
                    metricName = "nutrition_glucose_blood",
                    valueNumeric = bgData.level * 18.0,
                    valueText = null,
                    valueJson = buildJsonObject {
                        put("specimen_source", bgData.specimenSource)
                        put("meal_type", bgData.mealType)
                    },
                    unit = "mg/dL",
                    tags = null,
                    valueGeography = null
                )
            )
        }
    }

    // Body Temperature
    pihsHealthData.bodyTemperature.forEach { tempData ->
        if (tempData.temperature > 0) {
            dataPoints.add(
                org.devpins.pihs.data.model.DataPoint(
                    metricSourceId = metricSourceId,
                    timestamp = tempData.time,
                    metricName = "body_temperature",
                    valueNumeric = tempData.temperature,
                    valueText = null,
                    valueJson = buildJsonObject {
                        put("measurement_location", tempData.measurementLocation)
                    },
                    unit = "celsius",
                    tags = null,
                    valueGeography = null
                )
            )
        }
    }

    // Oxygen Saturation
    pihsHealthData.oxygenSaturation.forEach { o2Data ->
        if (o2Data.saturation > 0) {
            dataPoints.add(
                org.devpins.pihs.data.model.DataPoint(
                    metricSourceId = metricSourceId,
                    timestamp = o2Data.time,
                    metricName = "blood_oxygen_spo2",
                    valueNumeric = o2Data.saturation,
                    valueText = null,
                    valueJson = null,
                    unit = "percentage",
                    tags = null,
                    valueGeography = null
                )
            )
        }
    }

    // Respiratory Rate
    pihsHealthData.respiratoryRate.forEach { rrData ->
        if (rrData.rate > 0) {
            dataPoints.add(
                org.devpins.pihs.data.model.DataPoint(
                    metricSourceId = metricSourceId,
                    timestamp = rrData.time,
                    metricName = "respiratory_rate",
                    valueNumeric = rrData.rate,
                    valueText = null,
                    valueJson = null,
                    unit = "breaths_per_minute",
                    tags = null,
                    valueGeography = null
                )
            )
        }
    }

    // Hydration
    pihsHealthData.hydration.forEach { hydrationData ->
        if (hydrationData.volume > 0) {
            dataPoints.add(
                org.devpins.pihs.data.model.DataPoint(
                    metricSourceId = metricSourceId,
                    timestamp = hydrationData.endTime,
                    metricName = "hydration",
                    valueNumeric = hydrationData.volume,
                    valueText = null,
                    valueJson = null,
                    unit = "L",
                    tags = null,
                    valueGeography = null
                )
            )
        }
    }

    // Sleep Metrics (decomposed from PIHSSleepData)
        Log.d("SleepData", "sleepData: ${pihsHealthData.sleep}")
    pihsHealthData.sleep.forEach { sleepData ->
        val sleepTags = buildJsonObject { put("sleep_session_start_time", sleepData.startTime) }

        // Note: sleep_stages is now handled as an Event, not a DataPoint
        // See transformPihsToEvents() method

        if (sleepData.totalSleepDurationMinutes > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = sleepData.endTime,
                metricName = "sleep_duration_total",
                valueNumeric = sleepData.totalSleepDurationMinutes,
                valueText = null,
                valueJson = null,
                unit = "minutes",
                tags = sleepTags,
                valueGeography = null
            )
        )
        if (sleepData.deepSleepDurationMinutes > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = sleepData.endTime,
                metricName = "sleep_duration_deep",
                valueNumeric = sleepData.deepSleepDurationMinutes,
                valueText = null,
                valueJson = null,
                valueGeography = null,
                unit = "minutes",
                tags = sleepTags
            )
        )
        if (sleepData.lightSleepDurationMinutes > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = sleepData.endTime,
                metricName = "sleep_duration_light",
                valueNumeric = sleepData.lightSleepDurationMinutes,
                valueText = null,
                valueJson = null,
                unit = "minutes",
                tags = sleepTags,
                valueGeography = null
            )
        )
        if (sleepData.remSleepDurationMinutes > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = sleepData.endTime,
                metricName = "sleep_duration_rem",
                valueNumeric = sleepData.remSleepDurationMinutes,
                valueText = null,
                valueJson = null,
                unit = "minutes",
                tags = sleepTags,
                valueGeography = null
            )
        )
        if (sleepData.sleepEfficiencyPercentage > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = sleepData.endTime,
                metricName = "sleep_efficiency_percentage",
                valueNumeric = sleepData.sleepEfficiencyPercentage,
                valueText = null,
                valueJson = null,
                unit = "percentage",
                tags = sleepTags,
                valueGeography = null
            )
        )
        if (sleepData.sleepLatencyMinutes > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = sleepData.endTime,
                metricName = "sleep_latency_minutes",
                valueNumeric = sleepData.sleepLatencyMinutes,
                valueText = null,
                valueJson = null,
                unit = "minutes",
                tags = sleepTags,
                valueGeography = null
            )
        )
        if (sleepData.awakeningsCount > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = sleepData.endTime,
                metricName = "awakenings_count",
                valueNumeric = sleepData.awakeningsCount.toDouble(),
                valueText = null,
                valueJson = null,
                unit = "count",
                tags = sleepTags,
                valueGeography = null
            )
        )
        if (sleepData.timeInBedMinutes > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = sleepData.endTime,
                metricName = "time_in_bed_minutes",
                valueNumeric = sleepData.timeInBedMinutes,
                valueText = null,
                valueJson = null,
                unit = "minutes",
                tags = sleepTags,
                valueGeography = null
            )
        )
        val awakeMinutes = sleepData.timeInBedMinutes - sleepData.totalSleepDurationMinutes
        if (awakeMinutes > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = sleepData.endTime,
                metricName = "sleep_duration_awake",
                valueNumeric = awakeMinutes,
                valueText = null,
                valueJson = null,
                unit = "minutes",
                tags = sleepTags,
                valueGeography = null
            )
        )
        if (sleepData.sleepScore > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = sleepData.endTime,
                metricName = "sleep_score",
                valueNumeric = sleepData.sleepScore,
                valueText = null,
                valueJson = null,
                unit = "score",
                tags = sleepTags,
                valueGeography = null
            )
        )
    }

    pihsHealthData.exercise.forEach { exercise ->
        val exerciseTags = buildJsonObject {
            put("exercise_type", exercise.exerciseType)
            put("exercise_session_start_time", exercise.startTime)
            if (exercise.title.isNotEmpty()) put("exercise_title", exercise.title)
        }

        if (exercise.durationMinutes > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = exercise.endTime,
                metricName = "workout_duration",
                valueNumeric = exercise.durationMinutes * 60.0,
                valueText = null,
                valueJson = null,
                unit = "seconds",
                tags = exerciseTags,
                valueGeography = null
            )
        )
        if (exercise.calories > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = exercise.endTime,
                metricName = "workout_calories_burned",
                valueNumeric = exercise.calories,
                valueText = null,
                valueJson = null,
                unit = "kcal",
                tags = exerciseTags,
                valueGeography = null
            )
        )
        if (exercise.distanceKm > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = exercise.endTime,
                metricName = "workout_distance",
                valueNumeric = exercise.distanceKm * 1000.0,
                valueText = null,
                valueJson = null,
                unit = "meters",
                tags = exerciseTags,
                valueGeography = null
            )
        )
        if (exercise.averageHeartRateBpm > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = exercise.endTime,
                metricName = "workout_average_heart_rate_bpm",
                valueNumeric = exercise.averageHeartRateBpm,
                valueText = null,
                valueJson = null,
                unit = "bpm",
                tags = exerciseTags,
                valueGeography = null
            )
        )
        if (exercise.maxHeartRateBpm > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = exercise.endTime,
                metricName = "workout_max_heart_rate_bpm",
                valueNumeric = exercise.maxHeartRateBpm,
                valueText = null,
                valueJson = null,
                unit = "bpm",
                tags = exerciseTags,
                valueGeography = null
            )
        )
        if (exercise.stepsCount > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = exercise.endTime,
                metricName = "workout_steps_count",
                valueNumeric = exercise.stepsCount.toDouble(),
                valueText = null,
                valueJson = null,
                unit = "count",
                tags = exerciseTags,
                valueGeography = null
            )
        )
        if (exercise.activeEnergyKcal > 0) dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = exercise.endTime,
                metricName = "workout_active_energy_kcal",
                valueNumeric = exercise.activeEnergyKcal,
                valueText = null,
                valueJson = null,
                unit = "kcal",
                tags = exerciseTags,
                valueGeography = null
            )
        )
        dataPoints.add(
            org.devpins.pihs.data.model.DataPoint(
                metricSourceId = metricSourceId,
                timestamp = exercise.endTime,
                metricName = "workout_type",
                valueNumeric = null,
                valueText = exercise.exerciseType,
                valueJson = null,
                unit = null,
                tags = exerciseTags,
                valueGeography = null
            )
        )
    }

    Log.d("HealthConnect", "HealthRepository: Transformed ${dataPoints.size} data points from PIHSHealthData.")
    return dataPoints
}

    // New method to transform sleep stages to Events
    private fun transformPihsToEvents(
        pihsHealthData: PIHSHealthData
    ): List<org.devpins.pihs.data.model.Event> {
        val events = mutableListOf<org.devpins.pihs.data.model.Event>()

        // Transform sleep stages to events
        pihsHealthData.sleep.forEach { sleepData ->
            Log.d("SleepData", "Transforming sleep_stages to event: ${sleepData}")
            
            // Build the stages JSON for the properties field
            val stagesJson = kotlinx.serialization.json.buildJsonArray {
                sleepData.stages.forEach { stage ->
                    val durationSeconds = Instant.parse(stage.endTime).epochSecond - Instant.parse(stage.startTime).epochSecond
                    add(
                        buildJsonObject {
                            put("stage", stage.stage.lowercase())
                            put("startTimestamp", stage.startTime)
                            put("endTimestamp", stage.endTime)
                            put("durationSeconds", durationSeconds)
                        }
                    )
                }
            }

            // Create properties object with stages and additional metadata
            val properties = buildJsonObject {
                put("stages", stagesJson)
                put("total_duration_minutes", sleepData.totalSleepDurationMinutes)
                put("deep_sleep_minutes", sleepData.deepSleepDurationMinutes)
                put("light_sleep_minutes", sleepData.lightSleepDurationMinutes)
                put("rem_sleep_minutes", sleepData.remSleepDurationMinutes)
                put("efficiency_percentage", sleepData.sleepEfficiencyPercentage)
            }

            events.add(
                org.devpins.pihs.data.model.Event(
                    eventName = "sleep",
                    startTimestamp = sleepData.startTime,
                    endTimestamp = sleepData.endTime,
                    description = "Sleep session with ${sleepData.stages.size} stage transitions",
                    properties = properties
                )
            )
        }

        Log.d("HealthConnect", "HealthRepository: Transformed ${events.size} events from PIHSHealthData.")
        return events
    }
} // Closing brace for HealthRepository class

// Sync status
sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    object Success : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}
