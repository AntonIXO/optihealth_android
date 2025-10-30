package org.devpins.pihs.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.devpins.pihs.data.model.supplement.Product
import org.devpins.pihs.data.model.supplement.SupplementLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Horizontal scrolling widget showing products in user's cabinet.
 * The "3-Tap Log" starts here (Tap 1: Select Product).
 */
@Composable
fun MyCabinetWidget(
    products: List<Product>,
    onProductClick: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "My Cabinet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (products.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "No products in cabinet. Add one to get started!",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(products) { product ->
                    ProductButton(
                        product = product,
                        onClick = { onProductClick(product) }
                    )
                }
            }
        }
    }
}

/**
 * Button representing a product in the cabinet.
 */
@Composable
fun ProductButton(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = product.nameOnBottle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${product.unitDosage}${product.unitMeasure} ${product.formFactor}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Quick Log Modal - Bottom sheet for logging a supplement.
 * Taps 2 and 3 of the "3-Tap Log" flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLogModal(
    product: Product,
    onDismiss: () -> Unit,
    onLog: (dosageAmount: Double, dosageUnit: String, intakeForm: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var dosageAmount by remember { mutableStateOf("1") }
    var selectedIntakeForm by remember { mutableStateOf(product.defaultIntakeForm ?: "oral") }
    var showIntakeFormMenu by remember { mutableStateOf(false) }

    val intakeForms = listOf("oral", "sublingual", "topical", "injection")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Log Supplement",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = product.nameOnBottle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dosage Amount Input
            OutlinedTextField(
                value = dosageAmount,
                onValueChange = { dosageAmount = it },
                label = { Text("Dosage Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                suffix = { Text(product.formFactor) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Intake Form Dropdown
            ExposedDropdownMenuBox(
                expanded = showIntakeFormMenu,
                onExpandedChange = { showIntakeFormMenu = it }
            ) {
                OutlinedTextField(
                    value = selectedIntakeForm,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Intake Form") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showIntakeFormMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = showIntakeFormMenu,
                    onDismissRequest = { showIntakeFormMenu = false }
                ) {
                    intakeForms.forEach { form ->
                        DropdownMenuItem(
                            text = { Text(form) },
                            onClick = {
                                selectedIntakeForm = form
                                showIntakeFormMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Log Button
            Button(
                onClick = {
                    val amount = dosageAmount.toDoubleOrNull() ?: 1.0
                    onLog(amount, product.formFactor, selectedIntakeForm)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = dosageAmount.toDoubleOrNull() != null && dosageAmount.toDoubleOrNull()!! > 0
            ) {
                Text("Log Now")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Widget showing today's supplement logs.
 */
@Composable
fun TodaysLogWidget(
    logs: List<SupplementLog>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Today's Logs",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (logs.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "No logs for today. Start logging!",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    LogEntryCard(log = log)
                }
            }
        }
    }
}

/**
 * Card displaying a single supplement log entry.
 */
@Composable
fun LogEntryCard(
    log: SupplementLog,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.product?.nameOnBottle ?: "Unknown Product",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${log.dosageAmount} ${log.dosageUnit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (log.calculatedDosageMg != null) {
                    Text(
                        text = "${log.calculatedDosageMg}mg total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatTime(log.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.intakeForm,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Format ISO timestamp to readable time (e.g., "9:41 PM").
 */
private fun formatTime(isoTimestamp: String): String {
    return try {
        val instant = Instant.parse(isoTimestamp)
        val formatter = DateTimeFormatter.ofPattern("h:mm a")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        "Unknown time"
    }
}
