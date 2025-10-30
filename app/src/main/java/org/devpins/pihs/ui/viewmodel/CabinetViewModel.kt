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
import org.devpins.pihs.data.model.supplement.Product
import org.devpins.pihs.data.repository.SupplementRepository
import javax.inject.Inject

/**
 * ViewModel for the Cabinet Screen.
 * Manages the user's saved supplement products.
 */
@HiltViewModel
class CabinetViewModel @Inject constructor(
    private val repository: SupplementRepository,
    private val auth: Auth
) : ViewModel() {

    companion object {
        private const val TAG = "CabinetViewModel"
    }

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    init {
        loadProducts()
    }

    /**
     * Load all products in the user's cabinet.
     */
    fun loadProducts() {
        viewModelScope.launch {
            val userId = auth.currentUserOrNull()?.id ?: return@launch
            _isLoading.value = true

            repository.getUserProducts(userId).fold(
                onSuccess = { products ->
                    _products.value = products
                    Log.d(TAG, "Loaded ${products.size} products")
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to load products: ${error.message}"
                    Log.e(TAG, "Error loading products", error)
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Archive a product (removes from cabinet for ALL users).
     * Note: Products are global, so archiving affects all users.
     * TODO: Implement user_hidden_products table for per-user hiding.
     */
    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            repository.archiveProduct(productId).fold(
                onSuccess = {
                    Log.d(TAG, "Product archived successfully")
                    _deleteSuccess.value = true
                    loadProducts() // Reload the list
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to archive product: ${error.message}"
                    Log.e(TAG, "Error archiving product", error)
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
     * Reset delete success flag.
     */
    fun resetDeleteSuccess() {
        _deleteSuccess.value = false
    }
}
