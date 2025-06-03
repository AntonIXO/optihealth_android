package org.devpins.pihs.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LocationManager"
        
        // Required permissions
        val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        
        // Background location permission (separate because it requires special handling)
        const val BACKGROUND_LOCATION_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    }
    
    /**
     * Checks if all required permissions are granted
     */
    fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Checks if background location permission is granted
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
     * Registers permission request launchers
     */
    fun registerPermissionLaunchers(
        activity: Activity,
        onRequiredPermissionsResult: (Boolean) -> Unit,
        onBackgroundPermissionResult: (Boolean) -> Unit
    ): Pair<ActivityResultLauncher<Array<String>>, ActivityResultLauncher<String>> {
        
        // Launcher for required permissions
        val requiredPermissionsLauncher = (activity as androidx.activity.ComponentActivity)
            .registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val allGranted = permissions.entries.all { it.value }
                onRequiredPermissionsResult(allGranted)
            }
        
        // Launcher for background location permission (needs to be requested separately)
        val backgroundPermissionLauncher = activity
            .registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                onBackgroundPermissionResult(isGranted)
            }
        
        return Pair(requiredPermissionsLauncher, backgroundPermissionLauncher)
    }
    
    /**
     * Requests required permissions
     */
    fun requestRequiredPermissions(permissionLauncher: ActivityResultLauncher<Array<String>>) {
        permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }
    
    /**
     * Requests background location permission
     * Note: This should only be called after the user has granted the required permissions
     */
    fun requestBackgroundLocationPermission(permissionLauncher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(BACKGROUND_LOCATION_PERMISSION)
        }
    }
    
    /**
     * Creates an intent to open the app settings
     */
    fun getAppSettingsIntent(): Intent {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", context.packageName, null)
        intent.data = uri
        return intent
    }
    
    /**
     * Starts location tracking service
     */
    fun startLocationTracking() {
        Log.d(TAG, "Starting location tracking service")
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_LOCATION_TRACKING
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
    
    /**
     * Stops location tracking service
     */
    fun stopLocationTracking() {
        Log.d(TAG, "Stopping location tracking service")
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_LOCATION_TRACKING
        }
        context.startService(intent)
    }
    
    /**
     * Checks if all permissions are granted and starts location tracking
     * Returns true if tracking was started, false otherwise
     */
    fun checkPermissionsAndStartTracking(): Boolean {
        if (!hasRequiredPermissions()) {
            Log.d(TAG, "Cannot start tracking: Missing required permissions")
            return false
        }
        
        if (!hasBackgroundLocationPermission()) {
            Log.d(TAG, "Cannot start tracking: Missing background location permission")
            return false
        }
        
        startLocationTracking()
        return true
    }
}