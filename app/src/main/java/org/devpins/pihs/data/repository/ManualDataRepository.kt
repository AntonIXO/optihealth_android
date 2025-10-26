package org.devpins.pihs.data.repository

import android.util.Log
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.devpins.pihs.data.model.DataPoint
import org.devpins.pihs.data.remote.DataUploaderService
import org.devpins.pihs.data.remote.UploadResult
import org.devpins.pihs.health.MetricSource
import javax.inject.Inject
import javax.inject.Singleton

sealed class ManualDataResult {
    data class Success(val message: String) : ManualDataResult()
    data class Error(val message: String) : ManualDataResult()
}

@Singleton
class ManualDataRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth,
    private val dataUploaderService: DataUploaderService
) {
    private var healthConnectMetricSourceId: Long? = null

    /**
     * Get or create the Health Connect Android metric source for the current user
     * This reuses the same source as automatic Health Connect syncs
     */
    private suspend fun getOrCreateHealthConnectMetricSource(): Long? = withContext(Dispatchers.IO) {
        try {
            val userId = auth.currentUserOrNull()?.id
            if (userId == null) {
                Log.e("ManualDataRepo", "User not authenticated")
                return@withContext null
            }

            // Check if already cached
            healthConnectMetricSourceId?.let { return@withContext it }

            // Try to find existing Health Connect source
            val existingSources = postgrest["metric_sources"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("source_identifier", "health_connect_android")
                    }
                }
                .decodeList<MetricSource>()

            if (existingSources.isNotEmpty()) {
                val sourceId = existingSources.first().id
                healthConnectMetricSourceId = sourceId
                Log.d("ManualDataRepo", "Found existing Health Connect metric source: $sourceId")
                return@withContext sourceId
            }

            // Create new Health Connect source if it doesn't exist
            Log.d("ManualDataRepo", "Creating new Health Connect metric source")
            val newSource = buildJsonObject {
                put("user_id", userId)
                put("source_identifier", "health_connect_android")
                put("source_name", "Android Health Connect")
                put("is_active", true)
            }

            val result = postgrest["metric_sources"]
                .insert(newSource) {
                    select()
                }
                .decodeSingle<MetricSource>()

            healthConnectMetricSourceId = result.id
            Log.d("ManualDataRepo", "Created Health Connect metric source: ${result.id}")
            result.id
        } catch (e: Exception) {
            Log.e("ManualDataRepo", "Error getting/creating Health Connect metric source", e)
            null
        }
    }

    /**
     * Insert a manual data point
     */
    suspend fun insertManualDataPoint(dataPoint: DataPoint): ManualDataResult = withContext(Dispatchers.IO) {
        try {
            val sourceId = getOrCreateHealthConnectMetricSource()
            if (sourceId == null) {
                return@withContext ManualDataResult.Error("Failed to get metric source. Please ensure you are logged in.")
            }

            // Create data point with the Health Connect source ID
            val dataPointWithSource = dataPoint.copy(metricSourceId = sourceId)

            // Upload using DataUploaderService
            when (val result = dataUploaderService.uploadDataPoints(listOf(dataPointWithSource))) {
                is UploadResult.Success -> {
                    Log.d("ManualDataRepo", "Successfully uploaded manual data point")
                    ManualDataResult.Success("Data point added successfully")
                }
                is UploadResult.Failure -> {
                    Log.e("ManualDataRepo", "Failed to upload manual data point: ${result.errorMessage}")
                    ManualDataResult.Error(result.errorMessage)
                }
            }
        } catch (e: Exception) {
            Log.e("ManualDataRepo", "Error inserting manual data point", e)
            ManualDataResult.Error(e.message ?: "Unknown error")
        }
    }

    fun clearCache() {
        healthConnectMetricSourceId = null
    }
}
