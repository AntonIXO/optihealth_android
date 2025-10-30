package org.devpins.pihs.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.devpins.pihs.data.model.supplement.*
import org.devpins.pihs.data.repository.SupplementRepository
import java.time.Instant
import javax.inject.Inject

/**
 * ViewModel for the Supplement Dashboard (Job 2: High-Speed Logging).
 * Manages cabinet products and today's logs.
 */
@HiltViewModel
class SupplementViewModel @Inject constructor(
    private val repository: SupplementRepository,
    private val auth: Auth
) : ViewModel() {

    companion object {
        private const val TAG = "SupplementViewModel"
    }

    private val _cabinetProducts = MutableStateFlow<List<Product>>(emptyList())
    val cabinetProducts: StateFlow<List<Product>> = _cabinetProducts.asStateFlow()

    private val _todaysLogs = MutableStateFlow<List<SupplementLog>>(emptyList())
    val todaysLogs: StateFlow<List<SupplementLog>> = _todaysLogs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _logSuccess = MutableStateFlow(false)
    val logSuccess: StateFlow<Boolean> = _logSuccess.asStateFlow()

    init {
        loadData()
    }

    /**
     * Load cabinet products and today's logs.
     */
    fun loadData() {
        viewModelScope.launch {
            val userId = auth.currentUserOrNull()?.id ?: return@launch
            _isLoading.value = true
            
            // Load cabinet products
            repository.getCabinetProducts(userId).fold(
                onSuccess = { products ->
                    _cabinetProducts.value = products
                    Log.d(TAG, "Loaded ${products.size} cabinet products")
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to load products: ${error.message}"
                    Log.e(TAG, "Error loading cabinet products", error)
                }
            )

            // Load today's logs
            repository.getTodaysLogs(userId).fold(
                onSuccess = { logs ->
                    _todaysLogs.value = logs
                    Log.d(TAG, "Loaded ${logs.size} logs for today")
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to load logs: ${error.message}"
                    Log.e(TAG, "Error loading today's logs", error)
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Log a supplement intake (the "3-Tap Log" action).
     */
    fun logSupplement(
        productId: String,
        dosageAmount: Double,
        dosageUnit: String,
        intakeForm: String,
        notes: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _logSuccess.value = false

            val entry = NewSupplementLog(
                productId = productId,
                dosageAmount = dosageAmount,
                dosageUnit = dosageUnit,
                intakeForm = intakeForm,
                timestamp = Instant.now().toString(),
                notes = notes
            )

            repository.logSupplement(entry).fold(
                onSuccess = {
                    Log.d(TAG, "Successfully logged supplement")
                    _logSuccess.value = true
                    // Reload today's logs to show the new entry
                    loadData()
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to log supplement: ${error.message}"
                    Log.e(TAG, "Error logging supplement", error)
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Reset log success flag.
     */
    fun resetLogSuccess() {
        _logSuccess.value = false
    }
}
