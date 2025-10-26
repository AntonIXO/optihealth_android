package org.devpins.pihs

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch // Added import for Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.android.identity.util.UUID
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.devpins.pihs.health.HealthConnectAvailability
import org.devpins.pihs.health.HealthRepository
import org.devpins.pihs.health.SyncStatus
import org.devpins.pihs.location.LocationManager
import org.devpins.pihs.location.LocationTrackingCard
import org.devpins.pihs.stats.UsageStatsHelper // Import UsageStatsHelper
import org.devpins.pihs.ui.theme.PIHSTheme
import org.devpins.pihs.ui.viewmodel.ExampleHealthViewModel
import org.devpins.pihs.settings.SettingsKeys
import org.devpins.pihs.neiry.NeiryManager
import org.devpins.pihs.ui.navigation.NavigationHost
import com.gelo.capsule.CapsuleNative
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val PREF_KEY_TRACKING_ACTIVE = "is_tracking_active"
private const val SETTINGS_PREFS = "AppSettings"
private const val KEY_ENABLE_USAGE = "enable_usage_tracking"
private const val KEY_ENABLE_LOCATION = "enable_location_tracking"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var healthRepository: HealthRepository

    @Inject
    lateinit var locationManager: LocationManager

    private val exampleHealthViewModel: ExampleHealthViewModel by viewModels()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private lateinit var requiredLocationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var backgroundLocationPermissionLauncher: ActivityResultLauncher<String>

    private val sharedPreferences by lazy {
        getSharedPreferences("LocationTrackingPrefs", Context.MODE_PRIVATE)
    }

    private val settingsPreferences by lazy {
        getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("HealthConnect", "MainActivity: onCreate")
        coroutineScope.launch {
            Log.d("HealthConnect", "MainActivity: Initializing health repository")
            healthRepository.initialize()
            Log.d("HealthConnect", "MainActivity: Health repository initialized")
        }
        enableEdgeToEdge()

        var hasLocationPermissions by mutableStateOf(locationManager.hasRequiredPermissions())
        var hasBackgroundLocationPermission by mutableStateOf(locationManager.hasBackgroundLocationPermission())

        val (requiredLauncher, backgroundLauncher) = locationManager.registerPermissionLaunchers(
            activity = this,
            onRequiredPermissionsResult = { allGranted ->
                Log.d("PermissionRequest", "Required permissions granted: $allGranted")
                hasLocationPermissions = allGranted
                // Check background permission status again, as granting fine/coarse might affect it
                hasBackgroundLocationPermission = locationManager.hasBackgroundLocationPermission()
            },
            onBackgroundPermissionResult = { granted ->
                Log.d("PermissionRequest", "Background permission granted: $granted")
                hasBackgroundLocationPermission = granted
            }
        )
        requiredLocationPermissionLauncher = requiredLauncher
        backgroundLocationPermissionLauncher = backgroundLauncher

        setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current

            val healthConnectAvailability by healthRepository.healthConnectAvailability.collectAsState()
            val permissionsGranted by healthRepository.permissionsGranted.collectAsState()
            val syncStatus by healthRepository.syncStatus.collectAsState()
            val lastSyncTime by healthRepository.lastSyncTime.collectAsState()

            val sessionStatus by supabaseClient.auth.sessionStatus.collectAsState()
            val isLoggedIn = sessionStatus is SessionStatus.Authenticated

            // The state variables are already declared above setContent
            // var hasLocationPermissions by remember { mutableStateOf(locationManager.hasRequiredPermissions()) }
            // var hasBackgroundLocationPermission by remember { mutableStateOf(locationManager.hasBackgroundLocationPermission()) }
            var isLocationTrackingActive by remember {
                mutableStateOf(sharedPreferences.getBoolean(PREF_KEY_TRACKING_ACTIVE, false))
            }
            var isIgnoringBatteryOptimizations by remember { mutableStateOf(locationManager.isIgnoringBatteryOptimizations()) }

            var isUsageTrackingEnabled by remember { mutableStateOf(settingsPreferences.getBoolean(KEY_ENABLE_USAGE, true)) }
            var isLocationFeatureEnabled by remember { mutableStateOf(settingsPreferences.getBoolean(KEY_ENABLE_LOCATION, true)) }
            var isNeiryEnabled by remember { mutableStateOf(settingsPreferences.getBoolean(SettingsKeys.KEY_ENABLE_NEIRY, false)) }
            var showTestUpload by remember { mutableStateOf(settingsPreferences.getBoolean(SettingsKeys.KEY_SHOW_TEST_UPLOAD, false)) }

            val healthPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = healthRepository.getPermissionRequestContract(),
                onResult = { permissions ->
                    scope.launch {
                        healthRepository.handlePermissionResult(permissions)
                    }
                }
            )

            val appSettingsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                onResult = {
                    hasLocationPermissions = locationManager.hasRequiredPermissions()
                    hasBackgroundLocationPermission = locationManager.hasBackgroundLocationPermission()
                    isIgnoringBatteryOptimizations = locationManager.isIgnoringBatteryOptimizations()
                }
            )

            val batteryOptimizationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                onResult = {
                    // Refresh the state after returning from the settings screen
                    isIgnoringBatteryOptimizations = locationManager.isIgnoringBatteryOptimizations()
                    Log.d("MainActivity", "Returned from battery optimization settings. Is ignoring: $isIgnoringBatteryOptimizations")
                }
            )

            val settingsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                onResult = {
                    isUsageTrackingEnabled = settingsPreferences.getBoolean(KEY_ENABLE_USAGE, false)
                    isLocationFeatureEnabled = settingsPreferences.getBoolean(KEY_ENABLE_LOCATION, true)
                    isNeiryEnabled = settingsPreferences.getBoolean(SettingsKeys.KEY_ENABLE_NEIRY, false)
                    showTestUpload = settingsPreferences.getBoolean(SettingsKeys.KEY_SHOW_TEST_UPLOAD, false)
                }
            )

            val onRequestIgnoreBatteryOptimizations = {
                val intent = locationManager.getRequestIgnoreBatteryOptimizationsIntent()
                if (intent != null) {
                    batteryOptimizationLauncher.launch(intent)
                } else {
                    Toast.makeText(context, "Battery optimization settings not available on this Android version.", Toast.LENGTH_LONG).show()
                    Log.w("MainActivity", "Battery optimization intent is null.")
                }
                Unit // Explicitly return Unit
            }

            PIHSTheme {
                NavigationHost(
                    healthConnectAvailability = healthConnectAvailability,
                    permissionsGranted = permissionsGranted,
                    syncStatus = syncStatus,
                    lastSyncTime = lastSyncTime,
                    onRequestPermissions = {
                        val permissions = healthRepository.getPermissionsToRequest()
                        healthPermissionLauncher.launch(permissions)
                    },
                    onOpenHealthConnect = {
                        val intent = healthRepository.getHealthConnectSettingsIntent()
                        startActivity(intent)
                    },
                    onSyncData = {
                        scope.launch {
                            healthRepository.syncHealthData()
                        }
                    },
                    onSyncDataInRange = { startTime, endTime ->
                        scope.launch {
                            healthRepository.syncHealthDataInRange(startTime, endTime)
                        }
                    },
                    onCancelSync = {
                        healthRepository.cancelSync()
                    },
                    isLoggedIn = isLoggedIn,
                    supabaseClient = supabaseClient,
                    hasLocationPermissions = hasLocationPermissions,
                    hasBackgroundLocationPermission = hasBackgroundLocationPermission,
                    isLocationTrackingActive = isLocationTrackingActive,
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    showLocationFeature = isLocationFeatureEnabled,
                    showUsageFeature = isUsageTrackingEnabled,
                    showNeiryFeature = isNeiryEnabled,
                    showTestUpload = showTestUpload,
                    onOpenSettings = {
                        val intent = Intent(context, org.devpins.pihs.settings.SettingsActivity::class.java)
                        settingsLauncher.launch(intent)
                    },
                    onRequestLocationPermissions = {
                        if (!hasLocationPermissions) {
                            Log.d("PermissionRequest", "Requesting required foreground permissions.")
                            locationManager.requestRequiredPermissions(requiredLocationPermissionLauncher)
                        } else if (!hasBackgroundLocationPermission) {
                            Log.d("PermissionRequest", "Requesting background location permission.")
                            locationManager.requestBackgroundLocationPermission(backgroundLocationPermissionLauncher)
                        } else {
                            Toast.makeText(context, "All location permissions already granted.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRequestIgnoreBatteryOptimizations = onRequestIgnoreBatteryOptimizations,
                    onStartLocationTracking = {
                        locationManager.startLocationTracking()
                        isLocationTrackingActive = true
                        with(sharedPreferences.edit()) {
                            putBoolean(PREF_KEY_TRACKING_ACTIVE, true)
                            apply()
                        }
                        Log.d("MainActivity", "Location tracking started and state saved.")
                    },
                    onStopLocationTracking = {
                        locationManager.stopLocationTracking()
                        isLocationTrackingActive = false
                        with(sharedPreferences.edit()) {
                            putBoolean(PREF_KEY_TRACKING_ACTIVE, false)
                            apply()
                        }
                        Log.d("MainActivity", "Location tracking stopped and state saved.")
                    },
                    onOpenAppSettings = {
                        appSettingsLauncher.launch(locationManager.getAppSettingsIntent())
                    },
                    onSyncUsageData = {
                        // Actual sync logic in UsageStatsCard for better state management there
                    },
                    onUploadSampleData = {
                        exampleHealthViewModel.collectAndUploadSampleData()
                    },
                    onUploadEmptyData = {
                        exampleHealthViewModel.uploadEmptyData()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Note: Deeplink handling will be implemented according to the Supabase documentation
    }
}
