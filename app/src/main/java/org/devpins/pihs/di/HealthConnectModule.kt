package org.devpins.pihs.di

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object HealthConnectModule {

    // Health Connect package name
    private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

    // Check if Health Connect is available
    private fun isHealthConnectAvailable(context: Context): Boolean {
        // Method 1: Check using intent resolution
        val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SHOW")
        val intentResult = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

        // Method 2: Check if package is installed directly
        val packageInstalled = try {
            context.packageManager.getPackageInfo(HEALTH_CONNECT_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        // Log both results for debugging
        Log.d("HealthConnect", "Intent resolution result: $intentResult")
        Log.d("HealthConnect", "Package installed: $packageInstalled")

        // Use either method or combine them
        return packageInstalled || intentResult != null
    }

    // Provide HealthConnectClient
    @Provides
    @Singleton
    fun provideHealthConnectClient(@ApplicationContext context: Context): HealthConnectClient? {
        return try {
            val isAvailable = isHealthConnectAvailable(context)
            Log.d("HealthConnect", "Health Connect available: $isAvailable")

            if (isAvailable) {
                try {
                    val client = HealthConnectClient.getOrCreate(context)
                    Log.d("HealthConnect", "Health Connect client created successfully: $client")
                    client
                } catch (e: Exception) {
                    Log.e("HealthConnect", "Error creating Health Connect client", e)
                    null
                }
            } else {
                Log.d("HealthConnect", "Health Connect not available, returning null client")
                null
            }
        } catch (e: Exception) {
            Log.e("HealthConnect", "Unexpected error in provideHealthConnectClient", e)
            null
        }
    }

    // Provide the set of permissions needed for the app
    @Provides
    @Singleton
    fun provideHealthConnectPermissions(): Set<String> {
        return setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getReadPermission(BodyTemperatureRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class),
            HealthPermission.getReadPermission(HydrationRecord::class)
        )
    }
}
