package com.smartspend.ui.khata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartspend.data.models.KhataEntryType
import com.smartspend.data.models.KhataOverallSummary
import com.smartspend.data.models.KhataPerson
import com.smartspend.data.models.KhataPersonSummary
import com.smartspend.data.models.PaymentMethod
import com.smartspend.ui.common.EmojiAvatar
import com.smartspend.ui.common.EmptyState
import com.smartspend.ui.common.formatCurrency
import com.smartspend.ui.theme.CategoryPalette
import com.smartspend.ui.theme.GivenColor
import com.smartspend.ui.theme.ReceivedColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhataScreen(
    onPersonClick: (Long) -> Unit = {},
    viewModel: KhataViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddPersonSheet by remember { mutableStateOf(false) }
    var showAddEntrySheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Khata / Cash Book") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddEntrySheet = true }) {
                Icon(Icons.Default.Add, "Add entry")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            KhataSummaryCard(state.overallSummary)

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("People", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { showAddPersonSheet = true }) { Text("+ Add Person") }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.personSummaries.isEmpty()) {
                EmptyState(
                    icon = "📒",
                    title = "No entries yet",
                    subtitle = "Add a person to start tracking who owes you",
                    modifier = Modifier.fillMaxSize().wrapContentHeight(Alignment.CenterVertically)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.personSummaries, key = { it.person.id }) { summary ->
                        PersonSummaryRow(summary, onClick = { onPersonClick(summary.person.id) })
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showAddPersonSheet) {
        AddPersonSheet(
            onDismiss = { showAddPersonSheet = false },
            onSave = { name, phone, color ->
                viewModel.addPerson(name, phone, color)
                showAddPersonSheet = false
            }
        )
    }

    if (showAddEntrySheet) {
        AddKhataEntrySheet(
            persons = state.personSummaries.map { it.person },
            onDismiss = { showAddEntrySheet = false },
            onSave = { pId, type, amount, desc, method ->
                viewModel.addEntry(pId, type, amount, desc, method, System.currentTimeMillis())
                showAddEntrySheet = false
            }
        )
    }
}

@Composable
private fun KhataSummaryCard(summary: KhataOverallSummary) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Outstanding Balance", style = MaterialTheme.typography.labelMedium)
            Text(
                formatCurrency(kotlin.math.abs(summary.outstandingBalance)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (summary.outstandingBalance >= 0) GivenColor else ReceivedColor
            )
            Text(
                if (summary.outstandingBalance >= 0) "You are owed overall" else "You owe overall",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Given",        summary.totalGiven,       GivenColor)
                MiniStat("Received",     summary.totalReceived,    ReceivedColor)
                MiniStat("Cash Out",     summary.totalCashExpense, GivenColor)
                MiniStat("Cash In",      summary.totalCashIncome,  ReceivedColor)
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            formatCurrency(amount, compact = true),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = color
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PersonSummaryRow(summary: KhataPersonSummary, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EmojiAvatar(
                emoji  = summary.person.name.take(1).uppercase(),
                colorHex = summary.person.avatarColorHex,
                sizeDp = 44
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(summary.person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                summary.person.phone?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val color = if (summary.balance >= 0) GivenColor else ReceivedColor
                Text(formatCurrency(kotlin.math.abs(summary.balance)), fontWeight = FontWeight.Bold, color = color)
                Text(
                    if (summary.balance >= 0) "owes you" else "you owe",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPersonSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String?, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val color = CategoryPalette.random()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("New Person", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Phone (optional)") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val hex = "#%06X".format(0xFFFFFF and color.toArgb())
                    onSave(name, phone.ifBlank { null }, hex)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add Person") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddKhataEntrySheet(
    persons: List<KhataPerson>,
    onDismiss: () -> Unit,
    onSave: (personId: Long?, type: KhataEntryType, amount: Double, description: String?, method: PaymentMethod) -> Unit
) {
    var type by remember { mutableStateOf(KhataEntryType.MONEY_GIVEN) }
    var personId by remember { mutableStateOf(persons.firstOrNull()?.id) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var personExpanded by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }

    val needsPerson = type == KhataEntryType.MONEY_GIVEN || type == KhataEntryType.MONEY_RECEIVED

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("New Entry", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            KhataEntryType.values().forEach { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == t, onClick = { type = t })
                    Spacer(Modifier.width(4.dp))
                    Text(t.displayName, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (needsPerson && persons.isNotEmpty()) {
                ExposedDropdownMenuBox(expanded = personExpanded, onExpandedChange = { personExpanded = it }) {
                    OutlinedTextField(
                        value = persons.firstOrNull { it.id == personId }?.name ?: "",
                        onValueChange = {}, readOnly = true,
                        label = { Text("Person") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = personExpanded, onDismissRequest = { personExpanded = false }) {
                        persons.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = { personId = p.id; personExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") }, prefix = { Text("₹") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(expanded = methodExpanded, onExpandedChange = { methodExpanded = it }) {
                OutlinedTextField(
                    value = method.name.lowercase().replaceFirstChar { it.uppercase() },
                    onValueChange = {}, readOnly = true,
                    label = { Text("Payment Method") },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                    PaymentMethod.values().forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = { method = m; methodExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    onSave(
                        if (needsPerson) personId else null,
                        type,
                        amount.toDoubleOrNull() ?: 0.0,
                        description.ifBlank { null },
                        method
                    )
                },
                enabled = amount.toDoubleOrNull() != null && (!needsPerson || personId != null),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add Entry") }
        }
    }
}
