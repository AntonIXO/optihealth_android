package org.devpins.pihs.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BroadcastReceiver that listens for device boot completion.
 * Re-enqueues the location polling work after device reboot if tracking was previously active.
 * 
 * Note: WorkManager should automatically reschedule periodic work after reboot,
 * but this receiver provides an additional layer of reliability and allows us to
 * check user preferences if needed.
 */
@AndroidEntryPoint
class LocationBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LocationBootReceiver"
        private const val PREFS_NAME = "LocationTrackingPrefs"
        private const val KEY_TRACKING_ACTIVE = "is_tracking_active"
    }

    @Inject
    lateinit var locationManager: LocationManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed, checking location tracking status")
            
            // Check if tracking was previously active
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val wasTrackingActive = prefs.getBoolean(KEY_TRACKING_ACTIVE, false)
            
            if (wasTrackingActive) {
                Log.i(TAG, "Location tracking was active before reboot, re-enqueueing work")
                
                // Check if permissions are still granted
                if (locationManager.hasRequiredPermissions() && 
                    locationManager.hasBackgroundLocationPermission()) {
                    locationManager.startLocationTracking()
                    Log.i(TAG, "Location polling re-started after boot")
                } else {
                    Log.w(TAG, "Cannot restart location tracking: permissions not granted")
                }
            } else {
                Log.d(TAG, "Location tracking was not active before reboot, no action taken")
            }
        }
    }
}
