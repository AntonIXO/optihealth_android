package org.devpins.pihs

import android.R.attr.text
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.FragmentManager.TAG
import com.android.identity.util.UUID
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.devpins.pihs.health.HealthConnectAvailability
import org.devpins.pihs.health.HealthRepository
import org.devpins.pihs.health.SyncStatus
import org.devpins.pihs.ui.theme.PIHSTheme
import java.security.MessageDigest
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var healthRepository: HealthRepository

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // Simple flag to track login status
    private var isLoggedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("HealthConnect", "MainActivity: onCreate")

        // Initialize Health Repository
        coroutineScope.launch {
            Log.d("HealthConnect", "MainActivity: Initializing health repository")
            healthRepository.initialize()
            Log.d("HealthConnect", "MainActivity: Health repository initialized")
        }

        // Try to check login status
        try {
            // This is a simple check - in a real app, you'd want to properly check the session
            isLoggedIn = supabaseClient.auth != null
            Log.d("LOGIN", "Supabase client initialized, auth available: $isLoggedIn")
        } catch (e: Exception) {
            Log.e("LOGIN", "Error checking login status: ${e.message}")
            isLoggedIn = false
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
                        },
                        isLoggedIn = isLoggedIn,
                        supabaseClient = supabaseClient // Pass supabaseClient here
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
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    healthConnectAvailability: HealthConnectAvailability,
    permissionsGranted: Boolean,
    syncStatus: SyncStatus,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onSyncData: () -> Unit,
    isLoggedIn: Boolean = false,
    supabaseClient: SupabaseClient // Add supabaseClient as a parameter
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

        // Supabase Login Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Supabase Login Status: ",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (isLoggedIn) "Logged In" else "Not Logged In",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Google Sign-In Button
        GoogleSignInButton(supabaseClient = supabaseClient) // Pass supabaseClient here

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

@Composable
fun GoogleSignInButton(supabaseClient: SupabaseClient) { // Add supabaseClient as a parameter
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

    Button(onClick = onClick) { // Remove "this: RowScope"
        Text(text = "Sign in with Google")
    }
}