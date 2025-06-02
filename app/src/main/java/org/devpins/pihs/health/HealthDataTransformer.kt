package org.devpins.pihs.health

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
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset
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
            exercise = transformExerciseData(healthData.exercise),
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
            PIHSSleepData(
                startTime = formatInstant(record.startTime),
                endTime = formatInstant(record.endTime),
                duration = record.endTime.epochSecond - record.startTime.epochSecond,
                stages = record.stages.map { stage ->
                    PIHSSleepStage(
                        startTime = formatInstant(stage.startTime),
                        endTime = formatInstant(stage.endTime),
                        stage = stage.stage.toString()
                    )
                }
            )
        }
    }

    // Transform heart rate data
    private fun transformHeartRateData(heartRateRecords: List<HeartRateRecord>): List<PIHSHeartRateData> {
        return heartRateRecords.map { record ->
            PIHSHeartRateData(
                time = formatInstant(record.startTime),
                beatsPerMinute = record.samples.map { it.beatsPerMinute }
            )
        }
    }

    // Transform exercise data
    private fun transformExerciseData(exerciseRecords: List<ExerciseSessionRecord>): List<PIHSExerciseData> {
        return exerciseRecords.map { record ->
            PIHSExerciseData(
                startTime = formatInstant(record.startTime),
                endTime = formatInstant(record.endTime),
                exerciseType = record.exerciseType.toString(),
                title = record.title ?: "",
                notes = record.notes ?: "",
                calories = 0.0 // Energy data would need to be read separately
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
    val stages: List<PIHSSleepStage> = emptyList()
)

@Serializable
data class PIHSSleepStage(
    val startTime: String,
    val endTime: String,
    val stage: String
)

@Serializable
data class PIHSHeartRateData(
    val time: String,
    val beatsPerMinute: List<Long>
)

@Serializable
data class PIHSExerciseData(
    val startTime: String,
    val endTime: String,
    val exerciseType: String,
    val title: String,
    val notes: String,
    val calories: Double
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
