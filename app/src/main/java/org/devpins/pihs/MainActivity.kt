package org.devpins.pihs

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.devpins.pihs.health.HealthConnectAvailability
import org.devpins.pihs.health.HealthRepository
import org.devpins.pihs.health.SyncStatus
import org.devpins.pihs.ui.theme.PIHSTheme
import javax.inject.Inject

// Hardcoded Supabase credentials
private const val SUPABASE_URL = "https://gdvghuytkemlslumyeov.supabase.co"
private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdkdmdodXl0a2VtbHNsdW15ZW92Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDg4NzM0NTAsImV4cCI6MjA2NDQ0OTQ1MH0.HcdUWxm2Rbypxk8gIXrLkLvOxQg6BDpMF72eE4DXxQA"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var healthRepository: HealthRepository

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("HealthConnect", "MainActivity: onCreate")

        // Initialize Health Repository
        coroutineScope.launch {
            Log.d("HealthConnect", "MainActivity: Initializing health repository")
            healthRepository.initialize()
            Log.d("HealthConnect", "MainActivity: Health repository initialized")
        }

        enableEdgeToEdge()
        setContent {
            val scope = rememberCoroutineScope()

            // Health Connect states
            val healthConnectAvailability by healthRepository.healthConnectAvailability.collectAsState()
            val permissionsGranted by healthRepository.permissionsGranted.collectAsState()
            val syncStatus by healthRepository.syncStatus.collectAsState()

            // Permission launcher
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = healthRepository.getPermissionRequestContract(),
                onResult = { permissions ->
                    Log.d("HealthConnect", "MainActivity: Permission result received: $permissions")
                    scope.launch {
                        Log.d("HealthConnect", "MainActivity: Handling permission result")
                        healthRepository.handlePermissionResult(permissions)
                        Log.d("HealthConnect", "MainActivity: Permission result handled")
                    }
                }
            )

            PIHSTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onGoogleSignInClick = { 
                            scope.launch {
                                signInWithGoogle()
                            }
                        },
                        healthConnectAvailability = healthConnectAvailability,
                        permissionsGranted = permissionsGranted,
                        syncStatus = syncStatus,
                        onRequestPermissions = {
                            Log.d("HealthConnect", "MainActivity: Request permissions button clicked")
                            val permissions = healthRepository.getPermissionsToRequest()
                            Log.d("HealthConnect", "MainActivity: Launching permission request for: $permissions")
                            permissionLauncher.launch(permissions)
                        },
                        onOpenHealthConnect = {
                            Log.d("HealthConnect", "MainActivity: Open Health Connect button clicked")
                            val intent = healthRepository.getHealthConnectSettingsIntent()
                            Log.d("HealthConnect", "MainActivity: Starting Health Connect settings activity with intent: $intent")
                            startActivity(intent)
                        },
                        onSyncData = {
                            Log.d("HealthConnect", "MainActivity: Sync data button clicked")
                            scope.launch {
                                Log.d("HealthConnect", "MainActivity: Starting health data sync")
                                healthRepository.syncHealthData()
                                Log.d("HealthConnect", "MainActivity: Health data sync completed")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Note: Deeplink handling will be implemented according to the Supabase documentation
        // This is a placeholder for now
    }

    private suspend fun signInWithGoogle() {
        try {
            // Launch Google sign-in flow
            supabaseClient.auth.signInWith(Google)
            Log.d("LOGIN", "Google sign-in initiated")
        } catch (e: Exception) {
            Log.e("LOGIN", "Google Sign-In error: ${e.message}")
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onGoogleSignInClick: () -> Unit,
    healthConnectAvailability: HealthConnectAvailability,
    permissionsGranted: Boolean,
    syncStatus: SyncStatus,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onSyncData: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "PIHS - Personalized Insight Health Server",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Authentication Section
        Text(
            text = "Authentication",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onGoogleSignInClick) {
            Text(text = "Sign in with Google")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Health Connect Section
        Text(
            text = "Health Connect",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Health Connect Availability
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Health Connect Status: ",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = when(healthConnectAvailability) {
                    HealthConnectAvailability.INSTALLED -> "Installed"
                    HealthConnectAvailability.NOT_INSTALLED -> "Not Installed"
                    HealthConnectAvailability.UNKNOWN -> "Checking..."
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = when(healthConnectAvailability) {
                    HealthConnectAvailability.INSTALLED -> MaterialTheme.colorScheme.primary
                    HealthConnectAvailability.NOT_INSTALLED -> MaterialTheme.colorScheme.error
                    HealthConnectAvailability.UNKNOWN -> MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Health Connect Permissions
        if (healthConnectAvailability == HealthConnectAvailability.INSTALLED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Permissions: ",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (permissionsGranted) "Granted" else "Not Granted",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (permissionsGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Health Connect Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRequestPermissions,
                    enabled = !permissionsGranted
                ) {
                    Text(text = "Request Permissions")
                }

                Button(
                    onClick = onOpenHealthConnect
                ) {
                    Text(text = "Open Health Connect")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sync Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync Status: ",
                    style = MaterialTheme.typography.bodyMedium
                )
                when (syncStatus) {
                    is SyncStatus.Idle -> Text(
                        text = "Ready",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    is SyncStatus.Syncing -> {
                        Text(
                            text = "Syncing...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    }
                    is SyncStatus.Success -> Text(
                        text = "Success",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    is SyncStatus.Error -> Text(
                        text = "Error: ${(syncStatus as SyncStatus.Error).message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sync Button
            Button(
                onClick = onSyncData,
                enabled = permissionsGranted && syncStatus !is SyncStatus.Syncing
            ) {
                Text(text = "Sync Health Data")
            }
        } else if (healthConnectAvailability == HealthConnectAvailability.NOT_INSTALLED) {
            Text(
                text = "Health Connect is not installed on this device. Please install it from the Play Store.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
