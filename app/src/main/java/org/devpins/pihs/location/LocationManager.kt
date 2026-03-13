package org.devpins.pihs.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages location-related functionalities including permission checking,
 * requesting permissions, and starting/stopping low-power location polling via WorkManager.
 * 
 * This manager uses WorkManager to periodically poll location with battery-efficient settings:
 * - Polls approximately every 30 minutes (inexact interval)
 * - Only runs when battery is not low and network is connected
 * - Uses PRIORITY_BALANCED_POWER_ACCURACY (no GPS, ~100m-300m accuracy)
 *
 * @property context The application context, injected by Hilt.
 */
@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LocationManager"

        /**
         * Array of required location permissions.
         * Prioritizes coarse location for battery efficiency.
         * ACCESS_COARSE_LOCATION is sufficient for PRIORITY_BALANCED_POWER_ACCURACY.
         */
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        // Background location permission (separate because it requires special handling)
        /**
         * Constant for the background location permission ([Manifest.permission.ACCESS_BACKGROUND_LOCATION]).
         * This permission is requested separately for Android Q (10) and above.
         * Required for location polling to work when app is not in use.
         */
        const val BACKGROUND_LOCATION_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION
        
        // WorkManager configuration
        private const val POLLING_INTERVAL_MINUTES = 30L
    }
    
    private val workManager = WorkManager.getInstance(context)

    /**
     * Checks if all permissions defined in [REQUIRED_PERMISSIONS] have been granted.
     *
     * @return `true` if all required permissions are granted, `false` otherwise.
     */
    fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if the background location permission has been granted.
     * For devices running Android versions older than Q (10), this check defaults to
     * whether all [REQUIRED_PERMISSIONS] are granted, as background location was
     * part of the standard location permissions.
     *
     * @return `true` if background location permission is granted (or if not applicable and foreground permissions are granted), `false` otherwise.
     */
    fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                BACKGROUND_LOCATION_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Before Android 10, background location is included in the fine location permission
            hasRequiredPermissions()
        }
    }

    /**
     * Creates an [Intent] to open the application's details settings screen.
     * This allows users to manually change app permissions if they have been permanently denied.
     *
     * @return An [Intent] to navigate to the app's settings page.
     */
    fun getAppSettingsIntent(): Intent {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", context.packageName, null)
        intent.data = uri
        return intent
    }

    /**
     * Starts low-power location polling using WorkManager.
     * Creates a periodic work request that runs approximately every 30 minutes.
     * The work is constrained to run only when:
     * - Device has network connectivity
     * - Battery is not low
     */
    fun startLocationTracking() {
        Log.i(TAG, "Starting location polling via WorkManager")
        
        // Define constraints for battery-efficient polling
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Only run if device has internet
            .setRequiresBatteryNotLow(true) // Do not run if battery is dying
            .build()
        
        // Create periodic work request with ~30 minute interval (inexact)
        val locationPollingRequest = PeriodicWorkRequestBuilder<LocationPollingWorker>(
            POLLING_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        
        // Enqueue the work with KEEP policy (keeps existing work if already scheduled)
        workManager.enqueueUniquePeriodicWork(
            LocationPollingWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            locationPollingRequest
        )
        
        Log.i(TAG, "Location polling work enqueued with KEEP policy")
    }

    /**
     * Stops location polling by canceling the WorkManager periodic work.
     */
    fun stopLocationTracking() {
        Log.i(TAG, "Stopping location polling via WorkManager")
        workManager.cancelUniqueWork(LocationPollingWorker.UNIQUE_WORK_NAME)
        Log.i(TAG, "Location polling work cancelled")
    }

    /**
     * Checks if location polling is currently active.
     * Note: This is a synchronous operation that queries WorkManager.
     * For UI usage, consider calling this from a coroutine or background thread.
     * @return true if the periodic work is enqueued or running, false otherwise
     */
    suspend fun isLocationTrackingActive(): Boolean {
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val workInfos = workManager.getWorkInfosForUniqueWork(LocationPollingWorker.UNIQUE_WORK_NAME).get()
                workInfos.any {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking location tracking status", e)
            false
        }
    }

    /**
     * Checks if both required foreground and background location permissions are granted.
     * If they are, it starts the location polling.
     *
     * @return `true` if all necessary permissions were granted and tracking was started, `false` otherwise.
     */
    fun checkPermissionsAndStartTracking(): Boolean {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Cannot start tracking: Missing required permissions")
            return false
        }
        
        if (!hasBackgroundLocationPermission()) {
            Log.w(TAG, "Cannot start tracking: Missing background location permission")
            return false
        }
        
        startLocationTracking()
        return true
    }
}