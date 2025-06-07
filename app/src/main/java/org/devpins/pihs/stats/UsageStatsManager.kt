package org.devpins.pihs.stats

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable // Required for Supabase data class serialization
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Serializable // Ensure PihsDataPoint is serializable for Supabase
data class PihsDataPoint(
    val user_id: String,
    val metric_source_id: String,
    val timestamp: String, // ISO 8601 format e.g., "YYYY-MM-DDT23:59:59Z"
    val metric_name: String,
    val value_numeric: Double,
    val unit: String,
    val value_json: Map<String, String>? = null,
    val tags: List<String>? = null
)

/**
 * Helper object for accessing and processing app usage statistics.
 * It provides functions to check for and request usage stats permission,
 * retrieve daily app usage, and log this data to a Supabase backend.
 */
object UsageStatsHelper {

    private const val TAG = "UsageStatsHelper"

    // Minimum duration for an app's foreground time to be considered significant for daily tracking.
    private const val TEN_MINUTES_IN_MILLIS = 10 * 60 * 1000L

    // List of specific application package names to track. Currently hardcoded.
    private val TARGET_APP_PACKAGES = listOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.android.chrome",
        "com.instagram.android",
        "com.facebook.katana",
        "com.brave.browser"
        // Add more as needed
    )

    /**
     * Checks if the application has been granted permission to access usage statistics.
     *
     * @param context The application [Context].
     * @return `true` if permission is granted, `false` otherwise.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                context.applicationInfo.uid,
                context.packageName
            )
        } else {
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                context.applicationInfo.uid,
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Opens the system settings screen where the user can grant access to usage statistics.
     *
     * @param context The application [Context].
     */
    fun requestUsageStatsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /**
     * Retrieves and processes usage statistics for the specified date, user, and metric source.
     * It calculates total device usage and usage for a predefined list of target applications.
     *
     * @param context The application [Context].
     * @param date The [LocalDate] for which to fetch usage stats.
     * @param userId The ID of the user for whom the data is being tracked.
     * @param metricSourceId The ID of the metric source (e.g., a specific device or app installation).
     * @return A list of [PihsDataPoint] objects representing the processed usage data.
     *         Returns an empty list if usage stats permission is not granted or if no relevant usage data is found.
     */
    fun getAndProcessUsageStats(
        context: Context,
        date: LocalDate,
        userId: String,
        metricSourceId: String
    ): List<PihsDataPoint> {
        if (!hasUsageStatsPermission(context)) {
            Log.w(TAG, "Usage stats permission not granted.")
            return emptyList()
        }

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val startTime = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTime = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val queryUsageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (queryUsageStats.isNullOrEmpty()) {
            return emptyList()
        }

        val dataPoints = mutableListOf<PihsDataPoint>()
        val timestampEndOfDay = ZonedDateTime.of(date.atTime(23, 59, 59), ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("UTC"))
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        var totalDeviceUsageMillis = 0L
        queryUsageStats.forEach {
            if (!isSystemApp(context, it.packageName)) {
                totalDeviceUsageMillis += it.totalTimeInForeground
            }
        }
        if (totalDeviceUsageMillis > 0) {
            dataPoints.add(
                PihsDataPoint(
                    user_id = userId,
                    metric_source_id = metricSourceId,
                    timestamp = timestampEndOfDay,
                    metric_name = "device_usage_total_duration_minutes",
                    value_numeric = TimeUnit.MILLISECONDS.toMinutes(totalDeviceUsageMillis).toDouble(),
                    unit = "minutes",
                    tags = listOf("daily_summary")
                )
            )
        }

        val targetAppStats = queryUsageStats
            .filter { TARGET_APP_PACKAGES.contains(it.packageName) && it.totalTimeInForeground > TEN_MINUTES_IN_MILLIS }
            .sortedByDescending { it.totalTimeInForeground }
            .take(5)

        targetAppStats.forEach { usageStat ->
            val appName = getAppName(context, usageStat.packageName)
            // Metric name includes sanitized package name for detailed app-specific tracking.
            val sanitizedPackageName = usageStat.packageName.replace(".", "_").replace("-", "_")
            dataPoints.add(
                PihsDataPoint(
                    user_id = userId,
                    metric_source_id = metricSourceId,
                    timestamp = timestampEndOfDay,
                    metric_name = "app_usage_duration_minutes_$sanitizedPackageName",
                    value_numeric = TimeUnit.MILLISECONDS.toMinutes(usageStat.totalTimeInForeground).toDouble(),
                    unit = "minutes",
                    value_json = mapOf(
                        "app_package_name" to usageStat.packageName,
                        "app_name" to appName
                    ),
                    tags = listOf("daily_summary", "app_specific")
                )
            )
        }
        return dataPoints
    }

    /**
     * Retrieves the application name for a given package name.
     * @param context The application context.
     * @param packageName The package name of the application.
     * @return The application name, or the package name if the app name cannot be found.
     */
    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // Return package name as fallback
        }
    }

    /**
     * Checks if an application is a system app.
     * @param context The application context.
     * @param packageName The package name of the application.
     * @return `true` if the app is a system app or an updated system app, `false` otherwise.
     * Returns `true` if [PackageManager.NameNotFoundException] occurs, assuming it might be a system component.
     */
    private fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            true // Treat as system app if not found, to be safe in filtering usage
        }
    }

    /**
     * Logs a list of [PihsDataPoint] to a Supabase table named "data_points".
     * It attempts to insert each data point individually and logs any errors encountered during the process.
     *
     * @param dataPoints The list of [PihsDataPoint]s to be logged.
     * @param supabaseClient The initialized [SupabaseClient] instance.
     */
    suspend fun logDataToSupabase(dataPoints: List<PihsDataPoint>, supabaseClient: SupabaseClient) {
        if (dataPoints.isEmpty()) {
            Log.i(TAG, "No data points to log.")
            return
        }
        var successfulUploads = 0
        try {
            // The batch insert is commented out as per plan, individual inserts are used below.
            // supabaseClient.postgrest.from("data_points").insert(dataPoints) {
            //     // ... (original comment about conflict handling)
            // }

            dataPoints.forEach { dataPoint ->
                try {
                    supabaseClient.postgrest.from("data_points").insert(dataPoint) {
                        // Potential for onConflict handling here if needed
                    }
                    successfulUploads++
                } catch (e: Exception) {
                    Log.e(TAG, "Error logging data point for metric ${dataPoint.metric_name} at ${dataPoint.timestamp} to Supabase: ${e.message}", e)
                }
            }

            Log.i(TAG, "Successfully logged $successfulUploads out of ${dataPoints.size} data points to Supabase.")
            if (successfulUploads < dataPoints.size) {
                Log.w(TAG, "${dataPoints.size - successfulUploads} data points failed to upload.")
                // You could throw a custom exception here if you want the worker to result in partial success or specific error
            }
        } catch (e: Exception) { // This outer catch is for unexpected errors during the loop or setup.
            Log.e(TAG, "Unexpected error during Supabase logging loop: ${e.message}", e)
        }
    }
}
