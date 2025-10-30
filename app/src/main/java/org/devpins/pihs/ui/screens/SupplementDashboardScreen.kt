package org.devpins.pihs.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.devpins.pihs.data.model.supplement.Product
import org.devpins.pihs.ui.components.MyCabinetWidget
import org.devpins.pihs.ui.components.QuickLogModal
import org.devpins.pihs.ui.components.TodaysLogWidget
import org.devpins.pihs.ui.viewmodel.SupplementViewModel

/**
 * Main Supplement Dashboard Screen (Job 2: High-Speed Logging).
 * Focus: Quick 3-tap logging workflow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementDashboardScreen(
    onNavigateToCabinet: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SupplementViewModel = hiltViewModel()
) {
    val cabinetProducts by viewModel.cabinetProducts.collectAsState()
    val todaysLogs by viewModel.todaysLogs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val logSuccess by viewModel.logSuccess.collectAsState()

    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var showQuickLogModal by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Show toast on log success
    LaunchedEffect(logSuccess) {
        if (logSuccess) {
            Toast.makeText(context, "Supplement logged successfully!", Toast.LENGTH_SHORT).show()
            viewModel.resetLogSuccess()
            showQuickLogModal = false
        }
    }

    // Show toast on error
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Supplements",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    TextButton(onClick = onNavigateToCabinet) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manage Cabinet")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading && cabinetProducts.isEmpty() && todaysLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // My Cabinet Widget - Horizontal scrolling product buttons
                MyCabinetWidget(
                    products = cabinetProducts,
                    onProductClick = { product ->
                        selectedProduct = product
                        showQuickLogModal = true
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Today's Logs Widget
                TodaysLogWidget(
                    logs = todaysLogs,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Quick Log Modal (Bottom Sheet)
        if (showQuickLogModal && selectedProduct != null) {
            QuickLogModal(
                product = selectedProduct!!,
                onDismiss = { 
                    showQuickLogModal = false
                    selectedProduct = null
                },
                onLog = { dosageAmount, dosageUnit, intakeForm ->
                    viewModel.logSupplement(
                        productId = selectedProduct!!.id,
                        dosageAmount = dosageAmount,
                        dosageUnit = dosageUnit,
                        intakeForm = intakeForm
                    )
                }
            )
        }
    }
}
