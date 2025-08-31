package org.devpins.pihs.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.devpins.pihs.background.BackgroundSyncController
import org.devpins.pihs.ui.theme.PIHSTheme
import org.devpins.pihs.settings.SettingsKeys

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(SettingsKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
        setContent {
            PIHSTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.Start
                    ) {
//                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
//                            OutlinedButton(onClick = { finish() }, shape = RoundedCornerShape(8.dp)) {
//                                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
//                                Spacer(modifier = Modifier.width(8.dp))
//                                Text("Back")
//                            }
//                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        // Background sync toggle
                        ToggleRow(
                            title = "Enable Background Sync",
                            checkedInitial = prefs.getBoolean(SettingsKeys.KEY_ENABLE_BACKGROUND_SYNC, true),
                        ) { newValue ->
                            prefs.edit().putBoolean(SettingsKeys.KEY_ENABLE_BACKGROUND_SYNC, newValue).apply()
                            if (newValue) BackgroundSyncController.enable(this@SettingsActivity) else BackgroundSyncController.disable(this@SettingsActivity)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // Sync interval selector
                        SyncIntervalRow()
                        Spacer(modifier = Modifier.height(8.dp))
                        SyncStatusSection()
                        Spacer(modifier = Modifier.height(24.dp))
                        // Other toggles
                        ToggleRow(
                            title = "Enable App Usage Tracking",
                            checkedInitial = prefs.getBoolean(SettingsKeys.KEY_ENABLE_USAGE, true),
                        ) { newValue ->
                            prefs.edit().putBoolean(SettingsKeys.KEY_ENABLE_USAGE, newValue).apply()
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        ToggleRow(
                            title = "Enable Location Tracking",
                            checkedInitial = prefs.getBoolean(SettingsKeys.KEY_ENABLE_LOCATION, true),
                        ) { newValue ->
                            prefs.edit().putBoolean(SettingsKeys.KEY_ENABLE_LOCATION, newValue).apply()
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        ToggleRow(
                            title = "Enable Neiry Headband (beta)",
                            checkedInitial = prefs.getBoolean(SettingsKeys.KEY_ENABLE_NEIRY, false),
                        ) { newValue ->
                            prefs.edit().putBoolean(SettingsKeys.KEY_ENABLE_NEIRY, newValue).apply()
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        // Optional: save button not required because we apply on change, but we can give a hint
                        Text(text = "Changes are applied immediately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checkedInitial: Boolean, onToggle: (Boolean) -> Unit) {
    var checked = remember { mutableStateOf(checkedInitial) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked.value, onCheckedChange = { v ->
            checked.value = v
            onToggle(v)
        })
    }
}

@Composable
private fun SyncIntervalRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences(SettingsKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)

    val optionsMinutes = listOf(15, 30, 60, 360, 720, 1440)
    fun labelFor(minutes: Int): String = when (minutes) {
        15 -> "Every 15 minutes"
        30 -> "Every 30 minutes"
        60 -> "Every 1 hour"
        360 -> "Every 6 hours"
        720 -> "Every 12 hours"
        1440 -> "Every 24 hours"
        else -> "Every ${minutes} minutes"
    }

    var current by remember { mutableStateOf(prefs.getInt(SettingsKeys.KEY_SYNC_INTERVAL_MINUTES, 1440)) }
    // Normalize to the nearest available option if a non-standard value is present
    if (!optionsMinutes.contains(current)) {
        current = 1440
    }

    var showDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(current) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Sync Interval", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(text = labelFor(current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = {
            selected = current
            showDialog = true
        }, shape = RoundedCornerShape(8.dp)) {
            Text("Change")
        }
    }

    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Choose sync interval") },
            text = {
                Column {
                    optionsMinutes.forEach { minutes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == minutes,
                                onClick = { selected = minutes }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = labelFor(minutes), modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    current = selected
                    prefs.edit().putInt(SettingsKeys.KEY_SYNC_INTERVAL_MINUTES, selected).apply()
                    // If background sync is enabled, reschedule with the new interval
                    val bgEnabled = prefs.getBoolean(SettingsKeys.KEY_ENABLE_BACKGROUND_SYNC, true)
                    if (bgEnabled) {
                        BackgroundSyncController.enable(context)
                    }
                    showDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SyncStatusSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences(SettingsKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)

    var lastHealthSync by remember { mutableStateOf(prefs.getLong(SettingsKeys.KEY_LAST_HEALTH_SYNC_AT, 0L)) }
    var lastUsageSync by remember { mutableStateOf(prefs.getLong(SettingsKeys.KEY_LAST_USAGE_SYNC_AT, 0L)) }
    val bgEnabled = prefs.getBoolean(SettingsKeys.KEY_ENABLE_BACKGROUND_SYNC, true)
    val intervalMinutes = prefs.getInt(SettingsKeys.KEY_SYNC_INTERVAL_MINUTES, 1440)

    fun formatTime(millis: Long): String {
        if (millis <= 0L) return "Never"
        val dt = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(millis))
        return dt
    }

    fun intervalLabel(minutes: Int): String = when (minutes) {
        15 -> "Every 15 minutes"
        30 -> "Every 30 minutes"
        60 -> "Every 1 hour"
        360 -> "Every 6 hours"
        720 -> "Every 12 hours"
        else -> "Every 24 hours"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Background Sync Status",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text("Background Sync: ${if (bgEnabled) "Enabled" else "Disabled"}")
        Spacer(Modifier.height(4.dp))
        Text("Sync Interval: ${intervalLabel(intervalMinutes)}")
        Spacer(Modifier.height(4.dp))
        Text("Health Sync: Last success: ${formatTime(lastHealthSync)}")
        Spacer(Modifier.height(4.dp))
        Text("Usage Sync: Last success: ${formatTime(lastUsageSync)}")
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            // Reload timestamps from prefs
            lastHealthSync = prefs.getLong(SettingsKeys.KEY_LAST_HEALTH_SYNC_AT, 0L)
            lastUsageSync = prefs.getLong(SettingsKeys.KEY_LAST_USAGE_SYNC_AT, 0L)
        }) {
            Text("Refresh Status")
        }
    }
}
