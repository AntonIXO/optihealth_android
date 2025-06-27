package org.devpins.pihs.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.devpins.pihs.data.model.DataPoint
import org.devpins.pihs.data.remote.DataUploaderService
import org.devpins.pihs.data.remote.UploadResult
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ExampleHealthViewModel @Inject constructor(
    private val dataUploaderService: DataUploaderService
) : ViewModel() {

    companion object {
        private const val TAG = "ExampleHealthViewModel"
    }

    // Example function to simulate gathering and uploading data
    fun collectAndUploadSampleData() {
        viewModelScope.launch {
            // 1. Create some sample data points
            val sampleDataPoints = createSampleDataPoints()

            // 2. Call the uploader service
            Log.d(TAG, "Attempting to upload ${sampleDataPoints.size} data points...")
            when (val result = dataUploaderService.uploadDataPoints(sampleDataPoints)) {
                is UploadResult.Success -> {
                    Log.i(
                        TAG,
                        "Upload successful: ${result.response.message}, " +
                                "Received: ${result.response.receivedCount}, " +
                                "Inserted: ${result.response.insertedCount}"
                    )
                    // Update UI accordingly (e.g., show success message)
                }
                is UploadResult.Failure -> {
                    Log.e(TAG, "Upload failed: ${result.errorMessage}", result.exception)
                    // Update UI accordingly (e.g., show error message)
                }
            }
        }
    }

    private fun createSampleDataPoints(): List<DataPoint> {
        val now = Instant.now()
        val timestampFormat = DateTimeFormatter.ISO_INSTANT // Ensures ISO 8601

        return listOf(
            DataPoint(
                metricSourceId = 1L,
                timestamp = timestampFormat.format(now.minusSeconds(3600)),
                metricName = "heart_rate",
                valueNumeric = 75.0,
                valueText = null,
                valueJson = null,
                unit = "bpm",
                tags = buildJsonObject { put("activity", "resting") }
            ),
            DataPoint(
                metricSourceId = 2L,
                timestamp = timestampFormat.format(now.minusSeconds(1800)),
                metricName = "steps_count",
                valueNumeric = 500.0,
                valueText = null,
                valueJson = null,
                unit = "steps",
                tags = buildJsonObject { put("source", "pedometer") }
            ),
            DataPoint(
                metricSourceId = 1L,
                timestamp = timestampFormat.format(now),
                metricName = "blood_glucose",
                valueNumeric = 5.5,
                valueText = null,
                valueJson = buildJsonObject { /* Can be more complex if needed */ },
                unit = "mmol/L",
                tags = null
            )
        )
    }

    // Example of uploading an empty list
    fun uploadEmptyData() {
        viewModelScope.launch {
            Log.d(TAG, "Attempting to upload an empty list of data points...")
            when (val result = dataUploaderService.uploadDataPoints(emptyList())) {
                is UploadResult.Success -> {
                     Log.i(TAG, "Upload (empty list) successful: ${result.response.message}")
                }
                is UploadResult.Failure -> {
                    Log.e(TAG, "Upload (empty list) failed: ${result.errorMessage}", result.exception)
                }
            }
        }
    }
}
