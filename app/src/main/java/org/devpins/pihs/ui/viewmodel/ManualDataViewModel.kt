package org.devpins.pihs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.devpins.pihs.data.model.DataPoint
import org.devpins.pihs.data.model.MetricDefinition
import org.devpins.pihs.data.repository.ManualDataRepository
import org.devpins.pihs.data.repository.ManualDataResult
import org.devpins.pihs.data.repository.MetricDefinitionsRepository
import org.devpins.pihs.data.repository.MetricDefinitionsResult
import java.time.Instant
import javax.inject.Inject

sealed class ManualDataUiState {
    object Loading : ManualDataUiState()
    data class Success(val metrics: List<MetricDefinition>) : ManualDataUiState()
    data class Error(val message: String) : ManualDataUiState()
}

sealed class SubmissionState {
    object Idle : SubmissionState()
    object Submitting : SubmissionState()
    data class Success(val message: String) : SubmissionState()
    data class Error(val message: String) : SubmissionState()
}

@HiltViewModel
class ManualDataViewModel @Inject constructor(
    private val metricDefinitionsRepository: MetricDefinitionsRepository,
    private val manualDataRepository: ManualDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ManualDataUiState>(ManualDataUiState.Loading)
    val uiState: StateFlow<ManualDataUiState> = _uiState.asStateFlow()

    private val _submissionState = MutableStateFlow<SubmissionState>(SubmissionState.Idle)
    val submissionState: StateFlow<SubmissionState> = _submissionState.asStateFlow()

    init {
        loadMetricDefinitions()
    }

    fun loadMetricDefinitions() {
        viewModelScope.launch {
            _uiState.value = ManualDataUiState.Loading
            when (val result = metricDefinitionsRepository.getMetricDefinitions()) {
                is MetricDefinitionsResult.Success -> {
                    _uiState.value = ManualDataUiState.Success(result.metrics)
                }
                is MetricDefinitionsResult.Error -> {
                    _uiState.value = ManualDataUiState.Error(result.message)
                }
            }
        }
    }

    fun submitDataPoint(
        metricDefinition: MetricDefinition,
        valueNumeric: Double?,
        valueText: String?,
        timestamp: Instant
    ) {
        viewModelScope.launch {
            _submissionState.value = SubmissionState.Submitting

            // Validate input
            if (valueNumeric == null && valueText.isNullOrBlank()) {
                _submissionState.value = SubmissionState.Error("Please enter a value")
                return@launch
            }

            val dataPoint = DataPoint(
                metricSourceId = null, // Will be set by repository
                timestamp = timestamp.toString(),
                metricName = metricDefinition.metricName,
                valueNumeric = valueNumeric,
                valueText = valueText,
                valueJson = null,
                unit = metricDefinition.defaultUnit,
                tags = null,
                valueGeography = null
            )

            when (val result = manualDataRepository.insertManualDataPoint(dataPoint)) {
                is ManualDataResult.Success -> {
                    _submissionState.value = SubmissionState.Success(result.message)
                }
                is ManualDataResult.Error -> {
                    _submissionState.value = SubmissionState.Error(result.message)
                }
            }
        }
    }

    fun resetSubmissionState() {
        _submissionState.value = SubmissionState.Idle
    }
}
