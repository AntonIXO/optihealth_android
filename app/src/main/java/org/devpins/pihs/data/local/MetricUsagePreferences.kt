package org.devpins.pihs.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages metric usage frequency tracking using SharedPreferences
 */
@Singleton
class MetricUsagePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "metric_usage_prefs"
        private const val PREFIX_COUNT = "usage_count_"
    }

    /**
     * Increment usage count for a metric
     */
    fun incrementMetricUsage(metricName: String) {
        val currentCount = getMetricUsageCount(metricName)
        prefs.edit().putInt(PREFIX_COUNT + metricName, currentCount + 1).apply()
    }

    /**
     * Get usage count for a metric
     */
    fun getMetricUsageCount(metricName: String): Int {
        return prefs.getInt(PREFIX_COUNT + metricName, 0)
    }

    /**
     * Get all metric usage counts
     */
    fun getAllMetricUsageCounts(): Map<String, Int> {
        val usageCounts = mutableMapOf<String, Int>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(PREFIX_COUNT) && value is Int) {
                val metricName = key.removePrefix(PREFIX_COUNT)
                usageCounts[metricName] = value
            }
        }
        return usageCounts
    }

    /**
     * Clear all usage data
     */
    fun clearAllUsageData() {
        prefs.edit().clear().apply()
    }

    /**
     * Clear usage data for a specific metric
     */
    fun clearMetricUsage(metricName: String) {
        prefs.edit().remove(PREFIX_COUNT + metricName).apply()
    }
}
