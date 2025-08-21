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
import org.devpins.pihs.ui.theme.PIHSTheme

private const val SETTINGS_PREFS = "AppSettings"
private const val KEY_ENABLE_USAGE = "enable_usage_tracking"
private const val KEY_ENABLE_LOCATION = "enable_location_tracking"

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
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
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { finish() }, shape = RoundedCornerShape(8.dp)) {
                                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Back")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        ToggleRow(
                            title = "Enable App Usage Tracking",
                            checkedInitial = prefs.getBoolean(KEY_ENABLE_USAGE, true),
                        ) { newValue ->
                            prefs.edit().putBoolean(KEY_ENABLE_USAGE, newValue).apply()
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        ToggleRow(
                            title = "Enable Location Tracking",
                            checkedInitial = prefs.getBoolean(KEY_ENABLE_LOCATION, true),
                        ) { newValue ->
                            prefs.edit().putBoolean(KEY_ENABLE_LOCATION, newValue).apply()
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
