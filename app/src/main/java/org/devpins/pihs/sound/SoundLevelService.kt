package org.devpins.pihs.sound

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.devpins.pihs.MainActivity // Assuming R is in org.devpins.pihs
import org.devpins.pihs.R
import org.devpins.pihs.stats.PihsDataPoint
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class SoundLevelService : Service() {

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var auth: Auth

    private lateinit var serviceScope: CoroutineScope
    private var soundMetricSourceId: String? = null // To store the fetched/created metric source ID

    // Handler for periodic measurements
    private val periodicHandler = Handler(Looper.getMainLooper())
    private var periodicRunnable: Runnable? = null
    private val PERIODIC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes, example

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        createNotificationChannel()
        Log.d(TAG, "SoundLevelService Created")

        // Initialize SoundLevelManager (it's an object, so no instance needed, but good to log)
        Log.d(TAG, "SoundLevelManager initialized implicitly.")

        // Placeholder: Fetch or create metric source ID on service creation
        serviceScope.launch {
            val userId = auth.currentUserOrNull()?.id
            if (userId != null) {
                soundMetricSourceId = getOrCreateSoundMetricSource(
                    userId = userId,
                    client = supabaseClient,
                    sourceIdentifier = "SOUND_LEVEL_SERVICE_DEVICE_ID", // This should be a unique device ID
                    sourceName = "Sound Level Monitoring"
                )
                Log.d(TAG, "Sound Metric Source ID: $soundMetricSourceId")
            } else {
                Log.e(TAG, "User not logged in, cannot fetch/create sound metric source.")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand received action: $action")

        when (action) {
            ACTION_START_SOUND_MONITORING -> {
                startForegroundService()
                startPeriodicSoundMeasurements()
                Log.i(TAG, "Started sound monitoring.")
            }
            ACTION_STOP_SOUND_MONITORING -> {
                stopPeriodicSoundMeasurements()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.i(TAG, "Stopped sound monitoring.")
            }
            ACTION_MEASURE_SOUND_LOCATION_CHANGE -> {
                val sourceDetails = intent.getStringExtra("source_details") ?: "Unknown Location Change"
                Log.d(TAG, "Triggered by location change: $sourceDetails")
                if (SoundLevelManager.hasRecordAudioPermission(this)) {
                     measureAndLogSound(
                         eventType = SoundLevelManager.METRIC_NAME_LOCATION_CHANGE,
                         metricSourceDetails = sourceDetails
                     )
                } else {
                    Log.w(TAG, "Cannot measure sound for location change: RECORD_AUDIO permission not granted.")
                    // Optionally, request permission here or notify user
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sound Level Monitoring")
            .setContentText("Monitoring sound levels in the background.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with actual icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Sound Level Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun startPeriodicSoundMeasurements() {
        if (!SoundLevelManager.hasRecordAudioPermission(this)) {
            Log.w(TAG, "Cannot start periodic sound measurements: RECORD_AUDIO permission not granted.")
            // Optionally, request permission or stop service if essential
            return
        }
        stopPeriodicSoundMeasurements() // Stop any existing before starting new
        periodicRunnable = Runnable {
            measureAndLogSound(
                eventType = SoundLevelManager.METRIC_NAME_PERIODIC,
                metricSourceDetails = "Periodic Measurement"
            )
            periodicHandler.postDelayed(periodicRunnable!!, PERIODIC_INTERVAL_MS)
        }
        periodicHandler.post(periodicRunnable!!)
        Log.d(TAG, "Periodic sound measurements started.")
    }

    private fun stopPeriodicSoundMeasurements() {
        periodicRunnable?.let { periodicHandler.removeCallbacks(it) }
        periodicRunnable = null
        Log.d(TAG, "Periodic sound measurements stopped.")
    }

    fun measureAndLogSound(eventType: String, metricSourceDetails: String) {
        val currentUserId = auth.currentUserOrNull()?.id
        if (currentUserId == null) {
            Log.e(TAG, "User not logged in. Cannot log sound data.")
            return
        }
        if (soundMetricSourceId == null) {
            Log.e(TAG, "Sound metric source ID not available. Cannot log sound data.")
            // Optionally, try to fetch/create it again or queue data
            return
        }

        SoundLevelManager.measureSoundLevel(this) { soundLevel ->
            if (soundLevel != null) {
                val timestamp = SoundLevelManager.getCurrentUTCTimestamp()
                val dataPoint = SoundLevelManager.formatSoundData(
                    timestamp = timestamp,
                    soundLevel = soundLevel,
                    eventType = eventType,
                    userId = currentUserId,
                    metricSourceId = soundMetricSourceId!! // Use the fetched/created ID
                )
                // Add specific detail about the source if needed, e.g. location that triggered it
                // For now, metricSourceDetails is logged but not directly part of PihsDataPoint
                Log.d(TAG, "Measured sound for $eventType ($metricSourceDetails): $soundLevel dB")
                logSoundDataToSupabase(dataPoint)
            } else {
                Log.w(TAG, "Failed to measure sound level for $eventType ($metricSourceDetails). Permission might be missing or error in measurement.")
            }
        }
    }

    private fun logSoundDataToSupabase(dataPoint: PihsDataPoint) {
        serviceScope.launch {
//            try {
//                // Placeholder: Actual Supabase logging to be implemented
//                Log.i(TAG, "Attempting to log to Supabase: ${dataPoint.metricName} - ${dataPoint.metricValue}")
//                // supabaseClient.from("pihs_data_points").insert(dataPoint) // Example actual call
//                Log.d(TAG, "logSoundDataToSupabase: Data: $dataPoint")
//                // Simulate network delay or operation
//                // kotlinx.coroutines.delay(1000)
//                Log.i(TAG, "Placeholder: Supabase logging complete for ${dataPoint.metricName}.")
//            } catch (e: Exception) {
//                Log.e(TAG, "Error logging data to Supabase: ${e.message}", e)
//            }
        }
    }

    // Placeholder for fetching/creating metric source ID
    private suspend fun getOrCreateSoundMetricSource(
        userId: String,
        client: SupabaseClient,
        sourceIdentifier: String, // e.g., a unique device ID or service instance ID
        sourceName: String
    ): String {
        // This is a simplified placeholder.
        // In a real app, you'd query Supabase for an existing source with 'sourceIdentifier' for this user.
        // If not found, you'd create it and return its ID.
        // For now, we'll just log and return a new UUID or a fixed string for testing.
        val newSourceId = UUID.randomUUID().toString()
        Log.d(TAG, "Placeholder: getOrCreateSoundMetricSource called. User: $userId, Identifier: $sourceIdentifier, Name: $sourceName. Generated/Returning: $newSourceId")
        // Example (conceptual, actual implementation would involve Supabase calls):
        // val existing = client.from("metric_sources").select { filter { eq("user_id", userId); eq("source_identifier", sourceIdentifier) } }.decodeList<MetricSource>().firstOrNull()
        // if (existing != null) return existing.id
        // val newSource = client.from("metric_sources").insert(MetricSource(user_id = userId, source_identifier = sourceIdentifier, source_name = sourceName, ...)).decodeSingle<MetricSource>()
        // return newSource.id
        return newSourceId // Replace with actual logic
    }


    override fun onDestroy() {
        super.onDestroy()
        stopPeriodicSoundMeasurements()
        serviceScope.cancel()
        Log.d(TAG, "SoundLevelService Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not providing binding
    }

    companion object {
        const val TAG = "SoundLevelService"
        const val NOTIFICATION_ID = 2 // Different from other services if any
        const val CHANNEL_ID = "SoundLevelServiceChannel"

        const val ACTION_START_SOUND_MONITORING = "org.devpins.pihs.action.START_SOUND_MONITORING"
        const val ACTION_STOP_SOUND_MONITORING = "org.devpins.pihs.action.STOP_SOUND_MONITORING"
        const val ACTION_MEASURE_SOUND_LOCATION_CHANGE = "org.devpins.pihs.action.MEASURE_SOUND_LOCATION_CHANGE"
    }
}
