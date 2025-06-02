package org.devpins.pihs.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
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
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    private val healthConnectClient: HealthConnectClient?,
    private val healthConnectPermissions: Set<String>
) {
    // State flow to track availability of Health Connect
    private val _availability = MutableStateFlow(HealthConnectAvailability.UNKNOWN)
    val availability: StateFlow<HealthConnectAvailability> = _availability.asStateFlow()

    // State flow to track permissions
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    // Initialize the manager
    suspend fun initialize() {
        checkAvailability()
        if (_availability.value == HealthConnectAvailability.INSTALLED) {
            checkPermissions()
        }
    }

    // Check if Health Connect is available
    private fun checkAvailability() {
        _availability.value = when {
            healthConnectClient == null -> HealthConnectAvailability.NOT_INSTALLED
            else -> HealthConnectAvailability.INSTALLED
        }
    }

    // Check if permissions are granted
    private suspend fun checkPermissions() {
        healthConnectClient?.let { client ->
            val granted = client.permissionController.getGrantedPermissions()
            _permissionsGranted.value = granted.containsAll(healthConnectPermissions)
        }
    }

    // Get permission request contract
    fun getPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    // Get permissions to request
    fun getPermissionsToRequest(): Set<String> {
        return healthConnectPermissions
    }

    // Handle permission result
    suspend fun handlePermissionResult(grantedPermissions: Set<String>) {
        _permissionsGranted.value = grantedPermissions.containsAll(healthConnectPermissions)
    }

    // Get intent to open Health Connect settings
    fun getHealthConnectSettingsIntent(): Intent {
        val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
        // No need to set data URI for this intent
        return intent
    }

    // Read steps data
    suspend fun readStepsData(start: Instant, end: Instant): List<StepsRecord> {
        return healthConnectClient?.let { client ->
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        } ?: emptyList()
    }

    // Read sleep data
    suspend fun readSleepData(start: Instant, end: Instant): List<SleepSessionRecord> {
        return healthConnectClient?.let { client ->
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        } ?: emptyList()
    }

    // Read heart rate data
    suspend fun readHeartRateData(start: Instant, end: Instant): List<HeartRateRecord> {
        return healthConnectClient?.let { client ->
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        } ?: emptyList()
    }

    // Read exercise data
    suspend fun readExerciseData(start: Instant, end: Instant): List<ExerciseSessionRecord> {
        return healthConnectClient?.let { client ->
            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        } ?: emptyList()
    }

    // Read weight data
    suspend fun readWeightData(start: Instant, end: Instant): List<WeightRecord> {
        return healthConnectClient?.let { client ->
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        } ?: emptyList()
    }

    // Read blood pressure data
    suspend fun readBloodPressureData(start: Instant, end: Instant): List<BloodPressureRecord> {
        return healthConnectClient?.let { client ->
            val request = ReadRecordsRequest(
                recordType = BloodPressureRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        } ?: emptyList()
    }

    // Read blood glucose data
    suspend fun readBloodGlucoseData(start: Instant, end: Instant): List<BloodGlucoseRecord> {
        return healthConnectClient?.let { client ->
            val request = ReadRecordsRequest(
                recordType = BloodGlucoseRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        } ?: emptyList()
    }

    // Read body temperature data
    suspend fun readBodyTemperatureData(start: Instant, end: Instant): List<BodyTemperatureRecord> {
        return healthConnectClient?.let { client ->
            val request = ReadRecordsRequest(
                recordType = BodyTemperatureRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        } ?: emptyList()
    }

    // Read oxygen saturation data
    suspend fun readOxygenSaturationData(start: Instant, end: Instant): List<OxygenSaturationRecord> {
        return healthConnectClient?.let { client ->
            val request = ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        } ?: emptyList()
    }

    // Read respiratory rate data
    suspend fun readRespiratoryRateData(start: Instant, end: Instant): List<RespiratoryRateRecord> {
        return healthConnectClient?.let { client ->
            val request = ReadRecordsRequest(
                recordType = RespiratoryRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        } ?: emptyList()
    }

    // Read nutrition data
    suspend fun readNutritionData(start: Instant, end: Instant): List<NutritionRecord> {
        return healthConnectClient?.let { client ->
            val request = ReadRecordsRequest(
                recordType = NutritionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        } ?: emptyList()
    }

    // Read hydration data
    suspend fun readHydrationData(start: Instant, end: Instant): List<HydrationRecord> {
        return healthConnectClient?.let { client ->
            val request = ReadRecordsRequest(
                recordType = HydrationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        } ?: emptyList()
    }

    // Get data for the last 30 days
    suspend fun getLastMonthData(): HealthData {
        val end = Instant.now()
        val start = end.minusSeconds(30 * 24 * 60 * 60L) // 30 days in seconds

        return HealthData(
            steps = readStepsData(start, end),
            sleep = readSleepData(start, end),
            heartRate = readHeartRateData(start, end),
            exercise = readExerciseData(start, end),
            weight = readWeightData(start, end),
            bloodPressure = readBloodPressureData(start, end),
            bloodGlucose = readBloodGlucoseData(start, end),
            bodyTemperature = readBodyTemperatureData(start, end),
            oxygenSaturation = readOxygenSaturationData(start, end),
            respiratoryRate = readRespiratoryRateData(start, end),
            nutrition = readNutritionData(start, end),
            hydration = readHydrationData(start, end)
        )
    }
}

// Health Connect availability states
enum class HealthConnectAvailability {
    UNKNOWN,
    INSTALLED,
    NOT_INSTALLED
}

// Data class to hold all health data
data class HealthData(
    val steps: List<StepsRecord> = emptyList(),
    val sleep: List<SleepSessionRecord> = emptyList(),
    val heartRate: List<HeartRateRecord> = emptyList(),
    val exercise: List<ExerciseSessionRecord> = emptyList(),
    val weight: List<WeightRecord> = emptyList(),
    val bloodPressure: List<BloodPressureRecord> = emptyList(),
    val bloodGlucose: List<BloodGlucoseRecord> = emptyList(),
    val bodyTemperature: List<BodyTemperatureRecord> = emptyList(),
    val oxygenSaturation: List<OxygenSaturationRecord> = emptyList(),
    val respiratoryRate: List<RespiratoryRateRecord> = emptyList(),
    val nutrition: List<NutritionRecord> = emptyList(),
    val hydration: List<HydrationRecord> = emptyList()
)
