package org.devpins.pihs.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.devpins.pihs.data.model.supplement.Compound
import org.devpins.pihs.data.model.supplement.Vendor
import org.devpins.pihs.ui.viewmodel.AddProductViewModel

/**
 * Add Product Wizard Screen (Job 1: Add New Product).
 * Multi-step wizard: 1) Find Compound, 2) Find Vendor, 3) Define Product.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductWizardScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddProductViewModel = hiltViewModel()
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()

    val context = LocalContext.current

    // Navigate back on successful save
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            Toast.makeText(context, "Product added to cabinet!", Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
    }

    // Show error toast
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
                        "Add Product - Step $currentStep of 3",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 1) {
                            viewModel.previousStep()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentStep) {
                1 -> Step1FindCompound(viewModel = viewModel)
                2 -> Step2FindVendor(viewModel = viewModel)
                3 -> Step3DefineProduct(viewModel = viewModel)
            }

            // Loading overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/**
 * Step 1: Find Compound (The "What").
 */
@Composable
fun Step1FindCompound(
    viewModel: AddProductViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.compoundSearchQuery.collectAsState()
    val searchResults by viewModel.compoundSearchResults.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "What supplement are you taking?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Search for the active compound or ingredient",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchCompounds(it) },
            label = { Text("Search compound") },
            placeholder = { Text("e.g., Magnesium L-Threonate") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.length < 2) {
            Text(
                text = "Type at least 2 characters to search",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (searchResults.isEmpty()) {
            Text(
                text = "No compounds found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults) { compound ->
                    CompoundResultCard(
                        compound = compound,
                        onClick = { viewModel.selectCompound(compound) }
                    )
                }
            }
        }
    }
}

@Composable
fun CompoundResultCard(
    compound: Compound,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = compound.fullName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            // Show short name if different from full name
            if (compound.name != compound.fullName) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Also known as: ${compound.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            compound.description?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Step 2: Find Vendor (The "Who").
 */
@Composable
fun Step2FindVendor(
    viewModel: AddProductViewModel,
    modifier: Modifier = Modifier
) {
    val selectedCompound by viewModel.selectedCompound.collectAsState()
    val searchQuery by viewModel.vendorSearchQuery.collectAsState()
    val searchResults by viewModel.vendorSearchResults.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Who makes this supplement?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Search for the vendor/manufacturer",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Selected: ${selectedCompound?.fullName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchVendors(it) },
            label = { Text("Vendor name") },
            placeholder = { Text("e.g., Thorne, Life Extension") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isNotBlank() && searchResults.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectVendor(searchQuery) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Create New Vendor",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "\"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(searchResults) { vendor ->
                VendorResultCard(
                    vendor = vendor,
                    onClick = { viewModel.selectVendor(vendor.name) }
                )
            }
        }
    }
}

@Composable
fun VendorResultCard(
    vendor: Vendor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = vendor.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            vendor.websiteUrl?.let { website ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = website,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Step 3: Define Product (The "Bottle").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step3DefineProduct(
    viewModel: AddProductViewModel,
    modifier: Modifier = Modifier
) {
    val selectedCompound by viewModel.selectedCompound.collectAsState()
    val selectedVendor by viewModel.selectedVendorName.collectAsState()
    val nameOnBottle by viewModel.nameOnBottle.collectAsState()
    val formFactor by viewModel.formFactor.collectAsState()
    val unitDosage by viewModel.unitDosage.collectAsState()
    val unitMeasure by viewModel.unitMeasure.collectAsState()

    var showFormFactorMenu by remember { mutableStateOf(false) }
    var showUnitMeasureMenu by remember { mutableStateOf(false) }

    val formFactors = listOf("capsule", "tablet", "powder", "liquid", "gummy", "softgel")
    val unitMeasures = listOf("mg", "g", "mcg", "IU", "ml")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Product Details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tell us about this specific product",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${selectedCompound?.fullName} by $selectedVendor",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = nameOnBottle,
            onValueChange = { viewModel.updateNameOnBottle(it) },
            label = { Text("Product name (as shown on bottle)") },
            placeholder = { Text("e.g., Magtein") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = showFormFactorMenu,
            onExpandedChange = { showFormFactorMenu = it }
        ) {
            OutlinedTextField(
                value = formFactor,
                onValueChange = {},
                readOnly = true,
                label = { Text("Form Factor") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFormFactorMenu) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = showFormFactorMenu,
                onDismissRequest = { showFormFactorMenu = false }
            ) {
                formFactors.forEach { form ->
                    DropdownMenuItem(
                        text = { Text(form) },
                        onClick = {
                            viewModel.updateFormFactor(form)
                            showFormFactorMenu = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = unitDosage,
                onValueChange = { viewModel.updateUnitDosage(it) },
                label = { Text("Dosage per unit") },
                placeholder = { Text("144") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            ExposedDropdownMenuBox(
                expanded = showUnitMeasureMenu,
                onExpandedChange = { showUnitMeasureMenu = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = unitMeasure,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Unit") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUnitMeasureMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = showUnitMeasureMenu,
                    onDismissRequest = { showUnitMeasureMenu = false }
                ) {
                    unitMeasures.forEach { measure ->
                        DropdownMenuItem(
                            text = { Text(measure) },
                            onClick = {
                                viewModel.updateUnitMeasure(measure)
                                showUnitMeasureMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.saveProduct() },
            modifier = Modifier.fillMaxWidth(),
            enabled = nameOnBottle.isNotBlank() && 
                     unitDosage.toDoubleOrNull() != null && 
                     unitDosage.toDoubleOrNull()!! > 0
        ) {
            Text("Save to Cabinet")
        }
    }
}
