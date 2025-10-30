package org.devpins.pihs.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import io.github.jan.supabase.postgrest.query.Columns
import org.devpins.pihs.MainActivity
import org.devpins.pihs.R
import org.devpins.pihs.data.model.DataPoint
import org.devpins.pihs.data.remote.DataUploaderService
import org.devpins.pihs.data.remote.UploadResult
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "location_tracking_channel"
        
        // Location tracking parameters
        private const val SIGNIFICANT_DISTANCE_METERS = 500.0 // 0.5 km
        private const val STABILITY_RADIUS_METERS = 150.0 // 150 meters
        private const val STABILITY_TIME_MS = 5 * 60 * 1000L // 5 minutes
        
        // Location request parameters
        private const val LOCATION_UPDATE_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        private const val FASTEST_LOCATION_INTERVAL_MS = 60 * 1000L // 60 seconds
        
        // Intent actions
        const val ACTION_START_LOCATION_TRACKING = "org.devpins.pihs.ACTION_START_LOCATION_TRACKING"
        const val ACTION_STOP_LOCATION_TRACKING = "org.devpins.pihs.ACTION_STOP_LOCATION_TRACKING"

        // Metric source constants for location datapoints
        private const val LOCATION_SOURCE_IDENTIFIER = "android_location_significant_v1"
        private const val LOCATION_SOURCE_NAME = "Android Location (Significant)"
    }
    
    @Inject
    lateinit var postgrest: Postgrest
    
    @Inject
    lateinit var auth: Auth

    @Inject
    lateinit var dataUploaderService: DataUploaderService
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    
    // Location tracking state
    private var lastStableLocation: Location? = null
    private var potentialNewLocation: Location? = null
    private var potentialNewLocationTimestamp: Long = 0
    private var isConfirmingStability = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Initializing location tracking service")
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Initialize location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { currentLocation ->
                    processNewLocation(currentLocation)
                }
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LOCATION_TRACKING -> {
                Log.d(TAG, "onStartCommand: Starting location tracking")
                startForeground(NOTIFICATION_ID, createNotification("Tracking your location"))
                startLocationUpdates()
            }
            ACTION_STOP_LOCATION_TRACKING -> {
                Log.d(TAG, "onStartCommand: Stopping location tracking")
                stopSelf()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Cleaning up resources")
        stopLocationUpdates()
        serviceScope.cancel()
    }
    
    private fun startLocationUpdates() {
        try {
            Log.i(TAG, "Attempting to start location updates with interval: ${LOCATION_UPDATE_INTERVAL_MS}ms, fastest interval: ${FASTEST_LOCATION_INTERVAL_MS}ms, priority: HIGH_ACCURACY")
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_UPDATE_INTERVAL_MS
            )
                .setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL_MS)
                .build()
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            
            // Get initial location
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    Log.d(TAG, "Initial location: ${it.latitude}, ${it.longitude}")
                    lastStableLocation = it
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "startLocationUpdates: Missing location permission", e)
        }
    }
    
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    
    private fun processNewLocation(currentLocation: Location) {
        Log.d(TAG, "New location: ${currentLocation.latitude}, ${currentLocation.longitude}")
        
        // If we don't have a stable location yet, set it
        if (lastStableLocation == null) {
            lastStableLocation = currentLocation
            Log.i(TAG, "Initial stable location established at: ${currentLocation.latitude}, ${currentLocation.longitude}")
            updateNotification("Established baseline location")
            return
        }
        
        // Calculate distance from last stable location
        val distanceFromStable = currentLocation.distanceTo(lastStableLocation!!)
        Log.d(TAG, "Distance from stable location: $distanceFromStable meters")
        
        if (distanceFromStable > SIGNIFICANT_DISTANCE_METERS && !isConfirmingStability) {
            // Significant movement detected, start monitoring for stability
            potentialNewLocation = currentLocation // Set potential location first
            potentialNewLocationTimestamp = System.currentTimeMillis()
            isConfirmingStability = true
            Log.i(TAG, "Potential new stable location identified at: ${potentialNewLocation?.latitude}, ${potentialNewLocation?.longitude}. Monitoring for stability over ${STABILITY_TIME_MS}ms within ${STABILITY_RADIUS_METERS}m.")
            updateNotification("Significant movement detected, monitoring stability")
        } else if (isConfirmingStability) {
            // Check if we're still within the stability radius of the potential new location
            val distanceFromPotential = currentLocation.distanceTo(potentialNewLocation!!)
            val timeAtPotentialLocation = System.currentTimeMillis() - potentialNewLocationTimestamp
            
            Log.d(TAG, "Stability check: Current: ${currentLocation.latitude}, ${currentLocation.longitude}. Potential: ${potentialNewLocation?.latitude}, ${potentialNewLocation?.longitude}. Dist from potential: $distanceFromPotential m. Time at potential: $timeAtPotentialLocation ms.")
            
            if (distanceFromPotential <= STABILITY_RADIUS_METERS) {
                // Still within stability radius
                if (timeAtPotentialLocation >= STABILITY_TIME_MS) {
                    // Stability confirmed at new location
                    Log.i(TAG, "New stable location CONFIRMED at: ${potentialNewLocation?.latitude}, ${potentialNewLocation?.longitude} after $timeAtPotentialLocation ms.")
                    
                    // Log the significant location change to Supabase
                    logSignificantLocationChange(lastStableLocation!!, potentialNewLocation!!, distanceFromStable)
                    
                    // Update the last stable location
                    lastStableLocation = potentialNewLocation
                    
                    // Reset stability monitoring
                    isConfirmingStability = false
                    potentialNewLocation = null
                    potentialNewLocationTimestamp = 0
                    
                    updateNotification("New stable location established")
                }
                // Otherwise, continue monitoring stability
            } else {
                // User moved away from the potential new location
                Log.d(TAG, "User moved away from potential new location.")
                if (potentialNewLocation != null && lastStableLocation != null) {
                    val distanceFromOriginalStable = potentialNewLocation!!.distanceTo(lastStableLocation!!)
                    // If the potential location was still somewhat significant compared to the last fully stable one
                    if (distanceFromOriginalStable > (SIGNIFICANT_DISTANCE_METERS / 2)) {
                        Log.i(TAG, "Logging potential location as a TRANSITORY stable point: ${potentialNewLocation?.latitude}, ${potentialNewLocation?.longitude}")
                        // Log this as a significant, albeit not fully stabilized, location.
                        // The stability duration will be shorter than STABILITY_TIME_MS.
                        logSignificantLocationChange(lastStableLocation!!, potentialNewLocation!!, distanceFromOriginalStable)
                        lastStableLocation = potentialNewLocation // Update base for next significant hop
                    } else {
                        Log.d(TAG, "Potential location ${potentialNewLocation?.latitude}, ${potentialNewLocation?.longitude} discarded as not a significant transit point.")
                    }
                }
                // Reset stability monitoring
                isConfirmingStability = false
                potentialNewLocation = null
                potentialNewLocationTimestamp = 0
                updateNotification("Tracking your location")
            }
        }
    }
    
    private fun logSignificantLocationChange(previousLocation: Location, newLocation: Location, distanceMeters: Float) {
        serviceScope.launch {
            var eventJsonString = "Event data not available or construction failed."
            try {
                val userId = auth.currentUserOrNull()?.id
                if (userId == null) {
                    Log.e(TAG, "Cannot log location change: User not authenticated")
                    return@launch
                }

                val distanceKm = distanceMeters / 1000.0
                val stabilityDurationMinutes = (System.currentTimeMillis() - potentialNewLocationTimestamp) / (60 * 1000)

                // Create properties JSON object
                val properties = buildJsonObject {
                    put("previous_latitude", previousLocation.latitude)
                    put("previous_longitude", previousLocation.longitude)
                    put("current_latitude", newLocation.latitude)
                    put("current_longitude", newLocation.longitude)
                    put("distance_moved_km", distanceKm)
                    put("stability_duration_minutes", stabilityDurationMinutes)
                }

                // Create event JSON object (for human-readable timeline)
                val event = buildJsonObject {
                    put("user_id", userId)
                    put("event_name", "Significant Location Change")
                    put("start_timestamp", Instant.now().toString())
                    put("end_timestamp", null)
                    put("duration_minutes", stabilityDurationMinutes)
                    put("description", "User moved approximately ${String.format("%.2f", distanceKm)} km to a new stable location.")
                    put("category", "Location")
                    put("properties", properties)
                    put("tags", buildJsonObject {
                        put("0", "automated_detection")
                    })
                }
                eventJsonString = event.toString()

                // Insert event into Supabase
                Log.i(TAG, "Attempting to log significant location change to Supabase. Event data: $eventJsonString")
                postgrest["events"].insert(event)
                Log.d(TAG, "Successfully logged significant location change to Supabase")

                // Also upload a data_points row for environment_location with GeoJSON geography
                uploadEnvironmentLocation(userId, newLocation)

            } catch (e: Exception) {
                Log.e(TAG, "Error logging location change to Supabase. Attempted event data: $eventJsonString. User ID: ${auth.currentUserOrNull()?.id}", e)
            }
        }
    }
    
    @Serializable
    private data class MetricSourceId(val id: Long)

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

    private suspend fun uploadEnvironmentLocation(userId: String, newLocation: Location) {
        try {
            val metricSourceId = getOrCreateLocationMetricSource(userId)
            if (metricSourceId == -1L) {
                Log.e(TAG, "Cannot upload environment_location: metric source unavailable")
                return
            }

            val geoJson = buildJsonObject {
                put("type", "Point")
                put("coordinates", buildJsonArray {
                    add(newLocation.longitude)
                    add(newLocation.latitude)
                })
            }

            val dp = DataPoint(
                metricSourceId = metricSourceId,
                timestamp = Instant.ofEpochMilli(newLocation.time).toString(),
                metricName = "environment_location",
                valueNumeric = null,
                valueText = null,
                valueJson = null,
                valueGeography = geoJson,
                unit = null,
                tags = null
            )

            when (val result = dataUploaderService.uploadDataPoints(listOf(dp))) {
                is UploadResult.Success -> Log.d(TAG, "Uploaded environment_location datapoint successfully: ${result.response}")
                is UploadResult.Failure -> Log.e(TAG, "Failed to upload environment_location datapoint: ${result.errorMessage}", result.exception)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading environment_location datapoint", e)
        }
    }

    private fun createNotification(contentText: String): Notification {
        createNotificationChannel()
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PIHS Location Tracking")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Location Tracking"
            val descriptionText = "Tracks significant location changes"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}