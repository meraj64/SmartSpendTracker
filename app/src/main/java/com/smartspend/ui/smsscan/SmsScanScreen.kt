package com.smartspend.ui.smsscan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartspend.ui.common.EmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScanScreen(
    onBack: () -> Unit = {},
    viewModel: SmsScanViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasSmsPermission = granted }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan SMS for Transactions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "Scans your SMS inbox for a date range and adds every debited (spent/paid/withdrawn) transaction it finds as an expense. Messages already recorded won't be added twice.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            if (!hasSmsPermission) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "SMS permission is needed to scan your inbox.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_SMS) }) {
                            Text("Grant SMS Permission")
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            OutlinedTextField(
                value = dateFormat.format(Date(state.fromDate)),
                onValueChange = {}, readOnly = true,
                label = { Text("From") },
                modifier = Modifier.fillMaxWidth().clickable { showFromPicker = true },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = dateFormat.format(Date(state.toDate)),
                onValueChange = {}, readOnly = true,
                label = { Text("To") },
                modifier = Modifier.fillMaxWidth().clickable { showToPicker = true },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.scan() },
                enabled = hasSmsPermission && state.step != ScanStep.SCANNING,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Sms, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.step == ScanStep.SCANNING) "Scanning…" else "Scan & Import")
            }

            Spacer(Modifier.height(24.dp))

            when (state.step) {
                ScanStep.SCANNING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Found ${state.debitedFound} transaction message(s) so far · queued ${state.imported}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ScanStep.DONE -> {
                    ScanResultsCard(state)
                }
                ScanStep.ERROR -> {
                    Text(
                        state.errorMessage ?: "Something went wrong",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                ScanStep.IDLE -> {
                    EmptyState(
                        icon = "📩",
                        title = "Ready to scan",
                        subtitle = "Pick a date range above and tap Scan & Import"
                    )
                }
            }
        }
    }

    if (showFromPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.fromDate)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.setFromDate(it) }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    if (showToPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.toDate)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.setToDate(it) }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun ScanResultsCard(state: SmsScanState) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Scan complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            ResultRow("Messages scanned", state.totalMessagesScanned.toString())
            ResultRow("Transaction messages found", state.debitedFound.toString())
            ResultRow("Added to pending queue", state.imported.toString())
            ResultRow("Skipped (already recorded)", state.skippedDuplicate.toString())
            Spacer(Modifier.height(8.dp))
            Text(
                "Nothing was added to your totals yet — review each one from \"Action needed\" " +
                    "on your dashboard to confirm, edit, or discard it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
