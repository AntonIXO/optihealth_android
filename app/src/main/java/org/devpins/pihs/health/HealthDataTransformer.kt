package org.devpins.pihs.health

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
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthDataTransformer @Inject constructor() {

    private val dateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    // Transform all health data to PIHS format
    fun transformHealthData(healthData: HealthData): PIHSHealthData {
        return PIHSHealthData(
            steps = transformStepsData(healthData.steps),
            sleep = transformSleepData(healthData.sleep),
            heartRate = transformHeartRateData(healthData.heartRate),
            exercise = transformExerciseData(healthData.exercise, healthData.distance, healthData.exerciseSegments, healthData.exerciseRoutes),
            weight = transformWeightData(healthData.weight),
            bloodPressure = transformBloodPressureData(healthData.bloodPressure),
            bloodGlucose = transformBloodGlucoseData(healthData.bloodGlucose),
            bodyTemperature = transformBodyTemperatureData(healthData.bodyTemperature),
            oxygenSaturation = transformOxygenSaturationData(healthData.oxygenSaturation),
            respiratoryRate = transformRespiratoryRateData(healthData.respiratoryRate),
            nutrition = transformNutritionData(healthData.nutrition),
            hydration = transformHydrationData(healthData.hydration)
        )
    }

    // Transform steps data
    private fun transformStepsData(stepsRecords: List<StepsRecord>): List<PIHSStepsData> {
        return stepsRecords.map { record ->
            PIHSStepsData(
                startTime = formatInstant(record.startTime),
                endTime = formatInstant(record.endTime),
                count = record.count
            )
        }
    }

    // Transform sleep data
    private fun transformSleepData(sleepRecords: List<SleepSessionRecord>): List<PIHSSleepData> {
        return sleepRecords.map { record ->
            // Calculate total duration in bed (from start to end of session)
            val timeInBedSeconds = record.endTime.epochSecond - record.startTime.epochSecond
            val timeInBedMinutes = timeInBedSeconds / 60.0

            // Initialize counters for different sleep stages
            var deepSleepSeconds = 0L
            var lightSleepSeconds = 0L
            var remSleepSeconds = 0L
            var awakeSeconds = 0L
            var awakeningsCount = 0
            var lastStageWasAwake = false

            // Process each sleep stage
            record.stages.forEach { stage ->
                val stageDurationSeconds = stage.endTime.epochSecond - stage.startTime.epochSecond

                when (stage.stage.toString()) {
                    "DEEP" -> deepSleepSeconds += stageDurationSeconds
                    "LIGHT" -> lightSleepSeconds += stageDurationSeconds
                    "REM" -> remSleepSeconds += stageDurationSeconds
                    "AWAKE" -> {
                        awakeSeconds += stageDurationSeconds
                        if (!lastStageWasAwake) {
                            awakeningsCount++
                            lastStageWasAwake = true
                        }
                    }
                    else -> { /* Unknown stage type, ignore */ }
                }

                // Update lastStageWasAwake for next iteration
                lastStageWasAwake = stage.stage.toString() == "AWAKE"
            }

            // Calculate total sleep duration (excluding awake time)
            val totalSleepSeconds = deepSleepSeconds + lightSleepSeconds + remSleepSeconds
            val totalSleepMinutes = totalSleepSeconds / 60.0

            // Calculate sleep efficiency (percentage of time in bed spent sleeping)
            val sleepEfficiency = if (timeInBedSeconds > 0) {
                (totalSleepSeconds.toDouble() / timeInBedSeconds.toDouble()) * 100.0
            } else {
                0.0
            }

            // Calculate sleep latency (time to fall asleep)
            // This is an approximation - time from start of session to first non-awake stage
            var sleepLatencySeconds = 0L
            if (record.stages.isNotEmpty()) {
                val firstNonAwakeStage = record.stages.find { it.stage.toString() != "AWAKE" }
                if (firstNonAwakeStage != null) {
                    sleepLatencySeconds = firstNonAwakeStage.startTime.epochSecond - record.startTime.epochSecond
                }
            }

            // Create and return the PIHSSleepData object with all metrics
            PIHSSleepData(
                startTime = formatInstant(record.startTime),
                endTime = formatInstant(record.endTime),
                duration = timeInBedSeconds,
                stages = record.stages.map { stage ->
                    PIHSSleepStage(
                        startTime = formatInstant(stage.startTime),
                        endTime = formatInstant(stage.endTime),
                        stage = stage.stage.toString()
                    )
                },
                totalSleepDurationMinutes = totalSleepMinutes,
                deepSleepDurationMinutes = deepSleepSeconds / 60.0,
                lightSleepDurationMinutes = lightSleepSeconds / 60.0,
                remSleepDurationMinutes = remSleepSeconds / 60.0,
                sleepScore = 0.0, // Sleep score is device-specific and not available from Health Connect
                sleepEfficiencyPercentage = sleepEfficiency,
                sleepLatencyMinutes = sleepLatencySeconds / 60.0,
                awakeningsCount = awakeningsCount,
                timeInBedMinutes = timeInBedMinutes
            )
        }
    }

    // Transform heart rate data
    private fun transformHeartRateData(heartRateRecords: List<HeartRateRecord>): List<PIHSHeartRateData> {
        return heartRateRecords.map { record ->
            PIHSHeartRateData(
                samples = record.samples.map { sample ->
                    PIHSHeartRateSample(
                        time = formatInstant(sample.time),
                        beatsPerMinute = sample.beatsPerMinute
                    )
                }
            )
        }
    }

    // Transform exercise data
    private fun transformExerciseData(
        exerciseRecords: List<ExerciseSessionRecord>,
        distanceRecords: List<DistanceRecord>,
        exerciseSegments: List<ExerciseSegment>,
        exerciseRoutes: List<ExerciseRoute> = emptyList()
    ): List<PIHSExerciseData> {
        return exerciseRecords.map { record ->
            // Calculate duration in minutes
            val durationSeconds = record.endTime.epochSecond - record.startTime.epochSecond
            val durationMinutes = durationSeconds / 60.0

            // Extract distance in kilometers if available
            // Find distance records that overlap with this exercise session
            val relevantDistanceRecords = distanceRecords.filter { 
                it.startTime <= record.endTime && it.endTime >= record.startTime
            }

            // Sum up the distances
            val distanceKm = if (relevantDistanceRecords.isNotEmpty()) {
                relevantDistanceRecords.sumOf { it.distance.inKilometers }
            } else {
                0.0
            }

            // Extract calories if available
            val calories = 0.0 // Will be populated from energy records in future

            // Extract heart rate data if available
            var averageHeartRateBpm = 0.0
            var maxHeartRateBpm = 0.0

            // Extract steps count if available
            val stepsCount = 0L // Will be populated from steps records in future

            // Map exercise type to human-readable name
            val exerciseTypeReadable = mapExerciseTypeToReadableName(record.exerciseType.toString())

            // Find segments that belong to this exercise session
            val relevantSegments = exerciseSegments.filter {
                it.startTime >= record.startTime && it.endTime <= record.endTime
            }

            // Transform segments to PIHSExerciseSegment
            val transformedSegments = relevantSegments.map { segment ->
                PIHSExerciseSegment(
                    startTime = formatInstant(segment.startTime),
                    endTime = formatInstant(segment.endTime),
                    segmentType = mapExerciseTypeToReadableName(segment.segmentType.toString())
                )
            }

            // For now, routes are not implemented in the manager
            // This will be implemented in a future update
            val transformedRoutes = emptyList<PIHSExerciseRoute>()

            // Create and return the PIHSExerciseData object with all metrics
            PIHSExerciseData(
                startTime = formatInstant(record.startTime),
                endTime = formatInstant(record.endTime),
                exerciseType = exerciseTypeReadable,
                title = record.title ?: "",
                notes = record.notes ?: "",
                calories = calories,
                durationMinutes = durationMinutes,
                distanceKm = distanceKm,
                averageHeartRateBpm = averageHeartRateBpm,
                maxHeartRateBpm = maxHeartRateBpm,
                intensityManual = 0, // Not available from Health Connect, would need to be calculated or provided by user
                stepsCount = stepsCount,
                activeEnergyKcal = calories, // Using the same value as calories for active energy
                segments = transformedSegments,
                routes = transformedRoutes
            )
        }
    }

    // Transform weight data
    private fun transformWeightData(weightRecords: List<WeightRecord>): List<PIHSWeightData> {
        return weightRecords.map { record ->
            PIHSWeightData(
                time = formatInstant(record.time),
                weight = record.weight.inKilograms
            )
        }
    }

    // Transform blood pressure data
    private fun transformBloodPressureData(bpRecords: List<BloodPressureRecord>): List<PIHSBloodPressureData> {
        return bpRecords.map { record ->
            PIHSBloodPressureData(
                time = formatInstant(record.time),
                systolic = record.systolic.inMillimetersOfMercury,
                diastolic = record.diastolic.inMillimetersOfMercury
            )
        }
    }

    // Transform blood glucose data
    private fun transformBloodGlucoseData(bgRecords: List<BloodGlucoseRecord>): List<PIHSBloodGlucoseData> {
        return bgRecords.map { record ->
            PIHSBloodGlucoseData(
                time = formatInstant(record.time),
                level = record.level.inMillimolesPerLiter,
                specimenSource = record.specimenSource.toString(),
                mealType = record.mealType?.toString() ?: ""
            )
        }
    }

    // Transform body temperature data
    private fun transformBodyTemperatureData(tempRecords: List<BodyTemperatureRecord>): List<PIHSBodyTemperatureData> {
        return tempRecords.map { record ->
            PIHSBodyTemperatureData(
                time = formatInstant(record.time),
                temperature = record.temperature.inCelsius,
                measurementLocation = record.measurementLocation?.toString() ?: ""
            )
        }
    }

    // Transform oxygen saturation data
    private fun transformOxygenSaturationData(o2Records: List<OxygenSaturationRecord>): List<PIHSOxygenSaturationData> {
        return o2Records.map { record ->
            PIHSOxygenSaturationData(
                time = formatInstant(record.time),
                saturation = record.percentage.value
            )
        }
    }

    // Transform respiratory rate data
    private fun transformRespiratoryRateData(rrRecords: List<RespiratoryRateRecord>): List<PIHSRespiratoryRateData> {
        return rrRecords.map { record ->
            PIHSRespiratoryRateData(
                time = formatInstant(record.time),
                rate = record.rate
            )
        }
    }

    // Transform nutrition data
    private fun transformNutritionData(nutritionRecords: List<NutritionRecord>): List<PIHSNutritionData> {
        return nutritionRecords.map { record ->
            PIHSNutritionData(
                startTime = formatInstant(record.startTime),
                endTime = formatInstant(record.endTime),
                name = record.name ?: "",
                calories = record.energy?.inKilocalories ?: 0.0,
                protein = record.protein?.inGrams ?: 0.0,
                fat = record.totalFat?.inGrams ?: 0.0,
                carbs = record.totalCarbohydrate?.inGrams ?: 0.0
            )
        }
    }

    // Transform hydration data
    private fun transformHydrationData(hydrationRecords: List<HydrationRecord>): List<PIHSHydrationData> {
        return hydrationRecords.map { record ->
            PIHSHydrationData(
                startTime = formatInstant(record.startTime),
                endTime = formatInstant(record.endTime),
                volume = record.volume.inLiters
            )
        }
    }

    // Format Instant to ISO string
    private fun formatInstant(instant: Instant): String {
        return dateTimeFormatter.format(instant)
    }

    // Map exercise type to human-readable name
    private fun mapExerciseTypeToReadableName(exerciseType: String): String {
        return when (exerciseType) {
            "ARCHERY" -> "Archery"
            "BADMINTON" -> "Badminton"
            "BASEBALL" -> "Baseball"
            "BASKETBALL" -> "Basketball"
            "BIKING" -> "Biking"
            "BIKING_STATIONARY" -> "Stationary Biking"
            "BOOT_CAMP" -> "Boot Camp"
            "BOXING" -> "Boxing"
            "CALISTHENICS" -> "Calisthenics"
            "CRICKET" -> "Cricket"
            "CROSSFIT" -> "CrossFit"
            "DANCING" -> "Dancing"
            "ELLIPTICAL" -> "Elliptical"
            "FENCING" -> "Fencing"
            "FOOTBALL_AMERICAN" -> "American Football"
            "FOOTBALL_AUSTRALIAN" -> "Australian Football"
            "FRISBEE_DISC" -> "Frisbee"
            "GOLF" -> "Golf"
            "GUIDED_BREATHING" -> "Guided Breathing"
            "GYMNASTICS" -> "Gymnastics"
            "HANDBALL" -> "Handball"
            "HIKING" -> "Hiking"
            "HOCKEY" -> "Hockey"
            "HORSEBACK_RIDING" -> "Horseback Riding"
            "ICE_SKATING" -> "Ice Skating"
            "IN_VEHICLE" -> "In Vehicle"
            "INTERVAL_TRAINING" -> "Interval Training"
            "JUMPING_ROPE" -> "Jumping Rope"
            "KAYAKING" -> "Kayaking"
            "KETTLEBELL_TRAINING" -> "Kettlebell Training"
            "KICKBOXING" -> "Kickboxing"
            "MARTIAL_ARTS" -> "Martial Arts"
            "MEDITATION" -> "Meditation"
            "PADDLING" -> "Paddling"
            "PARAGLIDING" -> "Paragliding"
            "PILATES" -> "Pilates"
            "RACQUETBALL" -> "Racquetball"
            "ROCK_CLIMBING" -> "Rock Climbing"
            "ROLLER_HOCKEY" -> "Roller Hockey"
            "ROWING" -> "Rowing"
            "ROWING_MACHINE" -> "Rowing Machine"
            "RUGBY" -> "Rugby"
            "RUNNING" -> "Running"
            "RUNNING_TREADMILL" -> "Treadmill Running"
            "SAILING" -> "Sailing"
            "SCUBA_DIVING" -> "Scuba Diving"
            "SKATING" -> "Skating"
            "SKIING" -> "Skiing"
            "SNOWBOARDING" -> "Snowboarding"
            "SNOWSHOEING" -> "Snowshoeing"
            "SOCCER" -> "Soccer"
            "SOFTBALL" -> "Softball"
            "SQUASH" -> "Squash"
            "STAIR_CLIMBING" -> "Stair Climbing"
            "STAIR_CLIMBING_MACHINE" -> "Stair Climbing Machine"
            "STRENGTH_TRAINING" -> "Strength Training"
            "STRETCHING" -> "Stretching"
            "SURFING" -> "Surfing"
            "SWIMMING_OPEN_WATER" -> "Open Water Swimming"
            "SWIMMING_POOL" -> "Pool Swimming"
            "TABLE_TENNIS" -> "Table Tennis"
            "TENNIS" -> "Tennis"
            "VOLLEYBALL" -> "Volleyball"
            "WALKING" -> "Walking"
            "WATER_POLO" -> "Water Polo"
            "WEIGHTLIFTING" -> "Weightlifting"
            "WHEELCHAIR" -> "Wheelchair"
            "YOGA" -> "Yoga"
            "OTHER_WORKOUT" -> "Other Workout"
            "UNKNOWN" -> "Unknown"
            "53" -> "Workout"
            else -> exerciseType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}

// PIHS data models for Supabase upload

@Serializable
data class PIHSHealthData(
    val steps: List<PIHSStepsData> = emptyList(),
    val sleep: List<PIHSSleepData> = emptyList(),
    val heartRate: List<PIHSHeartRateData> = emptyList(),
    val exercise: List<PIHSExerciseData> = emptyList(),
    val weight: List<PIHSWeightData> = emptyList(),
    val bloodPressure: List<PIHSBloodPressureData> = emptyList(),
    val bloodGlucose: List<PIHSBloodGlucoseData> = emptyList(),
    val bodyTemperature: List<PIHSBodyTemperatureData> = emptyList(),
    val oxygenSaturation: List<PIHSOxygenSaturationData> = emptyList(),
    val respiratoryRate: List<PIHSRespiratoryRateData> = emptyList(),
    val nutrition: List<PIHSNutritionData> = emptyList(),
    val hydration: List<PIHSHydrationData> = emptyList()
)

@Serializable
data class PIHSStepsData(
    val startTime: String,
    val endTime: String,
    val count: Long
)

@Serializable
data class PIHSSleepData(
    val startTime: String,
    val endTime: String,
    val duration: Long,
    val stages: List<PIHSSleepStage> = emptyList(),
    val totalSleepDurationMinutes: Double = 0.0,
    val deepSleepDurationMinutes: Double = 0.0,
    val lightSleepDurationMinutes: Double = 0.0,
    val remSleepDurationMinutes: Double = 0.0,
    val sleepScore: Double = 0.0,
    val sleepEfficiencyPercentage: Double = 0.0,
    val sleepLatencyMinutes: Double = 0.0,
    val awakeningsCount: Int = 0,
    val timeInBedMinutes: Double = 0.0
)

@Serializable
data class PIHSSleepStage(
    val startTime: String,
    val endTime: String,
    val stage: String
)

@Serializable
data class PIHSHeartRateSample(
    val time: String,
    val beatsPerMinute: Long
)

@Serializable
data class PIHSHeartRateData(
    val samples: List<PIHSHeartRateSample>
)

@Serializable
data class PIHSExerciseData(
    val startTime: String,
    val endTime: String,
    val exerciseType: String,
    val title: String,
    val notes: String,
    val calories: Double,
    val durationMinutes: Double = 0.0,
    val distanceKm: Double = 0.0,
    val averageHeartRateBpm: Double = 0.0,
    val maxHeartRateBpm: Double = 0.0,
    val intensityManual: Int = 0,
    val stepsCount: Long = 0,
    val activeEnergyKcal: Double = 0.0,
    val segments: List<PIHSExerciseSegment> = emptyList(),
    val routes: List<PIHSExerciseRoute> = emptyList()
)

@Serializable
data class PIHSExerciseSegment(
    val startTime: String,
    val endTime: String,
    val segmentType: String
)

@Serializable
data class PIHSExerciseRoute(
    val startTime: String,
    val endTime: String,
    val waypoints: List<PIHSExerciseRouteWaypoint> = emptyList()
)

@Serializable
data class PIHSExerciseRouteWaypoint(
    val time: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null
)

@Serializable
data class PIHSWeightData(
    val time: String,
    val weight: Double
)

@Serializable
data class PIHSBloodPressureData(
    val time: String,
    val systolic: Double,
    val diastolic: Double
)

@Serializable
data class PIHSBloodGlucoseData(
    val time: String,
    val level: Double,
    val specimenSource: String,
    val mealType: String
)

@Serializable
data class PIHSBodyTemperatureData(
    val time: String,
    val temperature: Double,
    val measurementLocation: String
)

@Serializable
data class PIHSOxygenSaturationData(
    val time: String,
    val saturation: Double
)

@Serializable
data class PIHSRespiratoryRateData(
    val time: String,
    val rate: Double
)

@Serializable
data class PIHSNutritionData(
    val startTime: String,
    val endTime: String,
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double
)

@Serializable
data class PIHSHydrationData(
    val startTime: String,
    val endTime: String,
    val volume: Double
)
