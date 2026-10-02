package com.smartspend.ui.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartspend.data.models.BudgetProgress
import com.smartspend.ui.common.*
import com.smartspend.ui.theme.statusColor
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onManageCategories: () -> Unit = {},
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var editingProgress by remember { mutableStateOf<BudgetProgress?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budget Planner") },
                actions = {
                    IconButton(onClick = onManageCategories) {
                        Icon(Icons.Default.Category, "Manage categories")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, "Add budget")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            MonthSelector(
                month = state.selectedMonth, year = state.selectedYear,
                onPrev = {
                    val newMonth = if (state.selectedMonth == 1) 12 else state.selectedMonth - 1
                    val newYear = if (state.selectedMonth == 1) state.selectedYear - 1 else state.selectedYear
                    viewModel.changeMonth(newMonth, newYear)
                },
                onNext = {
                    val newMonth = if (state.selectedMonth == 12) 1 else state.selectedMonth + 1
                    val newYear = if (state.selectedMonth == 12) state.selectedYear + 1 else state.selectedYear
                    viewModel.changeMonth(newMonth, newYear)
                }
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.budgetProgressList.isEmpty()) {
                EmptyState(
                    icon = "🎯",
                    title = "No budgets set",
                    subtitle = "Tap + to create your first monthly budget",
                    modifier = Modifier.fillMaxSize().wrapContentHeight(Alignment.CenterVertically)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.budgetProgressList, key = { it.budget.id }) { progress ->
                        BudgetProgressCard(
                            progress = progress,
                            onClick = { editingProgress = progress }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showAddSheet) {
        AddBudgetSheet(
            categories = state.availableCategories,
            existingCategoryIds = state.budgetProgressList.mapNotNull { it.budget.categoryId },
            onDismiss = { showAddSheet = false },
            onSave = { categoryId, amount ->
                viewModel.createOrUpdateBudget(categoryId, amount)
                showAddSheet = false
            }
        )
    }

    editingProgress?.let { progress ->
        EditBudgetSheet(
            progress = progress,
            onDismiss = { editingProgress = null },
            onSave = { amount ->
                viewModel.createOrUpdateBudget(progress.budget.categoryId, amount)
                editingProgress = null
            },
            onToggleNotifications = {
                viewModel.toggleNotifications(progress.budget)
                editingProgress = null
            },
            onDelete = {
                viewModel.deleteBudget(progress.budget)
                editingProgress = null
            }
        )
    }
}

@Composable
private fun MonthSelector(month: Int, year: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(
        Calendar.getInstance().apply { set(Calendar.MONTH, month - 1) }.time
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Previous month") }
        Text("$monthName $year", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Next month") }
    }
}

@Composable
private fun BudgetProgressCard(progress: BudgetProgress, onClick: () -> Unit) {
    val title = progress.category?.name ?: "Overall Budget"
    val emoji = progress.category?.emoji ?: "💰"
    val color = statusColor(progress.status)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BudgetCircularProgress(
                    progress = progress.percentUsed,
                    status = progress.status,
                    sizeDp = 56,
                    strokeDp = 6
                ) {
                    Text("${progress.percentUsed.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(emoji, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (!progress.budget.notificationsEnabled) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.NotificationsOff, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatCurrency(progress.spent, compact = true)} of ${formatCurrency(progress.budget.amount, compact = true)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (progress.isOverBudget) "Over by" else "Remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatCurrency(kotlin.math.abs(progress.remaining), compact = true),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            BudgetLinearProgress(progress.percentUsed, progress.status)

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${progress.daysRemainingInMonth} days left",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Est. month-end: ${formatCurrency(progress.estimatedMonthEndSpend, compact = true)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (progress.estimatedMonthEndSpend > progress.budget.amount) color
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetSheet(
    categories: List<com.smartspend.data.models.Category>,
    existingCategoryIds: List<Long>,
    onDismiss: () -> Unit,
    onSave: (categoryId: Long?, amount: Double) -> Unit
) {
    var isOverall by remember { mutableStateOf(true) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var amount by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val availableCategories = categories.filter { it.id !in existingCategoryIds }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("New Budget", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = isOverall,
                    onClick = { isOverall = true; selectedCategoryId = null },
                    label = { Text("Overall Budget") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = !isOverall,
                    onClick = { isOverall = false },
                    label = { Text("Category Budget") }
                )
            }
            Spacer(Modifier.height(16.dp))

            if (!isOverall) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = categories.firstOrNull { it.id == selectedCategoryId }?.let { "${it.emoji} ${it.name}" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Category") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        availableCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${cat.emoji} ${cat.name}") },
                                onClick = { selectedCategoryId = cat.id; expanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Budget Amount") },
                prefix = { Text("₹") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onSave(selectedCategoryId, amount.toDoubleOrNull() ?: 0.0) },
                enabled = amount.toDoubleOrNull() != null && (isOverall || selectedCategoryId != null),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create Budget") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBudgetSheet(
    progress: BudgetProgress,
    onDismiss: () -> Unit,
    onSave: (amount: Double) -> Unit,
    onToggleNotifications: () -> Unit,
    onDelete: () -> Unit
) {
    var amount by remember { mutableStateOf(progress.budget.amount.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }) }
    val title = progress.category?.name ?: "Overall Budget"
    val emoji = progress.category?.emoji ?: "💰"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("$emoji $title", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Budget Amount") },
                prefix = { Text("₹") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Budget alerts", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = progress.budget.notificationsEnabled,
                    onCheckedChange = { onToggleNotifications() }
                )
            }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onSave(amount.toDoubleOrNull() ?: 0.0) },
                enabled = amount.toDoubleOrNull() != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Changes") }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete Budget") }
        }
    }
}
