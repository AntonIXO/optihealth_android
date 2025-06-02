package org.devpins.pihs.health

import android.util.Log
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRepository @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val healthDataTransformer: HealthDataTransformer,
    private val postgrest: Postgrest
) {
    // State flow to track sync status
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // State flow to track Health Connect availability
    val healthConnectAvailability: StateFlow<HealthConnectAvailability> = healthConnectManager.availability

    // State flow to track permissions
    val permissionsGranted: StateFlow<Boolean> = healthConnectManager.permissionsGranted

    // Initialize the repository
    suspend fun initialize() {
        Log.d("HealthConnect", "HealthRepository: Initializing")
        healthConnectManager.initialize()
        Log.d("HealthConnect", "HealthRepository: Initialized, availability: ${healthConnectAvailability.value}, permissions: ${permissionsGranted.value}")
    }

    // Get permission request contract
    fun getPermissionRequestContract(): androidx.activity.result.contract.ActivityResultContract<Set<String>, Set<String>> {
        Log.d("HealthConnect", "HealthRepository: Getting permission request contract")
        return healthConnectManager.getPermissionRequestContract()
    }

    // Get permissions to request
    fun getPermissionsToRequest(): Set<String> {
        Log.d("HealthConnect", "HealthRepository: Getting permissions to request")
        val permissions = healthConnectManager.getPermissionsToRequest()
        Log.d("HealthConnect", "HealthRepository: Permissions to request: $permissions")
        return permissions
    }

    // Handle permission result
    suspend fun handlePermissionResult(grantedPermissions: Set<String>) {
        Log.d("HealthConnect", "HealthRepository: Handling permission result: $grantedPermissions")
        healthConnectManager.handlePermissionResult(grantedPermissions)
        Log.d("HealthConnect", "HealthRepository: Permission result handled, permissions granted: ${permissionsGranted.value}")
    }

    // Get intent to open Health Connect settings
    fun getHealthConnectSettingsIntent(): android.content.Intent {
        Log.d("HealthConnect", "HealthRepository: Getting Health Connect settings intent")
        val intent = healthConnectManager.getHealthConnectSettingsIntent()
        Log.d("HealthConnect", "HealthRepository: Health Connect settings intent: $intent")
        return intent
    }

    // Sync health data with Supabase
    suspend fun syncHealthData() {
        try {
            Log.d("HealthConnect", "HealthRepository: Starting health data sync")
            _syncStatus.value = SyncStatus.Syncing

            // Check if Health Connect is available and permissions are granted
            Log.d("HealthConnect", "HealthRepository: Health Connect availability: ${healthConnectAvailability.value}")
            Log.d("HealthConnect", "HealthRepository: Permissions granted: ${permissionsGranted.value}")

            // Get health data from Health Connect
            Log.d("HealthConnect", "HealthRepository: Getting health data from Health Connect")
            val healthData = healthConnectManager.getLastMonthData()
            Log.d("HealthConnect", "HealthRepository: Got health data from Health Connect")

            // Transform health data to PIHS format
            Log.d("HealthConnect", "HealthRepository: Transforming health data to PIHS format")
            val pihsHealthData = healthDataTransformer.transformHealthData(healthData)
            Log.d("HealthConnect", "HealthRepository: Transformed health data to PIHS format")

            // Upload to Supabase
            Log.d("HealthConnect", "HealthRepository: Uploading health data to Supabase")
            uploadToSupabase(pihsHealthData)
            Log.d("HealthConnect", "HealthRepository: Uploaded health data to Supabase")

            _syncStatus.value = SyncStatus.Success
            Log.d("HealthConnect", "HealthRepository: Health data sync completed successfully")
        } catch (e: Exception) {
            Log.e("HealthConnect", "HealthRepository: Error syncing health data", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
        }
    }

    // Upload health data to Supabase
    private suspend fun uploadToSupabase(pihsHealthData: PIHSHealthData) {
        try {
            // Convert to JSON
            Log.d("HealthConnect", "HealthRepository: Converting health data to JSON")
            val json = Json.encodeToString(pihsHealthData)
            Log.d("HealthConnect", "HealthRepository: Converted health data to JSON")

            // Upload to Supabase
            // Note: The table name and structure should match your Supabase setup
            Log.d("HealthConnect", "HealthRepository: Uploading JSON to Supabase")
            postgrest["health_data"].insert(json)
            Log.d("HealthConnect", "HealthRepository: Uploaded JSON to Supabase")
        } catch (e: Exception) {
            Log.e("HealthConnect", "HealthRepository: Error uploading to Supabase", e)
            throw e
        }
    }
}

// Sync status
sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    object Success : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}
