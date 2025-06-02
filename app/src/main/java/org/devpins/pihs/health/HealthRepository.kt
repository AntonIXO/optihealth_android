package org.devpins.pihs.health

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
        healthConnectManager.initialize()
    }

    // Get permission request contract
    fun getPermissionRequestContract() = healthConnectManager.getPermissionRequestContract()

    // Get permissions to request
    fun getPermissionsToRequest() = healthConnectManager.getPermissionsToRequest()

    // Handle permission result
    suspend fun handlePermissionResult(grantedPermissions: Set<String>) {
        healthConnectManager.handlePermissionResult(grantedPermissions)
    }

    // Get intent to open Health Connect settings
    fun getHealthConnectSettingsIntent() = healthConnectManager.getHealthConnectSettingsIntent()

    // Sync health data with Supabase
    suspend fun syncHealthData() {
        try {
            _syncStatus.value = SyncStatus.Syncing

            // Get health data from Health Connect
            val healthData = healthConnectManager.getLastMonthData()

            // Transform health data to PIHS format
            val pihsHealthData = healthDataTransformer.transformHealthData(healthData)

            // Upload to Supabase
            uploadToSupabase(pihsHealthData)

            _syncStatus.value = SyncStatus.Success
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
        }
    }

    // Upload health data to Supabase
    private suspend fun uploadToSupabase(pihsHealthData: PIHSHealthData) {
        // Convert to JSON
        val json = Json.encodeToString(pihsHealthData)

        // Upload to Supabase
        // Note: The table name and structure should match your Supabase setup
        postgrest["health_data"].insert(json)
    }
}

// Sync status
sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    object Success : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}