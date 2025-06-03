package org.devpins.pihs.health

import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSegment
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
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
        Log.d("HealthConnect", "Initializing HealthConnectManager")
        Log.d("HealthConnect", "Initial client: $healthConnectClient")

        checkAvailability()
        Log.d("HealthConnect", "After checkAvailability, availability: ${_availability.value}")

        if (_availability.value == HealthConnectAvailability.INSTALLED) {
            Log.d("HealthConnect", "Health Connect is installed, checking permissions")
            checkPermissions()
        } else {
            Log.d("HealthConnect", "Health Connect is not installed, skipping permission check")
        }
    }

    // Check if Health Connect is available
    private fun checkAvailability() {
        val newAvailability = when {
            healthConnectClient == null -> {
                Log.d("HealthConnect", "HealthConnectManager: healthConnectClient is null, reporting NOT_INSTALLED")
                HealthConnectAvailability.NOT_INSTALLED
            }
            else -> {
                Log.d("HealthConnect", "HealthConnectManager: healthConnectClient is available, reporting INSTALLED")
                HealthConnectAvailability.INSTALLED
            }
        }

        Log.d("HealthConnect", "HealthConnectManager: Setting availability to $newAvailability")
        _availability.value = newAvailability
    }

    // Check if permissions are granted
    private suspend fun checkPermissions() {
        healthConnectClient?.let { client ->
            try {
                Log.d("HealthConnect", "Checking permissions with client: $client")
                val granted = client.permissionController.getGrantedPermissions()
                Log.d("HealthConnect", "Granted permissions: $granted")
                Log.d("HealthConnect", "Required permissions: $healthConnectPermissions")

                val allGranted = granted.containsAll(healthConnectPermissions)
                Log.d("HealthConnect", "All permissions granted: $allGranted")

                _permissionsGranted.value = allGranted
            } catch (e: Exception) {
                Log.e("HealthConnect", "Error checking permissions", e)
                _permissionsGranted.value = false
            }
        } ?: run {
            Log.d("HealthConnect", "Cannot check permissions, client is null")
            _permissionsGranted.value = false
        }
    }

    // Get permission request contract
    fun getPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    // Get permissions to request
    fun getPermissionsToRequest(): Set<String> {
        Log.d("HealthConnect", "Getting permissions to request: $healthConnectPermissions")
        return healthConnectPermissions
    }

    // Handle permission result
    suspend fun handlePermissionResult(grantedPermissions: Set<String>) {
        Log.d("HealthConnect", "Permission result received: $grantedPermissions")
        Log.d("HealthConnect", "Required permissions: $healthConnectPermissions")

        val allGranted = grantedPermissions.containsAll(healthConnectPermissions)
        Log.d("HealthConnect", "All permissions granted: $allGranted")

        _permissionsGranted.value = allGranted
    }

    // Get intent to open Health Connect settings
    fun getHealthConnectSettingsIntent(): Intent {
        Log.d("HealthConnect", "HealthConnectManager: Creating Health Connect settings intent")
        val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
        // No need to set data URI for this intent
        Log.d("HealthConnect", "HealthConnectManager: Health Connect settings intent created: $intent")
        return intent
    }

    // Read steps data
    suspend fun readStepsData(start: Instant, end: Instant): List<StepsRecord> {
        Log.d("HealthConnect", "HealthConnectManager: Reading steps data from $start to $end")
        return healthConnectClient?.let { client ->
            try {
                val request = ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
                Log.d("HealthConnect", "HealthConnectManager: Created steps request: $request")

                val records = client.readRecords(request).records
                Log.d("HealthConnect", "HealthConnectManager: Read ${records.size} steps records")
                records
            } catch (e: Exception) {
                Log.e("HealthConnect", "HealthConnectManager: Error reading steps data", e)
                emptyList()
            }
        } ?: run {
            Log.d("HealthConnect", "HealthConnectManager: Cannot read steps data, client is null")
            emptyList()
        }
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
            try {
                val request = ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
                Log.d("HealthConnect", "HealthConnectManager: Created exercise request: $request")

                val records = client.readRecords(request).records
                Log.d("HealthConnect", "HealthConnectManager: Read ${records.size} exercise records")
                records
            } catch (e: Exception) {
                Log.e("HealthConnect", "HealthConnectManager: Error reading exercise data", e)
                emptyList()
            }
        } ?: run {
            Log.d("HealthConnect", "HealthConnectManager: Cannot read exercise data, client is null")
            emptyList()
        }
    }

    // Read weight data
    suspend fun readWeightData(start: Instant, end: Instant): List<WeightRecord> {
        return healthConnectClient?.let { client ->
            try {
                val request = ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
                Log.d("HealthConnect", "HealthConnectManager: Created weight request: $request")

                val records = client.readRecords(request).records
                Log.d("HealthConnect", "HealthConnectManager: Read ${records.size} weight records")
                records
            } catch (e: Exception) {
                Log.e("HealthConnect", "HealthConnectManager: Error reading weight data", e)
                emptyList()
            }
        } ?: run {
            Log.d("HealthConnect", "HealthConnectManager: Cannot read weight data, client is null")
            emptyList()
        }
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

    /**
     * Reads in existing WeightRecord records.
     */
    suspend fun readWeightInputs(start: Instant, end: Instant): List<WeightRecord> {
        Log.d("HealthConnect", "HealthConnectManager: Reading weight inputs from $start to $end")
        return healthConnectClient?.let { client ->
            try {
                val request = ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
                Log.d("HealthConnect", "HealthConnectManager: Created weight inputs request: $request")

                val response = client.readRecords(request)
                Log.d("HealthConnect", "HealthConnectManager: Read ${response.records.size} weight input records")
                response.records
            } catch (e: Exception) {
                Log.e("HealthConnect", "HealthConnectManager: Error reading weight inputs", e)
                emptyList()
            }
        } ?: run {
            Log.d("HealthConnect", "HealthConnectManager: Cannot read weight inputs, client is null")
            emptyList()
        }
    }

    /**
     * Obtains a list of ExerciseSessionRecord records in a specified time frame.
     */
    suspend fun readExerciseSessions(start: Instant, end: Instant): List<ExerciseSessionRecord> {
        Log.d("HealthConnect", "HealthConnectManager: Reading exercise sessions from $start to $end")
        return healthConnectClient?.let { client ->
            try {
                val request = ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
                Log.d("HealthConnect", "HealthConnectManager: Created exercise sessions request: $request")

                val response = client.readRecords(request)
                Log.d("HealthConnect", "HealthConnectManager: Read ${response.records.size} exercise session records")
                response.records
            } catch (e: Exception) {
                Log.e("HealthConnect", "HealthConnectManager: Error reading exercise sessions", e)
                emptyList()
            }
        } ?: run {
            Log.d("HealthConnect", "HealthConnectManager: Cannot read exercise sessions, client is null")
            emptyList()
        }
    }

    /**
     * Obtains a list of DistanceRecord records in a specified time frame.
     */
    suspend fun readDistanceData(start: Instant, end: Instant): List<DistanceRecord> {
        Log.d("HealthConnect", "HealthConnectManager: Reading distance data from $start to $end")
        return healthConnectClient?.let { client ->
            try {
                val request = ReadRecordsRequest(
                    recordType = DistanceRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
                Log.d("HealthConnect", "HealthConnectManager: Created distance data request: $request")

                val response = client.readRecords(request)
                Log.d("HealthConnect", "HealthConnectManager: Read ${response.records.size} distance records")
                response.records
            } catch (e: Exception) {
                Log.e("HealthConnect", "HealthConnectManager: Error reading distance data", e)
                emptyList()
            }
        } ?: run {
            Log.d("HealthConnect", "HealthConnectManager: Cannot read distance data, client is null")
            emptyList()
        }
    }

    /**
     * Obtains a list of ExerciseSegment records in a specified time frame.
     */
    suspend fun readExerciseSegments(start: Instant, end: Instant): List<ExerciseSegment> {
        Log.d("HealthConnect", "HealthConnectManager: Reading exercise segments from $start to $end")
        return healthConnectClient?.let { client ->
            try {
                // ExerciseSegment is part of ExerciseSessionRecord, so we need to get them from there
                val exerciseSessions = readExerciseSessions(start, end)
                val segments = mutableListOf<ExerciseSegment>()

                exerciseSessions.forEach { session ->
                    segments.addAll(session.segments)
                }

                Log.d("HealthConnect", "HealthConnectManager: Read ${segments.size} exercise segments")
                segments
            } catch (e: Exception) {
                Log.e("HealthConnect", "HealthConnectManager: Error reading exercise segments", e)
                emptyList()
            }
        } ?: run {
            Log.d("HealthConnect", "HealthConnectManager: Cannot read exercise segments, client is null")
            emptyList()
        }
    }

    /**
     * Obtains a list of ExerciseRoute records in a specified time frame.
     */
    suspend fun readExerciseRoutes(start: Instant, end: Instant): List<ExerciseRoute> {
        Log.d("HealthConnect", "HealthConnectManager: Reading exercise routes from $start to $end")
        return healthConnectClient?.let { client ->
            try {
                // For now, return an empty list as we need to implement a proper way to get routes
                // This will be implemented in a future update
                Log.d("HealthConnect", "HealthConnectManager: Exercise routes not yet implemented")
                emptyList()
            } catch (e: Exception) {
                Log.e("HealthConnect", "HealthConnectManager: Error reading exercise routes", e)
                emptyList()
            }
        } ?: run {
            Log.d("HealthConnect", "HealthConnectManager: Cannot read exercise routes, client is null")
            emptyList()
        }
    }

    // Get data for the last 30 days
    suspend fun getLastMonthData(): HealthData {
        Log.d("HealthConnect", "HealthConnectManager: Getting data for the last 30 days")

        if (healthConnectClient == null) {
            Log.e("HealthConnect", "HealthConnectManager: Cannot get data, client is null")
            return HealthData()
        }

        val end = Instant.now()
        val start = end.minusSeconds(30 * 24 * 60 * 60L) // 30 days in seconds
        Log.d("HealthConnect", "HealthConnectManager: Time range: $start to $end")

        return getHealthDataInRange(start, end)
    }

    // Get data since a specific timestamp until now
    suspend fun getHealthDataSince(since: Instant): HealthData {
        Log.d("HealthConnect", "HealthConnectManager: Getting data since $since")

        if (healthConnectClient == null) {
            Log.e("HealthConnect", "HealthConnectManager: Cannot get data, client is null")
            return HealthData()
        }

        val end = Instant.now()
        Log.d("HealthConnect", "HealthConnectManager: Time range: $since to $end")

        return getHealthDataInRange(since, end)
    }

    // Get data between specific start and end timestamps
    suspend fun getHealthDataBetween(start: Instant, end: Instant): HealthData {
        Log.d("HealthConnect", "HealthConnectManager: Getting data between $start and $end")

        if (healthConnectClient == null) {
            Log.e("HealthConnect", "HealthConnectManager: Cannot get data, client is null")
            return HealthData()
        }

        Log.d("HealthConnect", "HealthConnectManager: Time range: $start to $end")

        return getHealthDataInRange(start, end)
    }

    // Get health data in a specific time range
    private suspend fun getHealthDataInRange(start: Instant, end: Instant): HealthData {
        try {
            Log.d("HealthConnect", "HealthConnectManager: Reading health data from $start to $end")
            val healthData = HealthData(
                steps = readStepsData(start, end),
                sleep = readSleepData(start, end),
                heartRate = readHeartRateData(start, end),
                exercise = readExerciseSessions(start, end), // Use the new method
                distance = readDistanceData(start, end),
                exerciseSegments = readExerciseSegments(start, end),
                exerciseRoutes = readExerciseRoutes(start, end),
                weight = readWeightInputs(start, end), // Use the new method
                bloodPressure = readBloodPressureData(start, end),
                bloodGlucose = readBloodGlucoseData(start, end),
                bodyTemperature = readBodyTemperatureData(start, end),
                oxygenSaturation = readOxygenSaturationData(start, end),
                respiratoryRate = readRespiratoryRateData(start, end),
                nutrition = readNutritionData(start, end),
                hydration = readHydrationData(start, end)
            )
            Log.d("HealthConnect", "HealthConnectManager: Successfully read health data")
            return healthData
        } catch (e: Exception) {
            Log.e("HealthConnect", "HealthConnectManager: Error reading health data", e)
            return HealthData()
        }
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
    val distance: List<DistanceRecord> = emptyList(),
    val exerciseSegments: List<ExerciseSegment> = emptyList(),
    val exerciseRoutes: List<ExerciseRoute> = emptyList(),
    val weight: List<WeightRecord> = emptyList(),
    val bloodPressure: List<BloodPressureRecord> = emptyList(),
    val bloodGlucose: List<BloodGlucoseRecord> = emptyList(),
    val bodyTemperature: List<BodyTemperatureRecord> = emptyList(),
    val oxygenSaturation: List<OxygenSaturationRecord> = emptyList(),
    val respiratoryRate: List<RespiratoryRateRecord> = emptyList(),
    val nutrition: List<NutritionRecord> = emptyList(),
    val hydration: List<HydrationRecord> = emptyList()
)
