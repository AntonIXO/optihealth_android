package org.devpins.pihs

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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

    // Simple flag to track login stat

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


        enableEdgeToEdge()
        setContent {
            val scope = rememberCoroutineScope()

            // Health Connect states
            val healthConnectAvailability by healthRepository.healthConnectAvailability.collectAsState()
            val permissionsGranted by healthRepository.permissionsGranted.collectAsState()
            val syncStatus by healthRepository.syncStatus.collectAsState()
            val lastSyncTime by healthRepository.lastSyncTime.collectAsState()

            // Authentication state
            val sessionStatus by supabaseClient.auth.sessionStatus.collectAsState()
            val isLoggedIn = sessionStatus is SessionStatus.Authenticated

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
                        lastSyncTime = lastSyncTime,
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
    lastSyncTime: String?,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onSyncData: () -> Unit,
    isLoggedIn: Boolean = false,
    supabaseClient: SupabaseClient
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
            // App Header
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

            // Authentication Section
            AuthenticationCard(
                isLoggedIn = isLoggedIn,
                supabaseClient = supabaseClient
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Health Connect Section
            HealthConnectCard(
                healthConnectAvailability = healthConnectAvailability,
                permissionsGranted = permissionsGranted,
                syncStatus = syncStatus,
                lastSyncTime = lastSyncTime,
                onRequestPermissions = onRequestPermissions,
                onOpenHealthConnect = onOpenHealthConnect,
                onSyncData = onSyncData
            )
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
            // Card Header
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

            // Login Status
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

            // Sign-in Buttons
            if (!isLoggedIn) {
                GoogleSignInButtonStyled(supabaseClient = supabaseClient)
            } else {
                GoogleSignOutButtonStyled(supabaseClient = supabaseClient)
            }
        }
    }
}

@Composable
fun HealthConnectCard(
    healthConnectAvailability: HealthConnectAvailability,
    permissionsGranted: Boolean,
    syncStatus: SyncStatus,
    lastSyncTime: String?,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onSyncData: () -> Unit
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
            // Card Header
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

            // Health Connect Status
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
                // Permissions Status
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

                // Health Connect Actions
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

                // Sync Status
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = when (syncStatus) {
                        is SyncStatus.Idle -> MaterialTheme.colorScheme.surfaceVariant
                        is SyncStatus.Syncing -> MaterialTheme.colorScheme.surfaceVariant
                        is SyncStatus.Success -> MaterialTheme.colorScheme.primaryContainer
                        is SyncStatus.Error -> MaterialTheme.colorScheme.errorContainer
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

                Spacer(modifier = Modifier.height(16.dp))

                // Last Sync Time
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
                                    val instant = java.time.Instant.parse(lastSyncTime)
                                    val formatter = java.time.format.DateTimeFormatter
                                        .ofPattern("MMM dd, yyyy HH:mm")
                                        .withZone(java.time.ZoneId.systemDefault())
                                    formatter.format(instant)
                                } catch (e: Exception) {
                                    lastSyncTime // Fallback to raw timestamp if parsing fails
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Sync Button
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
                    Text(text = "Sync Health Data")
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

// Styled versions of the Google sign-in buttons
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
