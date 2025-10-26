package org.devpins.pihs.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.devpins.pihs.data.model.MetricDefinition
import org.devpins.pihs.ui.viewmodel.ManualDataUiState
import org.devpins.pihs.ui.viewmodel.ManualDataViewModel
import org.devpins.pihs.ui.viewmodel.SubmissionState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualDataEntryScreen(
    viewModel: ManualDataViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val submissionState by viewModel.submissionState.collectAsState()

    var selectedMetric by remember { mutableStateOf<MetricDefinition?>(null) }
    var numericValue by remember { mutableStateOf("") }
    var textValue by remember { mutableStateOf("") }
    var selectedDateTime by remember { mutableStateOf(LocalDateTime.now()) }
    var showDateTimePicker by remember { mutableStateOf(false) }

    // Reset form after successful submission
    LaunchedEffect(submissionState) {
        if (submissionState is SubmissionState.Success) {
            delay(2000)
            selectedMetric = null
            numericValue = ""
            textValue = ""
            selectedDateTime = LocalDateTime.now()
            viewModel.resetSubmissionState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Add Health Data",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (uiState) {
                is ManualDataUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ManualDataUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "Error Loading Metrics",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                (uiState as ManualDataUiState.Error).message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.loadMetricDefinitions() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                is ManualDataUiState.Success -> {
                    val metrics = (uiState as ManualDataUiState.Success).metrics

                    // Metric Selection
                    MetricSelector(
                        metrics = metrics,
                        selectedMetric = selectedMetric,
                        onMetricSelected = { 
                            selectedMetric = it
                            // Reset values when metric changes
                            numericValue = ""
                            textValue = ""
                        }
                    )

                    // Value Input
                    if (selectedMetric != null) {
                        ValueInputSection(
                            metric = selectedMetric!!,
                            numericValue = numericValue,
                            textValue = textValue,
                            onNumericValueChange = { numericValue = it },
                            onTextValueChange = { textValue = it }
                        )

                        // Date Time Picker
                        DateTimePickerSection(
                            selectedDateTime = selectedDateTime,
                            onDateTimeClick = { showDateTimePicker = true }
                        )

                        // Submission State Messages
                        when (submissionState) {
                            is SubmissionState.Submitting -> {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is SubmissionState.Success -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            (submissionState as SubmissionState.Success).message,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            is SubmissionState.Error -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        (submissionState as SubmissionState.Error).message,
                                        modifier = Modifier.padding(16.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            else -> {}
                        }

                        // Submit Button
                        Button(
                            onClick = {
                                val instant = selectedDateTime
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()

                                viewModel.submitDataPoint(
                                    metricDefinition = selectedMetric!!,
                                    valueNumeric = numericValue.toDoubleOrNull(),
                                    valueText = textValue.takeIf { it.isNotBlank() },
                                    timestamp = instant
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = submissionState !is SubmissionState.Submitting &&
                                    (numericValue.isNotBlank() || textValue.isNotBlank()),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Add Data Point",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDateTimePicker) {
        DateTimePickerDialog(
            initialDateTime = selectedDateTime,
            onDismiss = { showDateTimePicker = false },
            onConfirm = { 
                selectedDateTime = it
                showDateTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricSelector(
    metrics: List<MetricDefinition>,
    selectedMetric: MetricDefinition?,
    onMetricSelected: (MetricDefinition) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Select Metric",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedMetric?.getDisplayName() ?: "Choose a metric...",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    metrics.forEach { metric ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        metric.getDisplayName(),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "${metric.category} • ${metric.defaultUnit ?: "no unit"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onMetricSelected(metric)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ValueInputSection(
    metric: MetricDefinition,
    numericValue: String,
    textValue: String,
    onNumericValueChange: (String) -> Unit,
    onTextValueChange: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Value",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Numeric input for most metrics
            OutlinedTextField(
                value = numericValue,
                onValueChange = onNumericValueChange,
                label = { Text("Numeric Value") },
                suffix = {
                    metric.defaultUnit?.let { unit ->
                        Text(unit, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Text input for additional notes or text-based metrics
            OutlinedTextField(
                value = textValue,
                onValueChange = onTextValueChange,
                label = { Text("Text/Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )
        }
    }
}

@Composable
fun DateTimePickerSection(
    selectedDateTime: LocalDateTime,
    onDateTimeClick: () -> Unit
) {
    Card(
        onClick = onDateTimeClick,
        shape = RoundedCornerShape(12.dp),
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
                "Date & Time",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                selectedDateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a")),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Tap to change",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initialDateTime: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    var selectedDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedHour by remember { mutableStateOf(initialDateTime.hour) }
    var selectedMinute by remember { mutableStateOf(initialDateTime.minute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Date & Time") },
        text = {
            Column {
                // Date Picker
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = selectedDate
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                )
                
                DatePicker(
                    state = datePickerState,
                    showModeToggle = false
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Time Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Hour
                    OutlinedTextField(
                        value = selectedHour.toString().padStart(2, '0'),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let {
                                if (it in 0..23) selectedHour = it
                            }
                        },
                        label = { Text("Hour") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    
                    // Minute
                    OutlinedTextField(
                        value = selectedMinute.toString().padStart(2, '0'),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let {
                                if (it in 0..59) selectedMinute = it
                            }
                        },
                        label = { Text("Min") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                
                // Update selected date from picker
                LaunchedEffect(datePickerState.selectedDateMillis) {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dateTime = LocalDateTime.of(selectedDate, java.time.LocalTime.of(selectedHour, selectedMinute))
                    onConfirm(dateTime)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
