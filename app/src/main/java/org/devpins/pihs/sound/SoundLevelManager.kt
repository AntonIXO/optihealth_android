package org.devpins.pihs.sound

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.devpins.pihs.stats.PihsDataPoint // Adjusted import
import org.devpins.pihs.utils.PermissionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SoundLevelManager {

    private const val TAG = "SoundLevelManager"

    // Constants for metric names and tags
    const val METRIC_NAME_PERIODIC = "sound_level_periodic"
    const val METRIC_NAME_LOCATION_CHANGE = "sound_level_location_change"
    // Add more metric names as needed, e.g., for specific events

    const val TAG_PERIODIC = "PERIODIC_SOUND_MONITORING"
    const val TAG_LOCATION_CHANGE = "LOCATION_CHANGE_SOUND_MONITORING"
    // Add more tags as needed

    /**
     * Checks if the RECORD_AUDIO permission has been granted.
     *
     * @param context The application context.
     * @return True if permission is granted, false otherwise.
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Requests the RECORD_AUDIO permission from the user.
     * This function delegates to PermissionManager.
     *
     * @param activity The activity context to request permission.
     * @param callback Invoked with true if permission is granted/already was, false otherwise (or if request is initiated).
     */
    fun requestRecordAudioPermission(activity: Activity, callback: (Boolean) -> Unit) {
        PermissionManager.requestRecordAudioPermission(activity, callback)
        // Note: The actual grant result for a new request is handled via Activity.onRequestPermissionsResult,
        // which should then call PermissionManager.handleRequestPermissionsResult.
        // The callback here will immediately reflect pre-existing permission status or that a request was made.
    }

    /**
     * Placeholder function to measure sound level.
     * This will eventually use MediaRecorder.
     *
     * @param context The application context.
     * @param callback Invoked with the measured sound level (or null if permission denied/error).
     */
    fun measureSoundLevel(context: Context, callback: (Double?) -> Unit) {
        if (!hasRecordAudioPermission(context)) {
            Log.w(TAG, "Record audio permission not granted. Cannot measure sound level.")
            callback(null)
            return
        }

        // TODO: Implement actual sound level measurement using MediaRecorder
        // For now, log a message and return a dummy value.
        Log.d(TAG, "measureSoundLevel: Placeholder - actual measurement not implemented yet.")
        // Simulate some delay or async operation if needed in future
        val dummySoundLevel = 0.0 // Placeholder value
        callback(dummySoundLevel)
    }

    /**
     * Formats sound data into a PihsDataPoint.
     *
     * @param timestamp ISO 8601 UTC string.
     * @param soundLevel The measured sound level (e.g., in dB).
     * @param eventType A string describing the event that triggered the measurement (e.g., METRIC_NAME_PERIODIC).
     * @param userId The user identifier.
     * @param metricSourceId Identifier for the source of this metric (e.g., device ID).
     * @return PihsDataPoint formatted for Supabase.
     */
    fun formatSoundData(
        timestamp: String, // Expecting ISO 8601 UTC from caller
        soundLevel: Double,
        eventType: String, // e.g., METRIC_NAME_PERIODIC
        userId: String,
        metricSourceId: String
    ): PihsDataPoint {
        return PihsDataPoint(
            timestamp = timestamp,
            metric_name = eventType, // Using eventType as the metricName, e.g., "sound_level_periodic"
            value_numeric = soundLevel,
            metric_source_id = metricSourceId,
            user_id = userId,
            tags = listOf(eventType),
            unit = TODO(),
            value_json = TODO() // Using eventType also as a tag, or define specific tags like TAG_PERIODIC
        )
    }

    // Helper function to get current timestamp if needed by caller of formatSoundData
    fun getCurrentUTCTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
