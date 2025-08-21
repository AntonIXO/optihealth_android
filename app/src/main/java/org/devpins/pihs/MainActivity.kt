package org.devpins.pihs

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
                    isUsageTrackingEnabled = settingsPreferences.getBoolean(KEY_ENABLE_USAGE, true)
                    isLocationFeatureEnabled = settingsPreferences.getBoolean(KEY_ENABLE_LOCATION, true)
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Note: Deeplink handling will be implemented according to the Supabase documentation
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    healthConnectAvailability: HealthConnectAvailability,
    permissionsGranted: Boolean,
    syncStatus: SyncStatus,
    lastSyncTime: String?,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onSyncData: () -> Unit,
    onSyncDataInRange: (Instant, Instant) -> Unit,
    onCancelSync: () -> Unit,
    isLoggedIn: Boolean = false,
    supabaseClient: SupabaseClient,
    hasLocationPermissions: Boolean = false,
    hasBackgroundLocationPermission: Boolean = false,
    isLocationTrackingActive: Boolean = false,
    isIgnoringBatteryOptimizations: Boolean = false,
    showLocationFeature: Boolean = true,
    showUsageFeature: Boolean = true,
    onOpenSettings: () -> Unit = {},
    onRequestLocationPermissions: () -> Unit = {},
    onRequestIgnoreBatteryOptimizations: () -> Unit = {},
    onStartLocationTracking: () -> Unit = {},
    onStopLocationTracking: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onSyncUsageData: () -> Unit, // Added for future direct calls if needed, but logic is in UsageStatsCard
    onUploadSampleData: () -> Unit = {}, // For ExampleHealthViewModel.collectAndUploadSampleData
    onUploadEmptyData: () -> Unit = {} // For ExampleHealthViewModel.uploadEmptyData
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "PIHS",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Personalized Insight Health Server",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(8.dp)) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Settings")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            AuthenticationCard(
                isLoggedIn = isLoggedIn,
                supabaseClient = supabaseClient
            )
            Spacer(modifier = Modifier.height(16.dp))
            HealthConnectCard(
                healthConnectAvailability = healthConnectAvailability,
                permissionsGranted = permissionsGranted,
                syncStatus = syncStatus,
                lastSyncTime = lastSyncTime,
                onRequestPermissions = onRequestPermissions,
                onOpenHealthConnect = onOpenHealthConnect,
                onSyncData = onSyncData,
                onSyncDataInRange = onSyncDataInRange,
                onCancelSync = onCancelSync
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (showLocationFeature) {
                LocationTrackingCard(
                    hasRequiredPermissions = hasLocationPermissions,
                    hasBackgroundPermission = hasBackgroundLocationPermission,
                    isTrackingActive = isLocationTrackingActive,
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    onRequestPermissions = onRequestLocationPermissions,
                    onRequestIgnoreBatteryOptimizations = onRequestIgnoreBatteryOptimizations,
                    onStartTracking = onStartLocationTracking,
                    onStopTracking = onStopLocationTracking,
                    onOpenAppSettings = onOpenAppSettings
                )
            }
            if (showUsageFeature) {
                Spacer(modifier = Modifier.height(16.dp))
                UsageStatsCard(supabaseClient = supabaseClient)
            }
            Spacer(modifier = Modifier.height(16.dp)) // Spacer before ExampleHealthCard
            ExampleHealthCard(
                onUploadSampleData = onUploadSampleData,
                onUploadEmptyData = onUploadEmptyData
            )
        }
    }
}

@Composable
fun ExampleHealthCard(
    onUploadSampleData: () -> Unit,
    onUploadEmptyData: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Data Upload Test",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Test Data Upload (zstd)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onUploadSampleData,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Upload Sample Health Data")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onUploadEmptyData,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Upload Empty Data (Test)")
            }
        }
    }
}


@Composable
fun UsageStatsCard(supabaseClient: SupabaseClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.DateRange, // Using Smartphone icon
                    contentDescription = "App Usage Stats",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "App Usage Tracking",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            val hasPermission = UsageStatsHelper.hasUsageStatsPermission(context)
                            if (!hasPermission) {
                                Toast.makeText(context, "Usage access permission required. Redirecting to settings...", Toast.LENGTH_LONG).show()
                                UsageStatsHelper.requestUsageStatsPermission(context)
                                isLoading = false
                                return@launch
                            }

                            val currentUser = supabaseClient.auth.currentUserOrNull()
                            if (currentUser == null) {
                                Toast.makeText(context, "Please log in to sync usage data.", Toast.LENGTH_LONG).show()
                                isLoading = false
                                return@launch
                            }
                            // IMPORTANT: Replace with your actual Metric Source ID from Supabase
                            // TODO: This metricSourceId is a placeholder from UI testing.
                            // The actual UsageDataSyncWorker creates/retrieves the correct ID.
                            // For a manual UI sync, this would need a more robust way to get the ID
                            // (e.g., by querying the metric_sources table based on USAGE_SOURCE_IDENTIFIER
                            // for the current user, similar to UsageDataSyncWorker).
                            val metricSourceId = "YOUR_APP_USAGE_METRIC_SOURCE_ID"

                            try {
                                val dataPoints = UsageStatsHelper.getAndProcessUsageStats(
                                    context = context,
                                    date = LocalDate.now(), // Syncs for the current day
                                    userId = currentUser.id,
                                    metricSourceId = metricSourceId
                                )
                                if (dataPoints.isNotEmpty()) {
                                    UsageStatsHelper.logDataToSupabase(dataPoints, supabaseClient)
                                    Toast.makeText(context, "App usage data synced successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No new app usage data to sync or permission issue.", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Log.e("UsageStatsSync", "Error syncing usage data", e)
                                Toast.makeText(context, "Error syncing app usage data: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sync App Usage Data for Today")
                }
            }
        }
    }
}


@Composable
fun AuthenticationCard(
    isLoggedIn: Boolean,
    supabaseClient: SupabaseClient
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "Authentication",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Authentication",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = if (isLoggedIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isLoggedIn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (isLoggedIn) "Logged In" else "Not Logged In",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isLoggedIn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (!isLoggedIn) {
                GoogleSignInButtonStyled(supabaseClient = supabaseClient)
            } else {
                GoogleSignOutButtonStyled(supabaseClient = supabaseClient)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectCard(
    healthConnectAvailability: HealthConnectAvailability,
    permissionsGranted: Boolean,
    syncStatus: SyncStatus,
    lastSyncTime: String?,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onSyncData: () -> Unit,
    onSyncDataInRange: (Instant, Instant) -> Unit,
    onCancelSync: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Health Connect",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Health Connect",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = when(healthConnectAvailability) {
                    HealthConnectAvailability.INSTALLED -> MaterialTheme.colorScheme.primaryContainer
                    HealthConnectAvailability.NOT_INSTALLED -> MaterialTheme.colorScheme.errorContainer
                    HealthConnectAvailability.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when(healthConnectAvailability) {
                            HealthConnectAvailability.INSTALLED -> MaterialTheme.colorScheme.onPrimaryContainer
                            HealthConnectAvailability.NOT_INSTALLED -> MaterialTheme.colorScheme.onErrorContainer
                            HealthConnectAvailability.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = when(healthConnectAvailability) {
                            HealthConnectAvailability.INSTALLED -> "Installed"
                            HealthConnectAvailability.NOT_INSTALLED -> "Not Installed"
                            HealthConnectAvailability.UNKNOWN -> "Checking..."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = when(healthConnectAvailability) {
                            HealthConnectAvailability.INSTALLED -> MaterialTheme.colorScheme.onPrimaryContainer
                            HealthConnectAvailability.NOT_INSTALLED -> MaterialTheme.colorScheme.onErrorContainer
                            HealthConnectAvailability.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (healthConnectAvailability == HealthConnectAvailability.INSTALLED) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (permissionsGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Permissions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (permissionsGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (permissionsGranted) "Granted" else "Not Granted",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (permissionsGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRequestPermissions,
                        enabled = !permissionsGranted,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = "Request Permissions")
                    }
                    FilledTonalButton(
                        onClick = onOpenHealthConnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = "Open Health Connect")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = when (syncStatus) {
                        is SyncStatus.Idle -> MaterialTheme.colorScheme.surfaceVariant
                        is SyncStatus.Syncing -> MaterialTheme.colorScheme.surfaceVariant
                        is SyncStatus.Success -> MaterialTheme.colorScheme.primaryContainer
                        is SyncStatus.Error -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant // Exhaustive when
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sync Status",
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (syncStatus) {
                                is SyncStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
                                is SyncStatus.Syncing -> MaterialTheme.colorScheme.onSurfaceVariant
                                is SyncStatus.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                                is SyncStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant // Exhaustive when
                            }
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (syncStatus) {
                                is SyncStatus.Idle -> Text(
                                    text = "Ready",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                is SyncStatus.Syncing -> {
                                    Text(
                                        text = "Syncing...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                }
                                is SyncStatus.Success -> Text(
                                    text = "Success",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                is SyncStatus.Error -> Text(
                                    text = "Error",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                else -> Text( // Exhaustive when
                                    text = "Unknown",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (syncStatus is SyncStatus.Error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: ${(syncStatus as SyncStatus.Error).message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (syncStatus is SyncStatus.Syncing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onCancelSync,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(text = "Cancel Sync")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (lastSyncTime != null) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Synced Until",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = try {
                                    val instant = Instant.parse(lastSyncTime)
                                    val formatter = DateTimeFormatter
                                        .ofPattern("MMM dd, yyyy HH:mm")
                                        .withZone(java.time.ZoneId.systemDefault())
                                    formatter.format(instant)
                                } catch (e: Exception) {
                                    Log.d("error", e.toString())
                                    lastSyncTime
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                var startDate by remember { mutableStateOf<LocalDate?>(null) }
                var endDate by remember { mutableStateOf<LocalDate?>(null) }
                var showStartDatePicker by remember { mutableStateOf(false) }
                var showEndDatePicker by remember { mutableStateOf(false) }
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Sync Date Range",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "From",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = startDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) ?: "Select date",
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showStartDatePicker = true },
                                    enabled = false
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "To",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = endDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) ?: "Select date",
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showEndDatePicker = true },
                                    enabled = false
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { 
                                    startDate = null
                                    endDate = null
                                },
                                enabled = startDate != null || endDate != null
                            ) {
                                Text("Clear")
                            }
                            TextButton(
                                onClick = { showStartDatePicker = true }
                            ) {
                                Text("Select Dates")
                            }
                        }
                    }
                }
                if (showStartDatePicker) {
                    val startDatePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = startDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                    )
                    DatePickerDialog(
                        onDismissRequest = { showStartDatePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    startDatePickerState.selectedDateMillis?.let { millis ->
                                        startDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                                        showStartDatePicker = false
                                        showEndDatePicker = true
                                    }
                                }
                            ) {
                                Text("OK")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showStartDatePicker = false }
                            ) {
                                Text("Cancel")
                            }
                        }
                    ) {
                        DatePicker(state = startDatePickerState)
                    }
                }
                if (showEndDatePicker) {
                    val endDatePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = endDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                            ?: LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    )
                    DatePickerDialog(
                        onDismissRequest = { showEndDatePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    endDatePickerState.selectedDateMillis?.let { endMillis ->
                                        endDate = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                                        showEndDatePicker = false
                                    }
                                }
                            ) {
                                Text("OK")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showEndDatePicker = false }
                            ) {
                                Text("Cancel")
                            }
                        }
                    ) {
                        DatePicker(state = endDatePickerState)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onSyncData,
                    enabled = permissionsGranted && syncStatus !is SyncStatus.Syncing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = "Sync All Health Data")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (startDate != null && endDate != null) {
                            val startInstant = startDate!!.atStartOfDay(ZoneId.systemDefault()).toInstant()
                            val endInstant = endDate!!.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                            onSyncDataInRange(startInstant, endInstant)
                        }
                    },
                    enabled = permissionsGranted && syncStatus !is SyncStatus.Syncing && startDate != null && endDate != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = "Sync Selected Date Range")
                }
            } else if (healthConnectAvailability == HealthConnectAvailability.NOT_INSTALLED) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "Health Connect is not installed on this device. Please install it from the Play Store.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GoogleSignInButtonStyled(supabaseClient: SupabaseClient) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val onClick: () -> Unit = {
        val credentialManager = CredentialManager.create(context)
        val rawNonce = UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it)}
        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId("421259338685-acb3ddfpdkgc055ejpil1bj5oltes1tu.apps.googleusercontent.com")
            .setNonce(hashedNonce)
            .build()
        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        coroutineScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = context,
                )
                val credential = result.credential
                val googleIdTokenCredential = GoogleIdTokenCredential
                    .createFrom(credential.data)
                val googleIdToken = googleIdTokenCredential.idToken
                supabaseClient.auth.signInWith(IDToken) {
                    idToken = googleIdToken
                    provider = Google
                    nonce = rawNonce
                }
                Log.i("test", googleIdToken)
                Toast.makeText(context, "You are signed in!", Toast.LENGTH_SHORT).show()
            } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            } catch (e: GoogleIdTokenParsingException) {
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = "Sign in with Google",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun GoogleSignOutButtonStyled(supabaseClient: SupabaseClient) {
    val coroutineScope = rememberCoroutineScope()
    val onClick: () -> Unit = {
        coroutineScope.launch {
            supabaseClient.auth.signOut(SignOutScope.LOCAL)
        }
    }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = ButtonDefaults.outlinedButtonBorder,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = "Sign out",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
