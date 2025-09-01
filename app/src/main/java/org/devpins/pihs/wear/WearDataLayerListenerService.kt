package org.devpins.pihs.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.devpins.pihs.data.model.DataPoint
import org.devpins.pihs.data.remote.DataUploaderService
import org.devpins.pihs.data.remote.UploadResult
import org.devpins.pihs.settings.SettingsKeys
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class WearDataLayerListenerService : WearableListenerService() {

    @Inject lateinit var postgrest: Postgrest
    @Inject lateinit var auth: Auth
    @Inject lateinit var dataUploaderService: DataUploaderService

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                val path = item.uri.path
                if (path == BODY_TEMP_PATH) {
                    try {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val valueC = dataMap.getDouble(KEY_VALUE_CELSIUS)
                        val tsMillis = dataMap.getLong(KEY_TIMESTAMP)
                        Log.d(TAG, "Received body temp from wear: $valueC at $tsMillis")
                        // Persist last received immediately
                        updatePrefsOnReceive(applicationContext, valueC.toFloat(), tsMillis)
                        // Upload in background
                        serviceScope.launch {
                            handleBodyTemperatureUpload(valueC, tsMillis)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling body temperature data item", e)
                    }
                }
            }
        }
    }

    private suspend fun handleBodyTemperatureUpload(valueC: Double, tsMillis: Long) {
        try {
            val user = auth.currentUserOrNull()
            if (user == null) {
                Log.w(TAG, "No authenticated user; skipping upload")
                updatePrefsOnUpload(applicationContext, success = false, error = "Not authenticated")
                return
            }
            val sourceId = getOrCreateWearMetricSource(user.id)
            val dp = DataPoint(
                metricSourceId = sourceId,
                timestamp = Instant.ofEpochMilli(tsMillis).toString(),
                metricName = "body_temperature",
                valueNumeric = valueC,
                valueText = null,
                valueJson = null,
                unit = "celsius",
                tags = buildJsonObject { put("source", "wear_os_companion") }
            )
            when (val result = dataUploaderService.uploadDataPoints(listOf(dp))) {
                is UploadResult.Success -> {
                    Log.i(TAG, "Uploaded body temperature to cloud")
                    updatePrefsOnUpload(applicationContext, success = true, error = null)
                }
                is UploadResult.Failure -> {
                    Log.e(TAG, "Upload failed: ${result.errorMessage}", result.exception)
                    updatePrefsOnUpload(applicationContext, success = false, error = result.errorMessage)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during upload", e)
            updatePrefsOnUpload(applicationContext, success = false, error = e.message ?: "unknown error")
        }
    }

    private suspend fun getOrCreateWearMetricSource(userId: String): Long {
        // Try to find existing wear_os_companion source
        return try {
            val existing = postgrest["metric_sources"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("source_identifier", "wear_os_companion")
                    }
                }
                .decodeList<MetricSource>()
            if (existing.isNotEmpty()) {
                existing.first().id
            } else {
                val body = buildJsonObject {
                    put("user_id", userId)
                    put("source_identifier", "wear_os_companion")
                    put("source_name", "Wear OS Companion")
                    put("is_active", true)
                    put("last_synced_at", null as String?)
                }
                val created = postgrest["metric_sources"].insert(body).decodeSingle<MetricSource>()
                created.id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting/creating wear metric source", e)
            throw e
        }
    }

    private fun updatePrefsOnReceive(context: Context, tempValueC: Float, tsMillis: Long) {
        val prefs = context.getSharedPreferences(SettingsKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(SettingsKeys.KEY_WEAR_LAST_TEMP_VALUE, tempValueC)
            .putLong(SettingsKeys.KEY_WEAR_LAST_TEMP_RECEIVED_AT, tsMillis)
            .apply()
    }

    private fun updatePrefsOnUpload(context: Context, success: Boolean, error: String?) {
        val prefs = context.getSharedPreferences(SettingsKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(SettingsKeys.KEY_WEAR_LAST_UPLOAD_AT, System.currentTimeMillis())
            .putString(SettingsKeys.KEY_WEAR_LAST_UPLOAD_ERROR, if (success) null else (error ?: "unknown"))
            .apply()
    }

    companion object {
        private const val TAG = "WearDLService"
        private const val BODY_TEMP_PATH = "/vitals/body_temperature"
        private const val KEY_VALUE_CELSIUS = "value_celsius"
        private const val KEY_TIMESTAMP = "timestamp"
    }
}

@kotlinx.serialization.Serializable
private data class MetricSource(
    val id: Long,
    val user_id: String,
    val source_identifier: String,
    val source_name: String,
    val is_active: Boolean,
    val last_synced_at: String? = null
)