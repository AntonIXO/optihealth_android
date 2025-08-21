package org.devpins.pihs.settings

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
import kotlinx.coroutines.launch
import org.devpins.pihs.background.HealthDataSyncWorker
import org.devpins.pihs.background.UsageDataSyncWorker
import org.devpins.pihs.ui.theme.PIHSTheme
import java.security.MessageDigest
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val SETTINGS_PREFS = "AppSettings"
private const val KEY_ENABLE_HEALTH_SYNC = "enable_health_sync"
private const val KEY_ENABLE_USAGE_SYNC = "enable_usage_sync"

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var supabaseClient: SupabaseClient

    private val settingsPreferences by lazy {
        getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PIHSTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val sessionStatus by supabaseClient.auth.sessionStatus.collectAsState()
                        val isLoggedIn = sessionStatus is SessionStatus.Authenticated
                        SettingsScreen(
                            onToggleHealthSync = { enabled ->
                                settingsPreferences.edit().putBoolean(KEY_ENABLE_HEALTH_SYNC, enabled).apply()
                                if (enabled) {
                                    scheduleHealthDataSyncWorker(workManager)
                                } else {
                                    workManager.cancelUniqueWork(HealthDataSyncWorker.WORK_NAME)
                                }
                            },
                            onToggleUsageSync = { enabled ->
                                settingsPreferences.edit().putBoolean(KEY_ENABLE_USAGE_SYNC, enabled).apply()
                                if (enabled) {
                                    scheduleUsageDataSyncWorker(workManager)
                                } else {
                                    workManager.cancelUniqueWork(UsageDataSyncWorker.WORK_NAME)
                                }
                            },
                            isHealthSyncEnabled = settingsPreferences.getBoolean(KEY_ENABLE_HEALTH_SYNC, true),
                            isUsageSyncEnabled = settingsPreferences.getBoolean(KEY_ENABLE_USAGE_SYNC, true),
                            isLoggedIn = isLoggedIn,
                            supabaseClient = supabaseClient
                        )
                    }
                }
            }
        }
    }

    private fun scheduleHealthDataSyncWorker(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()

        val healthSyncRequest =
            PeriodicWorkRequestBuilder<HealthDataSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .addTag(HealthDataSyncWorker.WORK_NAME)
                .build()

        workManager.enqueueUniquePeriodicWork(
            HealthDataSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            healthSyncRequest
        )
    }

    private fun scheduleUsageDataSyncWorker(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()

        val usageSyncRequest =
            PeriodicWorkRequestBuilder<UsageDataSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .addTag(UsageDataSyncWorker.WORK_NAME)
                .build()

        workManager.enqueueUniquePeriodicWork(
            UsageDataSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            usageSyncRequest
        )
    }

    private fun calculateInitialDelay(): Long {
        val currentTimeMillis = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= currentTimeMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val randomOffsetMillis = (Math.random() * 2 * 60 * 60 * 1000).toLong()

        val targetTimeMillis = calendar.timeInMillis + randomOffsetMillis
        return targetTimeMillis - currentTimeMillis
    }
}

@Composable
fun SettingsScreen(
    onToggleHealthSync: (Boolean) -> Unit,
    onToggleUsageSync: (Boolean) -> Unit,
    isHealthSyncEnabled: Boolean,
    isUsageSyncEnabled: Boolean,
    isLoggedIn: Boolean,
    supabaseClient: SupabaseClient
) {
    var healthSyncEnabled by remember { mutableStateOf(isHealthSyncEnabled) }
    var usageSyncEnabled by remember { mutableStateOf(isUsageSyncEnabled) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Enable Health Data Sync")
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = healthSyncEnabled,
                onCheckedChange = {
                    healthSyncEnabled = it
                    onToggleHealthSync(it)
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Enable Usage Data Sync")
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = usageSyncEnabled,
                onCheckedChange = {
                    usageSyncEnabled = it
                    onToggleUsageSync(it)
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        AuthenticationCard(isLoggedIn = isLoggedIn, supabaseClient = supabaseClient)
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
                androidx.compose.material3.Icon(
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
            androidx.compose.material3.Icon(
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
            androidx.compose.material3.Icon(
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
