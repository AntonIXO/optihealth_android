package org.devpins.pihs.data.repository

import android.util.Log
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.devpins.pihs.data.model.MetricDefinition
import javax.inject.Inject
import javax.inject.Singleton

sealed class MetricDefinitionsResult {
    data class Success(val metrics: List<MetricDefinition>) : MetricDefinitionsResult()
    data class Error(val message: String) : MetricDefinitionsResult()
}

@Singleton
class MetricDefinitionsRepository @Inject constructor(
    private val postgrest: Postgrest
) {
    private var cachedMetrics: List<MetricDefinition>? = null

    suspend fun getMetricDefinitions(): MetricDefinitionsResult = withContext(Dispatchers.IO) {
        try {
            // Return cached metrics if available
            cachedMetrics?.let {
                return@withContext MetricDefinitionsResult.Success(it)
            }

            // Fetch from database
            val metrics = postgrest["metric_definitions"]
                .select()
                .decodeList<MetricDefinition>()
                .sortedBy { it.getDisplayName() }

            // Cache the results
            cachedMetrics = metrics

            Log.d("MetricDefinitionsRepo", "Fetched ${metrics.size} metric definitions")
            MetricDefinitionsResult.Success(metrics)
        } catch (e: Exception) {
            Log.e("MetricDefinitionsRepo", "Error fetching metric definitions", e)
            MetricDefinitionsResult.Error(e.message ?: "Unknown error")
        }
    }

    fun clearCache() {
        cachedMetrics = null
    }
}
