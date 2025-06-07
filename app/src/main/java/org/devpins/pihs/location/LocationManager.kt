package org.devpins.pihs.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages location-related functionalities including permission checking,
 * requesting permissions, and starting/stopping the location tracking service.
 * It handles differences in permission requirements based on Android SDK version.
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
         * For Android 13 (TIRAMISU) and above, this includes [Manifest.permission.POST_NOTIFICATIONS]
         * in addition to [Manifest.permission.ACCESS_FINE_LOCATION] and [Manifest.permission.ACCESS_COARSE_LOCATION].
         * For older versions, it only includes fine and coarse location permissions.
         */
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
        /**
         * Constant for the background location permission ([Manifest.permission.ACCESS_BACKGROUND_LOCATION]).
         * This permission is requested separately for Android Q (10) and above.
         */
        const val BACKGROUND_LOCATION_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    }

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
     * Registers activity result launchers for handling permission requests for both
     * required foreground permissions and the background location permission.
     *
     * @param activity The [Activity] (must be a [androidx.activity.ComponentActivity]) to register the launchers with.
     * @param onRequiredPermissionsResult A callback function that receives a boolean indicating if all required foreground permissions were granted.
     * @param onBackgroundPermissionResult A callback function that receives a boolean indicating if the background location permission was granted.
     * @return A [Pair] containing two [ActivityResultLauncher] instances:
     *         - The first launcher is for the array of [REQUIRED_PERMISSIONS].
     *         - The second launcher is for the single [BACKGROUND_LOCATION_PERMISSION].
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
     * Requests the set of [REQUIRED_PERMISSIONS] using the provided launcher.
     *
     * @param permissionLauncher The [ActivityResultLauncher] for an array of permission strings.
     */
    fun requestRequiredPermissions(permissionLauncher: ActivityResultLauncher<Array<String>>) {
        permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    /**
     * Requests the [BACKGROUND_LOCATION_PERMISSION] using the provided launcher.
     * This should only be called after [REQUIRED_PERMISSIONS] have been granted.
     * This function has an effect only on Android Q (10) and above.
     *
     * @param permissionLauncher The [ActivityResultLauncher] for a single permission string.
     */
    fun requestBackgroundLocationPermission(permissionLauncher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(BACKGROUND_LOCATION_PERMISSION)
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
     * Starts the [LocationTrackingService].
     * For Android O (8.0) and above, it starts the service as a foreground service.
     * For older versions, it uses `startService`.
     */
    fun startLocationTracking() {
        Log.i(TAG, "Starting location tracking service")
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
     * Stops the [LocationTrackingService].
     */
    fun stopLocationTracking() {
        Log.i(TAG, "Stopping location tracking service")
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_LOCATION_TRACKING
        }
        context.startService(intent)
    }

    /**
     * Checks if both required foreground and background location permissions are granted.
     * If they are, it starts the location tracking service.
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

    /**
     * Checks if the app is currently ignoring battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true // Before Marshmallow, this concept didn't exist in this form.
    }

    /**
     * Creates an intent to request the user to disable battery optimizations for the app.
     * Returns null if the Android version is below Marshmallow (API 23).
     */
    fun getRequestIgnoreBatteryOptimizationsIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${context.packageName}")
            return intent
        }
        return null
    }
}