package org.devpins.pihs.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.devpins.pihs.data.model.DataPoint
import org.devpins.pihs.data.remote.DataUploaderService
import org.devpins.pihs.data.remote.UploadResult
import java.time.Instant

/**
 * WorkManager worker that periodically polls the device's location using low-power settings.
 * This worker is designed to minimize battery drain while providing location data for
 * the server-side "Anchor" engine to discover significant locations like Home and Work.
 * 
 * Key features:
 * - Uses PRIORITY_BALANCED_POWER_ACCURACY (no GPS usage)
 * - Runs approximately every 30 minutes (inexact interval)
 * - Only runs when battery is not low and device has network connectivity
 * - POSTs location data to backend for analysis
 */
@HiltWorker
class LocationPollingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val dataUploaderService: DataUploaderService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "LocationPollingWorker"
        const val UNIQUE_WORK_NAME = "location_polling_work"
        
        // Location request timeout in milliseconds
        private const val LOCATION_TIMEOUT_MS = 30_000L // 30 seconds
        
        // Metric source identifier for location tracking
        private const val LOCATION_SOURCE_IDENTIFIER = "android_location_polling_v1"
        private const val LOCATION_SOURCE_NAME = "Android Location Polling"
    }

    @Serializable
    private data class MetricSourceId(val id: Long)

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override suspend fun doWork(): Result {
        Log.d(TAG, "LocationPollingWorker started - Attempt ${runAttemptCount + 1}")

        // Wait for Supabase Auth to initialize and restore session from storage
        Log.d(TAG, "Waiting for auth initialization...")
        auth.awaitInitialization()
        Log.d(TAG, "Auth initialization complete")

        // Check if user is authenticated
        val currentUser = auth.currentUserOrNull()
        if (currentUser == null) {
            Log.w(TAG, "User not authenticated, cannot log location")
            return Result.failure()
        }
        
        // Check location permissions
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Location permissions not granted")
            return Result.failure()
        }
        
        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        
        // Get current location
        val location = getCurrentLocation()
        if (location == null) {
            Log.w(TAG, "Failed to get location, will retry")
            return if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
        
        Log.i(TAG, "Location obtained: lat=${location.latitude}, lon=${location.longitude}, accuracy=${location.accuracy}m")
        
        // Upload location to backend
        val uploadSuccess = uploadLocation(currentUser.id, location)
        
        return if (uploadSuccess) {
            Log.i(TAG, "Location successfully uploaded")
            Result.success()
        } else {
            Log.w(TAG, "Failed to upload location, will retry")
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    /**
     * Checks if the app has the required location permissions.
     */
    private fun hasLocationPermissions(): Boolean {
        val coarseLocation = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val fineLocation = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        return coarseLocation || fineLocation
    }

    /**
     * Gets the current location using FusedLocationProviderClient with low-power settings.
     * Uses PRIORITY_BALANCED_POWER_ACCURACY to avoid GPS usage and minimize battery drain.
     * 
     * @return Location if successfully obtained within timeout, null otherwise
     */
    private suspend fun getCurrentLocation(): Location? {
        return try {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                // Use PRIORITY_BALANCED_POWER_ACCURACY for battery efficiency
                // This uses Wi-Fi and cell towers, not GPS (100m-300m accuracy is sufficient)
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                ).await()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when requesting location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Exception when requesting location", e)
            null
        }
    }

    /**
     * Gets or creates the metric source for location polling.
     * @param userId The authenticated user's ID
     * @return The metric source ID, or -1 if failed
     */
    private suspend fun getOrCreateLocationMetricSource(userId: String): Long {
        return try {
            val existing = postgrest.from("metric_sources")
                .select(Columns.list("id")) {
                    filter {
                        eq("user_id", userId)
                        eq("source_identifier", LOCATION_SOURCE_IDENTIFIER)
                    }
                }
                .decodeList<MetricSourceId>()

            if (existing.isNotEmpty()) {
                existing.first().id
            } else {
                val newSource = buildJsonObject {
                    put("user_id", userId)
                    put("source_identifier", LOCATION_SOURCE_IDENTIFIER)
                    put("source_name", LOCATION_SOURCE_NAME)
                    put("is_active", true)
                    put("last_synced_at", null as String?)
                }
                val inserted = postgrest["metric_sources"].insert(newSource).decodeSingle<MetricSourceId>()
                inserted.id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting/creating location metric source", e)
            -1L
        }
    }

    /**
     * Uploads the location data to the backend.
     * Creates a DataPoint with GeoJSON format and sends it via DataUploaderService.
     * 
     * @param userId The authenticated user's ID
     * @param location The location to upload
     * @return true if upload was successful, false otherwise
     */
    private suspend fun uploadLocation(userId: String, location: Location): Boolean {
        return try {
            // Get or create the metric source
            val metricSourceId = getOrCreateLocationMetricSource(userId)
            if (metricSourceId == -1L) {
                Log.e(TAG, "Cannot upload location: metric source unavailable")
                return false
            }
            
            // Create GeoJSON Point with proper structure: {"type":"Point","coordinates":[lon,lat]}
            val geoJson = buildJsonObject {
                put("type", "Point")
                put("coordinates", buildJsonArray {
                    add(location.longitude)
                    add(location.latitude)
                })
            }
            
            // For the timestamp, use the location's timestamp if available, otherwise current time
            val timestamp = if (location.time > 0) {
                Instant.ofEpochMilli(location.time)
            } else {
                Instant.now()
            }
            
            // Create a DataPoint with the location information
            val dataPoint = DataPoint(
                metricSourceId = metricSourceId,
                timestamp = timestamp.toString(),
                metricName = "environment_location",
                valueNumeric = null,
                valueText = null,
                valueJson = null,
                valueGeography = geoJson,
                unit = null,
                tags = null
            )
            
            // Upload the data point
            when (val result = dataUploaderService.uploadDataPoints(listOf(dataPoint))) {
                is UploadResult.Success -> {
                    Log.i(TAG, "Location uploaded successfully: ${result.response}")
                    true
                }
                is UploadResult.Failure -> {
                    Log.e(TAG, "Failed to upload location: ${result.errorMessage}", result.exception)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during location upload", e)
            false
        }
    }
}
