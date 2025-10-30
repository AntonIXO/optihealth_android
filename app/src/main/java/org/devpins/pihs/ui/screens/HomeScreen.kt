package org.devpins.pihs.ui.screens

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.launch
import org.devpins.pihs.health.HealthConnectAvailability
import org.devpins.pihs.health.SyncStatus
import org.devpins.pihs.location.LocationTrackingCard
import org.devpins.pihs.neiry.NeiryManager
import org.devpins.pihs.stats.UsageStatsHelper
import org.devpins.pihs.ui.components.AuthenticationCard
import org.devpins.pihs.ui.components.GoogleSignInButtonStyled
import org.devpins.pihs.ui.components.GoogleSignOutButtonStyled
import org.devpins.pihs.ui.components.HealthConnectCard
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
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
    showLocationFeature: Boolean = true,
    showUsageFeature: Boolean = true,
    showNeiryFeature: Boolean = false,
    showTestUpload: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onRequestLocationPermissions: () -> Unit = {},
    onStartLocationTracking: () -> Unit = {},
    onStopLocationTracking: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onSyncUsageData: () -> Unit,
    onUploadSampleData: () -> Unit = {},
    onUploadEmptyData: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
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
            AuthenticationCard(
                isLoggedIn = isLoggedIn,
                supabaseClient = supabaseClient
            )
            
            // Only show other cards when user is logged in
            if (isLoggedIn) {
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
                        onRequestPermissions = onRequestLocationPermissions,
                        onStartTracking = onStartLocationTracking,
                        onStopTracking = onStopLocationTracking,
                        onOpenAppSettings = onOpenAppSettings
                    )
                }
                if (showUsageFeature) {
                    Spacer(modifier = Modifier.height(16.dp))
                    UsageStatsCard(supabaseClient = supabaseClient)
                }
                if (showNeiryFeature) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NeiryCard()
                }
                if (showTestUpload) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ExampleHealthCard(
                        onUploadSampleData = onUploadSampleData,
                        onUploadEmptyData = onUploadEmptyData
                    )
                }
            }
        }
    }
}

@Composable
fun NeiryCard() {
    val context = LocalContext.current
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
                    imageVector = Icons.Filled.Face,
                    contentDescription = "Neiry Headband",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Neiry Headband",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val activity = (context as? Activity)
                        if (activity == null) {
                            Toast.makeText(context, "Unable to get Activity context", Toast.LENGTH_SHORT).show()
                        } else {
                            NeiryManager.initAndConnect(activity)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Connect Headband") }
                OutlinedButton(
                    onClick = { NeiryManager.disconnect(); Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Disconnect") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect to your Neiry BCI headband and print heart rate to logs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    imageVector = Icons.Filled.DateRange,
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
                            val metricSourceId = "YOUR_APP_USAGE_METRIC_SOURCE_ID"

                            try {
                                val dataPoints = UsageStatsHelper.getAndProcessUsageStats(
                                    context = context,
                                    date = LocalDate.now(),
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


