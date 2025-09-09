package org.devpins.pihs.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.devpins.pihs.data.model.DataPoint
import org.devpins.pihs.data.remote.DataUploaderService
import org.devpins.pihs.data.remote.UploadResult
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// Represents the state of the UI for the import process
sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    data class Success(val insertedCount: Int) : ImportState()
    data class Error(val message: String) : ImportState()
}

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    private val dataUploaderService: DataUploaderService,
    @ApplicationContext private val context: Context // Hilt can provide the app context
) : ViewModel() {

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState = _importState.asStateFlow()

    companion object {
        private const val TAG = "CsvImportViewModel"
        // Define the source identifier for this import type.
        // This should match a source you've created for the user.
        // For simplicity, we'll hardcode it, but ideally, this is managed dynamically.
        private const val CSV_METRIC_SOURCE_ID = 3L // Example ID, replace with real one.
    }

    fun processCsvUri(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState.Loading
            try {
                // Perform file reading and parsing on the IO dispatcher
                val dataPoints = withContext(Dispatchers.IO) {
                    parseCsv(uri)
                }

                if (dataPoints.isEmpty()) {
                    _importState.value = ImportState.Error("CSV file is empty or invalid.")
                    return@launch
                }
                Log.d(TAG, "Parsed ${dataPoints.size} data points from CSV.")


                // Upload the parsed data
                when (val result = dataUploaderService.uploadDataPoints(dataPoints)) {
                    is UploadResult.Success -> {
                        Log.i(TAG, "CSV data uploaded successfully. Inserted: ${result.response.insertedCount}")
                        _importState.value = ImportState.Success(result.response.insertedCount)
                    }
                    is UploadResult.Failure -> {
                        Log.e(TAG, "CSV data upload failed: ${result.errorMessage}", result.exception)
                        _importState.value = ImportState.Error(result.errorMessage ?: "Unknown upload error")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing CSV file", e)
                _importState.value = ImportState.Error("Failed to parse CSV: ${e.message}")
            }
        }
    }

    // Corrected the function name from fatique to fatigue for both metric name and column header.
    private val columnToMetricMap = mapOf(
        "cognitive score" to "cognitive_score",
        "focus" to "focus",
        "chill" to "chill",
        "stress" to "stress",
        "self-control" to "self_control",
        "anger" to "anger",
        "relaxation index" to "relaxation_index",
        "concentration index" to "concentration_index",
        "fatigue score" to "fatigue_score",
        "reverse fatigue" to "reverse_fatigue",
        "alpha gravity" to "alpha_gravity",
        "heart rate" to "hr"
    )

    private fun parseCsv(uri: Uri): List<DataPoint> {
        val dataPoints = mutableListOf<DataPoint>()
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)

        requireNotNull(inputStream) { "Could not open input stream for URI: $uri" }

        csvReader {
            // Configuration, e.g., for handling potential errors
            skipEmptyLine = true
        }.open(inputStream) {
            // Read the header row to map columns dynamically
            val header = readNext()
            if (header == null) {
                Log.w(TAG, "CSV header is missing.")
                return@open
            }

            // Map header names to their index for quick lookup
            val headerIndexMap = header.withIndex().associate { (index, name) -> name.trim() to index }
            val timeIndex = headerIndexMap["time"] ?: return@open // 'time' column is mandatory

            readAllAsSequence().forEach { row ->
                try {
                    val timestampStr = row[timeIndex]
                    // The example CSV uses a space separator, not 'T'
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    val localDateTime = LocalDateTime.parse(timestampStr, formatter)
                    val timestamp = localDateTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)

                    // Iterate through our known metrics and see if they exist in the CSV header
                    for ((columnIndex, columnName) in header.withIndex()) {
                        val metricName = columnToMetricMap[columnName.trim()] ?: continue
                        val valueStr = row.getOrNull(columnIndex)

                        if (!valueStr.isNullOrBlank()) {
                            val valueNum = valueStr.toDoubleOrNull()
                            if (valueNum != null) {
                                dataPoints.add(
                                    DataPoint(
                                        metricSourceId = CSV_METRIC_SOURCE_ID,
                                        timestamp = timestamp,
                                        metricName = metricName,
                                        valueNumeric = valueNum,
                                        valueText = null,
                                        valueJson = null,
                                        unit = null,
                                        tags = null,
                                        valueGeography = null
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Skipping invalid row: $row. Error: ${e.message}")
                }
            }
        }
        Log.d(TAG, "Successfully parsed ${dataPoints.size} data points from CSV.")
        return dataPoints
    }
}