package com.smartspend.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartspend.data.models.Bill
import com.smartspend.data.models.BillStatus
import com.smartspend.data.models.BudgetProgress
import com.smartspend.data.models.Category
import com.smartspend.data.models.EntryType
import com.smartspend.data.models.Expense
import com.smartspend.data.models.Transaction
import com.smartspend.ui.common.*
import com.smartspend.ui.theme.GivenColor
import com.smartspend.ui.theme.ReceivedColor
import com.smartspend.ui.theme.StatusDanger
import com.smartspend.ui.theme.StatusWarning
import com.smartspend.ui.theme.statusColor
import com.smartspend.util.LocationHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSearch: (TimeFilter) -> Unit = {},
    onAddExpense: () -> Unit = {},
    onEditExpense: (Long) -> Unit = {},
    onResolveTransaction: (Long) -> Unit = {},
    onBudgetClick: () -> Unit = {},
    onBillsClick: () -> Unit = {},
    onKhataClick: () -> Unit = {},
    onScanSms: () -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var fabExpanded by remember { mutableStateOf(false) }
    val groupedExpenses = remember(state.recentExpenses) {
        state.recentExpenses.groupBy { dateGroupLabel(it.date) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartSpend", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onScanSms) { Icon(Icons.Default.Sms, "Scan SMS for transactions") }
                    IconButton(onClick = { onSearch(state.filter) }) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense) {
                Icon(Icons.Default.Add, "Add expense")
            }
        }
    ) { padding ->

        if (state.isLoading) {
            DashboardSkeleton(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ── Time period filter ─────────────────────────────────────────
            item {
                TimeFilterBar(
                    filter = state.filter,
                    onFilterChange = viewModel::setFilter
                )
            }

            // ── Hero summary card ──────────────────────────────────────────
            item {
                HeroSummaryCard(
                    label = state.filter.label(),
                    spend = state.monthlySpend,
                    income = state.monthlyIncome
                )
            }

            // ── Action needed: SMS/notification-detected transactions awaiting review ──────
            // These replace the old "sticky notification that keeps coming back" behavior —
            // a pending detection now lives here until it's confirmed, edited, or discarded,
            // instead of nagging via a non-dismissible notification.
            if (state.pendingTransactions.isNotEmpty()) {
                item {
                    SectionHeader("Action needed")
                }
                items(state.pendingTransactions, key = { "pending_${it.id}" }) { transaction ->
                    ActionNeededCard(
                        transaction,
                        onEdit = { onResolveTransaction(transaction.id) },
                        onConfirm = { viewModel.confirmTransaction(transaction) },
                        onDiscard = { viewModel.discardTransaction(transaction) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // ── Quick stat row ─────────────────────────────────────────────
            item {
                QuickStatsRow(
                    khataBalance = state.khataBalance,
                    upcomingBillsCount = state.upcomingBills.size,
                    upcomingBillsTotal = state.upcomingBills.sumOf { it.amount },
                    onBillsClick = onBillsClick,
                    onKhataClick = onKhataClick
                )
            }

            // ── Budget progress ────────────────────────────────────────────
            if (state.budgetProgressList.isNotEmpty()) {
                item {
                    SectionHeader("Budget Progress", actionLabel = "See all", onAction = onBudgetClick)
                }
                items(state.budgetProgressList, key = { it.budget.id }) { progress ->
                    BudgetMiniCard(progress, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }

            // ── Upcoming bills ─────────────────────────────────────────────
            if (state.upcomingBills.isNotEmpty()) {
                item {
                    SectionHeader("Upcoming Bills", actionLabel = "See all", onAction = onBillsClick)
                }
                items(state.upcomingBills.take(3), key = { "bill_${it.id}" }) { bill ->
                    BillMiniRow(bill, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
                }
            }

            // ── Recent transactions, grouped by date (Google Pay style) ────
            if (state.recentExpenses.isNotEmpty()) {
                item {
                    SectionHeader("Recent Transactions")
                }
                groupedExpenses.forEach { (label, expensesForDay) ->
                    item(key = "date_$label") {
                        DateGroupHeader(label, total = expensesForDay.sumOf { it.amount })
                    }
                    items(expensesForDay, key = { "exp_${it.id}" }) { expense ->
                        ExpenseMiniRow(
                            expense,
                            category = state.categoriesById[expense.categoryId],
                            onEdit = { onEditExpense(expense.id) },
                            onDelete = { viewModel.deleteExpense(expense) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Empty state if nothing at all
            if (state.recentExpenses.isEmpty() && state.upcomingBills.isEmpty() &&
                state.budgetProgressList.isEmpty() && state.pendingTransactions.isEmpty()) {
                item {
                    EmptyState(
                        icon = "💸",
                        title = "Nothing here yet",
                        subtitle = "Add your first expense, bill, or budget to get started",
                        modifier = Modifier.fillParentMaxHeight(0.7f).wrapContentHeight()
                    )
                }
            }
        }
    }
}


@Composable
private fun HeroSummaryCard(label: String, spend: Double, income: Double) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Text(
                label,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatCurrency(spend),
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "spent",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                HeroStat("Income", income, ReceivedColor)
                HeroStat("Balance", income - spend, if (income >= spend) ReceivedColor else GivenColor)
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: Double, color: Color) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        Text(
            formatCurrency(value, compact = true),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun QuickStatsRow(
    khataBalance: Double,
    upcomingBillsCount: Int,
    upcomingBillsTotal: Double,
    onBillsClick: () -> Unit,
    onKhataClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickStatCard(
            label = "Bills Due",
            value = formatCurrency(upcomingBillsTotal, compact = true),
            sub = "$upcomingBillsCount pending",
            color = StatusWarning,
            modifier = Modifier.weight(1f),
            onClick = onBillsClick
        )
        QuickStatCard(
            label = "Khata Balance",
            value = formatCurrency(kotlin.math.abs(khataBalance), compact = true),
            sub = if (khataBalance >= 0) "owed to you" else "you owe",
            color = if (khataBalance >= 0) GivenColor else StatusDanger,
            modifier = Modifier.weight(1f),
            onClick = onKhataClick
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun QuickStatCard(
    label: String, value: String, sub: String,
    color: Color, modifier: Modifier, onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Returns "Today", "Yesterday", "Mon, 12 Jul" (this year) or "12 Jul 2025" — like Google Pay's transaction list. */
private fun dateGroupLabel(epochMillis: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    fun isSameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    return when {
        isSameDay(day, today) -> "Today"
        isSameDay(day, yesterday) -> "Yesterday"
        day.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
            SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date(epochMillis))
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochMillis))
    }
}

@Composable
private fun DateGroupHeader(label: String, total: Double, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            formatCurrency(total, compact = true),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun BudgetMiniCard(progress: BudgetProgress, modifier: Modifier = Modifier) {
    val color = statusColor(progress.status)
    Card(modifier, shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(progress.category?.emoji ?: "💰", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    progress.category?.name ?: "Overall",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                BudgetLinearProgress(progress.percentUsed, progress.status, height = 5)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "${progress.percentUsed.toInt()}%",
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ActionNeededCard(
    transaction: Transaction,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(transaction.detectedTime))

    Card(
        modifier.clickable { onEdit() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StatusWarning.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiAvatar(
                    emoji = transaction.category.emoji,
                    colorHex = "#FFA726",
                    sizeDp = 40
                )
                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.NotificationsActive, null,
                            modifier = Modifier.size(12.dp),
                            tint = StatusWarning
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Action needed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusWarning
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        transaction.merchantName ?: "Unknown merchant",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        "${transaction.bankName ?: "Detected"} • $timeStr" + if (transaction.isCredit) " • Credit" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (transaction.isCredit) com.smartspend.ui.theme.ReceivedColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(8.dp))
                if (transaction.isAmountMissing) {
                    Text("Amount?", fontWeight = FontWeight.Bold, color = StatusWarning, fontSize = 13.sp)
                } else {
                    Text(
                        (if (transaction.isCredit) "+" else "") + formatCurrency(transaction.amount),
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.isCredit) com.smartspend.ui.theme.ReceivedColor else GivenColor,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!transaction.isAmountMissing) {
                    OutlinedButton(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GivenColor)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Confirm", style = MaterialTheme.typography.labelMedium)
                    }
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Discard", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun BillMiniRow(bill: Bill, modifier: Modifier = Modifier) {
    val status = bill.computeStatus()
    val color = when (status) {
        BillStatus.OVERDUE -> StatusDanger
        BillStatus.PENDING -> StatusWarning
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(modifier, shape = RoundedCornerShape(10.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(bill.type.emoji, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(bill.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(formatCurrency(bill.amount, compact = true), fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun ExpenseMiniRow(
    expense: Expense,
    category: Category?,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(expense.date))
    var menuExpanded by remember { mutableStateOf(false) }
    val hasCoordinates = expense.latitude != null && expense.longitude != null

    Card(
        modifier.clickable { onEdit() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EmojiAvatar(
                emoji = category?.emoji ?: "📌",
                colorHex = category?.colorHex ?: "#7F77DD",
                sizeDp = 40
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    expense.merchantName ?: "Expense",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))

                // Category badge shown directly on the card
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(
                        text = category?.name ?: "Uncategorized",
                        color = try {
                            Color(android.graphics.Color.parseColor(category?.colorHex ?: "#888780"))
                        } catch (e: Exception) { Color.Gray }
                    )
                    if (expense.entryType != EntryType.EXPENSE) {
                        Spacer(Modifier.width(6.dp))
                        StatusChip(
                            text = expense.entryType.displayName,
                            color = if (expense.entryType == EntryType.CREDIT) ReceivedColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (!expense.locationName.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = if (hasCoordinates) {
                            Modifier.clickable {
                                LocationHelper.openInMaps(
                                    context,
                                    expense.latitude!!,
                                    expense.longitude!!,
                                    expense.locationName ?: expense.merchantName
                                )
                            }
                        } else Modifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            modifier = Modifier.size(13.dp),
                            tint = if (hasCoordinates) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            expense.locationName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasCoordinates) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        if (hasCoordinates) {
                            Icon(
                                Icons.Default.ChevronRight,
                                null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            val amountPrefix = if (expense.entryType == EntryType.CREDIT) "+" else ""
            val amountColor = when (expense.entryType) {
                EntryType.CREDIT -> ReceivedColor
                EntryType.EXCLUDED -> MaterialTheme.colorScheme.onSurfaceVariant
                EntryType.EXPENSE -> GivenColor
            }
            Text("$amountPrefix${formatCurrency(expense.amount)}", fontWeight = FontWeight.Bold, color = amountColor, fontSize = 14.sp)

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp).padding(start = 2.dp)) {
                    Icon(Icons.Default.MoreVert, "More", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun DashboardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(140.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(Modifier.weight(1f).height(80.dp))
            SkeletonBlock(Modifier.weight(1f).height(80.dp))
        }
        repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(56.dp)) }
    }
}