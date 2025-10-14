package org.devpins.pihs.health

import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import org.devpins.pihs.BuildConfig
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
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_DATA_PULL_DAYS = 30L

/**
 * Manages interactions with Health Connect, including checking availability, permissions,
 * and reading various health data records.
 *
 * @property healthConnectClient The [HealthConnectClient] instance, nullable if Health Connect is not available.
 * @property healthConnectPermissions The set of Health Connect permissions the application requires.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    private val healthConnectClient: HealthConnectClient?,
    private val healthConnectPermissions: Set<String>
) {
    /**
     * Default number of days to pull data for when getting "last month's data".
     */

    // State flow to track availability of Health Connect
    private val _availability = MutableStateFlow(HealthConnectAvailability.UNKNOWN)
    val availability: StateFlow<HealthConnectAvailability> = _availability.asStateFlow()

    // State flow to track permissions
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    /**
     * Initializes the HealthConnectManager by checking Health Connect SDK availability and then
     * verifying if the required permissions have been granted.
     * This function should typically be called once, for example, during application startup
     * or when health data related features are first accessed.
     */
    suspend fun initialize() {
        Log.v("HealthConnect", "Initializing HealthConnectManager")
        if (BuildConfig.DEBUG) {
            Log.d("HealthConnect", "Initial client: $healthConnectClient")
        }

        checkAvailability()
        Log.v("HealthConnect", "After checkAvailability, availability: ${_availability.value}")

        if (_availability.value == HealthConnectAvailability.INSTALLED) {
            Log.v("HealthConnect", "Health Connect is installed, checking permissions")
            checkPermissions()
        } else {
            Log.v("HealthConnect", "Health Connect is not installed, skipping permission check")
        }
    }

    // Check if Health Connect is available
    private fun checkAvailability() {
        val newAvailability = when {
            healthConnectClient == null -> {
                Log.v("HealthConnect", "HealthConnectManager: healthConnectClient is null, reporting NOT_INSTALLED")
                HealthConnectAvailability.NOT_INSTALLED
            }
            else -> {
                Log.v("HealthConnect", "HealthConnectManager: healthConnectClient is available, reporting INSTALLED")
                HealthConnectAvailability.INSTALLED
            }
        }

        Log.v("HealthConnect", "HealthConnectManager: Setting availability to $newAvailability")
        _availability.value = newAvailability
    }

    // Check if permissions are granted
    private suspend fun checkPermissions() {
        healthConnectClient?.let { client ->
            try {
                if (BuildConfig.DEBUG) {
                    Log.d("HealthConnect", "Checking permissions with client: $client")
                }
                val granted = client.permissionController.getGrantedPermissions()
                if (BuildConfig.DEBUG) {
                    Log.d("HealthConnect", "Granted permissions: $granted")
                    Log.d("HealthConnect", "Required permissions: $healthConnectPermissions")
                }

                val allGranted = granted.containsAll(healthConnectPermissions)
                Log.v("HealthConnect", "All permissions granted: $allGranted")

                _permissionsGranted.value = allGranted
            } catch (e: Exception) {
                Log.e("HealthConnect", "Error checking permissions", e)
                _permissionsGranted.value = false
            }
        } ?: run {
            Log.v("HealthConnect", "Cannot check permissions, client is null")
            _permissionsGranted.value = false
        }
    }

    // Get permission request contract
    fun getPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    /**
     * Returns the set of Health Connect permissions that the application will request.
     * @return A set of permission strings.
     */
    fun getPermissionsToRequest(): Set<String> {
        if (BuildConfig.DEBUG) {
            Log.d("HealthConnect", "Getting permissions to request: $healthConnectPermissions")
        }
        return healthConnectPermissions
    }

    // Handle permission result
    suspend fun handlePermissionResult(grantedPermissions: Set<String>) {
        if (BuildConfig.DEBUG) {
            Log.d("HealthConnect", "Permission result received: $grantedPermissions")
            Log.d("HealthConnect", "Required permissions: $healthConnectPermissions")
        }

        val allGranted = grantedPermissions.containsAll(healthConnectPermissions)
        Log.v("HealthConnect", "All permissions granted: $allGranted")

        _permissionsGranted.value = allGranted
    }

    /**
     * Creates an [Intent] that can be used to launch the Health Connect settings screen.
     * This allows the user to manage Health Connect settings directly.
     * @return An [Intent] to open Health Connect settings.
     */
    fun getHealthConnectSettingsIntent(): Intent {
        Log.v("HealthConnect", "HealthConnectManager: Creating Health Connect settings intent")
        val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
        // No need to set data URI for this intent
        if (BuildConfig.DEBUG) {
            Log.d("HealthConnect", "HealthConnectManager: Health Connect settings intent created: $intent")
        }
        return intent
    }

    // Generic data reading function
    private suspend fun <T : Record> readData(recordType: KClass<T>, start: Instant, end: Instant): List<T> {
        healthConnectClient?.let { client ->
            try {
                val request = ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
                if (BuildConfig.DEBUG) {
                    Log.d("HealthConnect", "HealthConnectManager: Created ${recordType.simpleName} request: $request")
                }
                val response = client.readRecords(request)
                Log.v("HealthConnect", "HealthConnectManager: Read ${response.records.size} ${recordType.simpleName} records")
                return response.records
            } catch (e: Exception) {
                Log.e("HealthConnect", "HealthConnectManager: Error reading ${recordType.simpleName} data", e)
                return emptyList()
            }
        } ?: run {
            Log.v("HealthConnect", "HealthConnectManager: Cannot read ${recordType.simpleName} data, client is null")
            return emptyList()
        }
    }

    /**
     * Reads [StepsRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [StepsRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readStepsData(start: Instant, end: Instant): List<StepsRecord> {
        Log.v("HealthConnect", "HealthConnectManager: Reading steps data from $start to $end")
        return readData(StepsRecord::class, start, end)
    }

    /**
     * Reads [SleepSessionRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [SleepSessionRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readSleepData(start: Instant, end: Instant): List<SleepSessionRecord> {
        return readData(SleepSessionRecord::class, start, end)
    }

    /**
     * Reads [HeartRateRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [HeartRateRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readHeartRateData(start: Instant, end: Instant): List<HeartRateRecord> {
        return readData(HeartRateRecord::class, start, end)
    }

    /**
     * Reads [HeartRateVariabilityRmssdRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [HeartRateVariabilityRmssdRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readHeartRateVariabilityData(start: Instant, end: Instant): List<HeartRateVariabilityRmssdRecord> {
        return readData(HeartRateVariabilityRmssdRecord::class, start, end)
    }

    /**
     * Reads [ExerciseSessionRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [ExerciseSessionRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readExerciseData(start: Instant, end: Instant): List<ExerciseSessionRecord> {
        // Log.v("HealthConnect", "HealthConnectManager: Reading exercise data from $start to $end") // Already logged in readData
        return readData(ExerciseSessionRecord::class, start, end)
    }

    /**
     * Reads [WeightRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [WeightRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readWeightData(start: Instant, end: Instant): List<WeightRecord> {
        // Log.v("HealthConnect", "HealthConnectManager: Reading weight data from $start to $end") // Already logged in readData
        return readData(WeightRecord::class, start, end)
    }

    /**
     * Reads [BloodPressureRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [BloodPressureRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readBloodPressureData(start: Instant, end: Instant): List<BloodPressureRecord> {
        return readData(BloodPressureRecord::class, start, end)
    }

    /**
     * Reads [BloodGlucoseRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [BloodGlucoseRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readBloodGlucoseData(start: Instant, end: Instant): List<BloodGlucoseRecord> {
        return readData(BloodGlucoseRecord::class, start, end)
    }

    /**
     * Reads [BodyTemperatureRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [BodyTemperatureRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readBodyTemperatureData(start: Instant, end: Instant): List<BodyTemperatureRecord> {
        return readData(BodyTemperatureRecord::class, start, end)
    }

    /**
     * Reads [OxygenSaturationRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [OxygenSaturationRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readOxygenSaturationData(start: Instant, end: Instant): List<OxygenSaturationRecord> {
        return readData(OxygenSaturationRecord::class, start, end)
    }

    /**
     * Reads [RespiratoryRateRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [RespiratoryRateRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readRespiratoryRateData(start: Instant, end: Instant): List<RespiratoryRateRecord> {
        return readData(RespiratoryRateRecord::class, start, end)
    }

    /**
     * Reads [NutritionRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [NutritionRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readNutritionData(start: Instant, end: Instant): List<NutritionRecord> {
        return readData(NutritionRecord::class, start, end)
    }

    /**
     * Reads [HydrationRecord] data from Health Connect within the specified time range.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return A list of [HydrationRecord]s, or an empty list if an error occurs or client is null.
     */
    suspend fun readHydrationData(start: Instant, end: Instant): List<HydrationRecord> {
        return readData(HydrationRecord::class, start, end)
    }

    /**
     * Reads in existing WeightRecord records.
     */
    suspend fun readWeightInputs(start: Instant, end: Instant): List<WeightRecord> {
        // Log.v("HealthConnect", "HealthConnectManager: Reading weight inputs from $start to $end") // Already logged in readData
        return readData(WeightRecord::class, start, end)
    }

    /**
     * Obtains a list of ExerciseSessionRecord records in a specified time frame.
     */
    suspend fun readExerciseSessions(start: Instant, end: Instant): List<ExerciseSessionRecord> {
        // Log.v("HealthConnect", "HealthConnectManager: Reading exercise sessions from $start to $end") // Already logged in readData
        return readData(ExerciseSessionRecord::class, start, end)
    }

    /**
     * Obtains a list of DistanceRecord records in a specified time frame.
     */
    suspend fun readDistanceData(start: Instant, end: Instant): List<DistanceRecord> {
        // Log.v("HealthConnect", "HealthConnectManager: Reading distance data from $start to $end") // Already logged in readData
        return readData(DistanceRecord::class, start, end)
    }

    /**
     * Obtains a list of ExerciseSegment records in a specified time frame.
     */
    suspend fun readExerciseSegments(start: Instant, end: Instant): List<ExerciseSegment> {
        Log.v("HealthConnect", "HealthConnectManager: Reading exercise segments from $start to $end")
        return healthConnectClient?.let { client ->
            try {
                // ExerciseSegment is part of ExerciseSessionRecord, so we need to get them from there
                val exerciseSessions = readExerciseSessions(start, end)
                val segments = mutableListOf<ExerciseSegment>()

                exerciseSessions.forEach { session ->
                    segments.addAll(session.segments)
                }

                Log.v("HealthConnect", "HealthConnectManager: Read ${segments.size} exercise segments")
                segments
            } catch (e: Exception) {
                Log.e("HealthConnect", "HealthConnectManager: Error reading exercise segments", e)
                emptyList()
            }
        } ?: run {
            Log.v("HealthConnect", "HealthConnectManager: Cannot read exercise segments, client is null")
            emptyList()
        }
    }

    /**
     * Obtains a list of ExerciseRoute records in a specified time frame.
     */
    suspend fun readExerciseRoutes(start: Instant, end: Instant): List<ExerciseRoute> {
        Log.v("HealthConnect", "HealthConnectManager: Reading exercise routes from $start to $end")
        return healthConnectClient?.let { client ->
            try {
                // TODO("Implement ExerciseRoute reading when Health Connect API provides a clearer way or if custom logic is defined.")
                // For now, return an empty list as we need to implement a proper way to get routes
                // This will be implemented in a future update
                Log.v("HealthConnect", "HealthConnectManager: Exercise routes not yet implemented")
                emptyList()
            } catch (e: Exception) {
                Log.e("HealthConnect", "HealthConnectManager: Error reading exercise routes", e)
                emptyList()
            }
        } ?: run {
            Log.v("HealthConnect", "HealthConnectManager: Cannot read exercise routes, client is null")
            emptyList()
        }
    }

    /**
     * Retrieves a comprehensive [HealthData] object containing various health metrics
     * for the last [DEFAULT_DATA_PULL_DAYS] (typically 30 days).
     * Returns an empty [HealthData] object if Health Connect client is not available.
     * @return [HealthData] object with lists of records.
     */
    suspend fun getLastMonthData(): HealthData {
        Log.v("HealthConnect", "HealthConnectManager: Getting data for the last 30 days")

        if (healthConnectClient == null) {
            Log.e("HealthConnect", "HealthConnectManager: Cannot get data, client is null")
            return HealthData()
        }

        val end = Instant.now()
        val start = end.minusSeconds(DEFAULT_DATA_PULL_DAYS * 24 * 60 * 60L) // Use constant for 30 days in seconds
        if (BuildConfig.DEBUG) {
            Log.d("HealthConnect", "HealthConnectManager: Time range: $start to $end")
        }

        return getHealthDataInRange(start, end)
    }

    /**
     * Retrieves a comprehensive [HealthData] object containing various health metrics
     * from a specified [since] timestamp up to the current time.
     * Returns an empty [HealthData] object if Health Connect client is not available.
     * @param since The start [Instant] from which to retrieve data.
     * @return [HealthData] object with lists of records.
     */
    suspend fun getHealthDataSince(since: Instant): HealthData {
        Log.v("HealthConnect", "HealthConnectManager: Getting data since $since")

        if (healthConnectClient == null) {
            Log.e("HealthConnect", "HealthConnectManager: Cannot get data, client is null")
            return HealthData()
        }

        val end = Instant.now()
        if (BuildConfig.DEBUG) {
            Log.d("HealthConnect", "HealthConnectManager: Time range: $since to $end")
        }

        return getHealthDataInRange(since, end)
    }

    /**
     * Retrieves a comprehensive [HealthData] object containing various health metrics
     * between the specified [start] and [end] timestamps.
     * Returns an empty [HealthData] object if Health Connect client is not available.
     * @param start The start [Instant] of the time range.
     * @param end The end [Instant] of the time range.
     * @return [HealthData] object with lists of records.
     */
    suspend fun getHealthDataBetween(start: Instant, end: Instant): HealthData {
        Log.v("HealthConnect", "HealthConnectManager: Getting data between $start and $end")

        if (healthConnectClient == null) {
            Log.e("HealthConnect", "HealthConnectManager: Cannot get data, client is null")
            return HealthData()
        }

        if (BuildConfig.DEBUG) {
            Log.d("HealthConnect", "HealthConnectManager: Time range: $start to $end")
        }

        return getHealthDataInRange(start, end)
    }

    // Get health data in a specific time range
    private suspend fun getHealthDataInRange(start: Instant, end: Instant): HealthData {
        try {
            Log.v("HealthConnect", "HealthConnectManager: Reading health data from $start to $end")
            val healthData = HealthData(
                steps = readStepsData(start, end),
                sleep = readSleepData(start, end),
                heartRate = readHeartRateData(start, end),
                heartRateVariability = readHeartRateVariabilityData(start, end),
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
            Log.v("HealthConnect", "HealthConnectManager: Successfully read health data")
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
    val heartRateVariability: List<HeartRateVariabilityRmssdRecord> = emptyList(),
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
