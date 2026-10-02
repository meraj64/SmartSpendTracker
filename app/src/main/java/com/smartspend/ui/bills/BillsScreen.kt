package com.smartspend.ui.bills

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartspend.data.models.Bill
import com.smartspend.data.models.BillStatus
import com.smartspend.data.models.BillType
import com.smartspend.data.models.RecurrenceFrequency
import com.smartspend.ui.common.EmptyState
import com.smartspend.ui.common.StatusChip
import com.smartspend.ui.common.formatCurrency
import com.smartspend.ui.theme.StatusDanger
import com.smartspend.ui.theme.StatusOver
import com.smartspend.ui.theme.StatusSafe
import com.smartspend.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    viewModel: BillViewModel = hiltViewModel(),
    smsSyncViewModel: BillSmsSyncViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showSmsSync by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<Bill?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bills") },
                actions = {
                    IconButton(onClick = { showSmsSync = true }) {
                        Icon(Icons.Default.Sms, "Sync from SMS")
                    }
                    IconButton(onClick = { showCalendar = !showCalendar }) {
                        Icon(if (showCalendar) Icons.Default.List else Icons.Default.CalendarMonth, "Toggle view")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, "Add bill")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // Summary banner
            if (state.totalUpcoming > 0) {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Upcoming Bills Total", style = MaterialTheme.typography.labelMedium)
                            Text(
                                formatCurrency(state.totalUpcoming),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (showCalendar) {
                BillCalendarView(bills = state.bills)
            } else {
                BillListView(
                    state = state,
                    onMarkPaid = viewModel::markPaid,
                    onEdit = { editingBill = it },
                    onDelete = viewModel::deleteBill
                )
            }
        }
    }

    if (showAddSheet) {
        AddBillSheet(
            onDismiss = { showAddSheet = false },
            onSave = { title, type, amount, dueDate, repeat, customDays, notes ->
                viewModel.addBill(title, type, amount, dueDate, repeat, customDays, null, notes)
                showAddSheet = false
            }
        )
    }

    editingBill?.let { bill ->
        AddBillSheet(
            existing = bill,
            onDismiss = { editingBill = null },
            onSave = { title, type, amount, dueDate, repeat, customDays, notes ->
                viewModel.updateBill(
                    bill.copy(
                        title = title, type = type, amount = amount, dueDate = dueDate,
                        repeatFrequency = repeat, customRepeatDays = customDays, notes = notes
                    )
                )
                editingBill = null
            }
        )
    }

    if (showSmsSync) {
        BillSmsSyncDialog(
            viewModel = smsSyncViewModel,
            onDismiss = {
                showSmsSync = false
                smsSyncViewModel.reset()
            }
        )
    }
}

/**
 * "Sync from SMS" flow: reads all SMS for the current month, detects bill-due messages
 * (electricity, credit card statements, EMI reminders, etc.), and creates a Bill for each
 * one not already imported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillSmsSyncDialog(viewModel: BillSmsSyncViewModel, onDismiss: () -> Unit) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync from SMS") },
        text = {
            Column {
                Text(
                    "Scans this month's SMS for bill reminders — electricity, credit card statements, EMIs, and more — and adds each one as a bill to pay. Bills already synced won't be added twice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                if (!hasSmsPermission) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(14.dp)) {
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
                    return@Column
                }

                when (state.step) {
                    BillSyncStep.SCANNING -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Found ${state.billsFound} bill message(s) so far · created ${state.created}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BillSyncStep.DONE -> {
                        SmsSyncResultsCard(state)
                    }
                    BillSyncStep.ERROR -> {
                        Text(
                            state.errorMessage ?: "Something went wrong",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    BillSyncStep.IDLE -> {
                        Text(
                            "Tap \"Sync\" to scan this month's messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.step == BillSyncStep.DONE || state.step == BillSyncStep.ERROR) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(
                    onClick = { viewModel.syncCurrentMonth() },
                    enabled = hasSmsPermission && state.step != BillSyncStep.SCANNING
                ) { Text(if (state.step == BillSyncStep.SCANNING) "Syncing…" else "Sync") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SmsSyncResultsCard(state: BillSyncState) {
    Column {
        Text("Sync complete", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        SyncResultRow("Messages scanned", state.totalMessagesScanned.toString())
        SyncResultRow("Bill reminders found", state.billsFound.toString())
        SyncResultRow("Bills created", state.created.toString())
        SyncResultRow("Skipped (already synced)", state.skippedDuplicate.toString())

        if (state.createdBills.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Added:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            state.createdBills.forEach { bill ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${bill.type.emoji} ${bill.title}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text(formatCurrency(bill.amount, compact = true), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SyncResultRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BillListView(
    state: BillsUiState,
    onMarkPaid: (Bill) -> Unit,
    onEdit: (Bill) -> Unit,
    onDelete: (Bill) -> Unit
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.bills.isEmpty()) {
        EmptyState(
            icon = "🧾", title = "No bills yet",
            subtitle = "Add your recurring bills to never miss a due date",
            modifier = Modifier.fillMaxSize().wrapContentHeight(Alignment.CenterVertically)
        )
        return
    }

    val order = listOf(BillStatus.OVERDUE, BillStatus.PENDING, BillStatus.UPCOMING, BillStatus.PAID)
    val sectionTitles = mapOf(
        BillStatus.OVERDUE to "Overdue",
        BillStatus.PENDING to "Due Today",
        BillStatus.UPCOMING to "Upcoming",
        BillStatus.PAID to "Paid"
    )

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        order.forEach { status ->
            val bills = state.grouped[status].orEmpty()
            if (bills.isNotEmpty()) {
                item(key = "header_$status") {
                    Text(
                        sectionTitles[status] ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(bills, key = { it.id }) { bill ->
                    BillCard(bill, onMarkPaid = { onMarkPaid(bill) }, onEdit = { onEdit(bill) }, onDelete = { onDelete(bill) })
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun BillCard(bill: Bill, onMarkPaid: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val status = bill.computeStatus()
    val statusColor = when (status) {
        BillStatus.PAID -> StatusSafe
        BillStatus.UPCOMING -> StatusSafe
        BillStatus.PENDING -> StatusWarning
        BillStatus.OVERDUE -> StatusDanger
    }
    val daysRemaining = bill.daysRemaining()
    var menuExpanded by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bill.type.emoji, fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(bill.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(bill.dueDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatCurrency(bill.amount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    StatusChip(
                        text = if (status == BillStatus.OVERDUE) "Overdue ${-daysRemaining}d"
                               else if (status == BillStatus.UPCOMING) "in ${daysRemaining}d"
                               else status.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = statusColor
                    )
                }
            }

            if (!bill.isPaid) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onMarkPaid, modifier = Modifier.weight(1f)) {
                        Text("Mark Paid", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Text("Edit", fontSize = 12.sp)
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { onDelete(); menuExpanded = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun BillCalendarView(bills: List<Bill>) {
    // Simple monthly agenda-style calendar (grouped by date) — avoids heavy calendar deps
    val grouped = bills.groupBy {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.dueDate))
    }.toSortedMap()

    if (grouped.isEmpty()) {
        EmptyState(icon = "📅", title = "No bills to show", subtitle = "Your bill calendar will appear here")
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        grouped.forEach { (dateStr, billsOnDate) ->
            item(key = dateStr) {
                val displayDate = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
                    .format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)!!)
                Text(displayDate, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                billsOnDate.forEach { bill ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${bill.type.emoji} ${bill.title}", style = MaterialTheme.typography.bodyMedium)
                        Text(formatCurrency(bill.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBillSheet(
    existing: Bill? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, type: BillType, amount: Double, dueDate: Long, repeat: RecurrenceFrequency, customDays: Int?, notes: String?) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: BillType.CUSTOM) }
    var amount by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var dueDate by remember { mutableStateOf(existing?.dueDate ?: System.currentTimeMillis()) }
    var repeat by remember { mutableStateOf(existing?.repeatFrequency ?: RecurrenceFrequency.MONTHLY) }
    var customDays by remember { mutableStateOf(existing?.customRepeatDays?.toString() ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }
    var repeatExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())
        ) {
            Text(if (existing == null) "New Bill" else "Edit Bill", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                OutlinedTextField(
                    value = "${type.emoji} ${type.displayName}", onValueChange = {}, readOnly = true,
                    label = { Text("Bill Type") }, modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    BillType.values().forEach { t ->
                        DropdownMenuItem(
                            text = { Text("${t.emoji} ${t.displayName}") },
                            onClick = { type = t; typeExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Bill Title") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") }, prefix = { Text("₹") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dueDate)),
                onValueChange = {}, readOnly = true,
                label = { Text("Due Date") }, modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
            )
            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(expanded = repeatExpanded, onExpandedChange = { repeatExpanded = it }) {
                OutlinedTextField(
                    value = repeat.name.lowercase().replaceFirstChar { it.uppercase() },
                    onValueChange = {}, readOnly = true,
                    label = { Text("Repeat") }, modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = repeatExpanded, onDismissRequest = { repeatExpanded = false }) {
                    RecurrenceFrequency.values().forEach { freq ->
                        DropdownMenuItem(
                            text = { Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = { repeat = freq; repeatExpanded = false }
                        )
                    }
                }
            }

            if (repeat == RecurrenceFrequency.CUSTOM) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = customDays, onValueChange = { customDays = it.filter { c -> c.isDigit() } },
                    label = { Text("Repeat every N days") }, modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    onSave(
                        title, type, amount.toDoubleOrNull() ?: 0.0, dueDate, repeat,
                        customDays.toIntOrNull(), notes.ifBlank { null }
                    )
                },
                enabled = title.isNotBlank() && amount.toDoubleOrNull() != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (existing == null) "Add Bill" else "Save Changes") }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dueDate = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}
