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
import javax.inject.Inject

/**
 * ViewModel for the Add Product Wizard (Job 1).
 * Manages the multi-step process of adding a new supplement product.
 */
@HiltViewModel
class AddProductViewModel @Inject constructor(
    private val repository: SupplementRepository,
    private val auth: Auth
) : ViewModel() {

    companion object {
        private const val TAG = "AddProductViewModel"
    }

    // Step 1: Compound Selection
    private val _compoundSearchQuery = MutableStateFlow("")
    val compoundSearchQuery: StateFlow<String> = _compoundSearchQuery.asStateFlow()

    private val _compoundSearchResults = MutableStateFlow<List<Compound>>(emptyList())
    val compoundSearchResults: StateFlow<List<Compound>> = _compoundSearchResults.asStateFlow()

    private val _selectedCompound = MutableStateFlow<Compound?>(null)
    val selectedCompound: StateFlow<Compound?> = _selectedCompound.asStateFlow()

    // Step 2: Vendor Selection
    private val _vendorSearchQuery = MutableStateFlow("")
    val vendorSearchQuery: StateFlow<String> = _vendorSearchQuery.asStateFlow()

    private val _vendorSearchResults = MutableStateFlow<List<Vendor>>(emptyList())
    val vendorSearchResults: StateFlow<List<Vendor>> = _vendorSearchResults.asStateFlow()

    private val _selectedVendorName = MutableStateFlow("")
    val selectedVendorName: StateFlow<String> = _selectedVendorName.asStateFlow()

    // Step 3: Product Details
    private val _nameOnBottle = MutableStateFlow("")
    val nameOnBottle: StateFlow<String> = _nameOnBottle.asStateFlow()

    private val _formFactor = MutableStateFlow("capsule")
    val formFactor: StateFlow<String> = _formFactor.asStateFlow()

    private val _unitDosage = MutableStateFlow("")
    val unitDosage: StateFlow<String> = _unitDosage.asStateFlow()

    private val _unitMeasure = MutableStateFlow("mg")
    val unitMeasure: StateFlow<String> = _unitMeasure.asStateFlow()

    // UI State
    private val _currentStep = MutableStateFlow(1)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    /**
     * Search for compounds by name (Step 1).
     */
    fun searchCompounds(query: String) {
        _compoundSearchQuery.value = query
        if (query.length < 2) {
            _compoundSearchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            repository.searchCompounds(query).fold(
                onSuccess = { compounds ->
                    _compoundSearchResults.value = compounds
                    Log.d(TAG, "Found ${compounds.size} compounds")
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to search compounds: ${error.message}"
                    Log.e(TAG, "Error searching compounds", error)
                }
            )
            _isLoading.value = false
        }
    }

    /**
     * Select a compound and move to Step 2.
     */
    fun selectCompound(compound: Compound) {
        _selectedCompound.value = compound
        _currentStep.value = 2
        Log.d(TAG, "Selected compound: ${compound.fullName}")
    }

    /**
     * Search for vendors by name (Step 2).
     */
    fun searchVendors(query: String) {
        _vendorSearchQuery.value = query
        if (query.length < 2) {
            _vendorSearchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            repository.searchVendors(query).fold(
                onSuccess = { vendors ->
                    _vendorSearchResults.value = vendors
                    Log.d(TAG, "Found ${vendors.size} vendors")
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to search vendors: ${error.message}"
                    Log.e(TAG, "Error searching vendors", error)
                }
            )
            _isLoading.value = false
        }
    }

    /**
     * Select a vendor and move to Step 3.
     */
    fun selectVendor(vendorName: String) {
        _selectedVendorName.value = vendorName
        _currentStep.value = 3
        Log.d(TAG, "Selected vendor: $vendorName")
    }

    /**
     * Update product details (Step 3).
     */
    fun updateNameOnBottle(value: String) {
        _nameOnBottle.value = value
    }

    fun updateFormFactor(value: String) {
        _formFactor.value = value
    }

    fun updateUnitDosage(value: String) {
        _unitDosage.value = value
    }

    fun updateUnitMeasure(value: String) {
        _unitMeasure.value = value
    }

    /**
     * Save the new product to the database.
     */
    fun saveProduct() {
        val compound = _selectedCompound.value
        if (compound == null) {
            _errorMessage.value = "Please select a compound"
            return
        }

        val vendorName = _selectedVendorName.value
        if (vendorName.isBlank()) {
            _errorMessage.value = "Please select or enter a vendor name"
            return
        }

        val nameOnBottle = _nameOnBottle.value
        if (nameOnBottle.isBlank()) {
            _errorMessage.value = "Please enter the product name on bottle"
            return
        }

        val unitDosage = _unitDosage.value.toDoubleOrNull()
        if (unitDosage == null || unitDosage <= 0) {
            _errorMessage.value = "Please enter a valid unit dosage"
            return
        }

        viewModelScope.launch {
            val userId = auth.currentUserOrNull()?.id
            if (userId == null) {
                _errorMessage.value = "User not authenticated"
                return@launch
            }

            _isLoading.value = true

            val params = AddProductParams(
                userId = userId,
                compoundId = compound.id,
                vendorName = vendorName,
                nameOnBottle = nameOnBottle,
                formFactor = _formFactor.value,
                unitDosage = unitDosage,
                unitMeasure = _unitMeasure.value
            )

            repository.addNewProduct(params).fold(
                onSuccess = { product ->
                    Log.d(TAG, "Product added successfully: ${product.nameOnBottle}")
                    _saveSuccess.value = true
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to save product: ${error.message}"
                    Log.e(TAG, "Error saving product", error)
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Navigate to previous step.
     */
    fun previousStep() {
        if (_currentStep.value > 1) {
            _currentStep.value -= 1
        }
    }

    /**
     * Reset the wizard to initial state.
     */
    fun reset() {
        _compoundSearchQuery.value = ""
        _compoundSearchResults.value = emptyList()
        _selectedCompound.value = null
        _vendorSearchQuery.value = ""
        _vendorSearchResults.value = emptyList()
        _selectedVendorName.value = ""
        _nameOnBottle.value = ""
        _formFactor.value = "capsule"
        _unitDosage.value = ""
        _unitMeasure.value = "mg"
        _currentStep.value = 1
        _saveSuccess.value = false
        _errorMessage.value = null
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
