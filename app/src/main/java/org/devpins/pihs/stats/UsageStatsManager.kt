package org.devpins.pihs.stats

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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

object UsageStatsHelper {

    private const val TEN_MINUTES_IN_MILLIS = 10 * 60 * 1000L

    private val TARGET_APP_PACKAGES = listOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.android.chrome",
        "com.instagram.android",
        "com.facebook.katana",
        "com.brave.browser"
        // Add more as needed
    )

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

    fun requestUsageStatsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun getAndProcessUsageStats(
        context: Context,
        date: LocalDate,
        userId: String,
        metricSourceId: String
    ): List<PihsDataPoint> {
        if (!hasUsageStatsPermission(context)) {
            println("Usage stats permission not granted.")
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

    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            true
        }
    }

    suspend fun logDataToSupabase(dataPoints: List<PihsDataPoint>, supabaseClient: SupabaseClient) {
        if (dataPoints.isEmpty()) {
            println("No data points to log.")
            return
        }
        try {
            // Assuming PihsDataPoint is @Serializable
            supabaseClient.postgrest.from("data_points").insert(dataPoints) {
                // To prevent re-inserting the same data for the same user, metric, and timestamp
                // If your table has a unique constraint on (user_id, metric_source_id, timestamp, metric_name)
                // this will help avoid duplicates.
                // Supabase by default will throw an error on conflict if a unique constraint is violated.
                // If you want to ignore duplicates, you can add `upsert()` with `onConflict`
                // and `ignoreDuplicates = true` (check Supabase KT library for exact syntax if needed)
                // For now, relying on default behavior or a table policy for ON CONFLICT DO NOTHING.
            }
            var successfulUploads = 0
            dataPoints.forEach { dataPoint ->
                try {
                    supabaseClient.postgrest.from("data_points").insert(dataPoint) {
                        // Potential for onConflict handling here if needed, see original comment
                    }
                    successfulUploads++
                } catch (e: Exception) {
                    // Log specific data point that failed, but without PII if possible from dataPoint.toString()
                    // For example, log metric_name and timestamp.
                    println("Error logging data point for metric ${dataPoint.metric_name} at ${dataPoint.timestamp} to Supabase: ${e.message}")
                    // Optionally, log e for full stack trace if needed for debugging, but be mindful of log size.
                }
            }
            println("Successfully logged $successfulUploads out of ${dataPoints.size} data points to Supabase.")
            if (successfulUploads < dataPoints.size) {
                println("${dataPoints.size - successfulUploads} data points failed to upload.")
                // You could throw a custom exception here if you want the worker to result in partial success or specific error
            }
        } catch (e: Exception) { // This outer catch is now more for unexpected errors or issues with the loop itself
            println("Unexpected error during Supabase logging loop: ${e.message}")
        }
    }
}
