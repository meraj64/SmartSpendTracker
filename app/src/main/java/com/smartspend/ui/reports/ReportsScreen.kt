package com.smartspend.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.data.db.DaySpendRow
import com.smartspend.data.db.MerchantSpendRow
import com.smartspend.data.db.PaymentMethodSpendRow
import com.smartspend.data.models.Category
import com.smartspend.data.models.Expense
import com.smartspend.data.models.PaymentMethod
import com.smartspend.data.repository.CategoryRepository
import com.smartspend.data.repository.ExpenseRepository
import com.smartspend.data.repository.IncomeRepository
import com.smartspend.ui.common.TimeFilterBar
import com.smartspend.ui.common.formatCurrency
import com.smartspend.ui.dashboard.TimeFilter
import com.smartspend.ui.theme.GivenColor
import com.smartspend.ui.theme.ReceivedColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ReportsState(
    val filter: TimeFilter = TimeFilter.currentMonth(),
    val categorySpend: List<Pair<Category?, Double>> = emptyList(),
    val dailySpend: List<DaySpendRow> = emptyList(),
    val totalSpend: Double = 0.0,
    val totalIncome: Double = 0.0,
    val previousTotalSpend: Double = 0.0,
    val topMerchants: List<MerchantSpendRow> = emptyList(),
    val paymentMethodSpend: List<PaymentMethodSpendRow> = emptyList(),
    val transactionCount: Int = 0,
    val averageTransaction: Double = 0.0,
    val highestTransaction: Expense? = null,
    val highestTransactionCategory: Category? = null,
    val isLoading: Boolean = true
) {
    val netSavings: Double get() = totalIncome - totalSpend

    /** % change in spend vs the previous equivalent period. Null when there's nothing
     *  meaningful to compare against (e.g. previous period had zero spend). */
    val spendChangePct: Double?
        get() = if (previousTotalSpend > 0) ((totalSpend - previousTotalSpend) / previousTotalSpend) * 100 else null

    /** Rough average spend per day across the selected range, for the "Daily avg" stat. */
    fun dailyAverage(): Double {
        val (start, end) = filter.toDateRange()
        val days = ((end - start) / (24L * 60 * 60 * 1000)).coerceAtLeast(1)
        return totalSpend / days
    }
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val incomeRepo: IncomeRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    private val _filter = MutableStateFlow<TimeFilter>(TimeFilter.currentMonth())
    val filter: StateFlow<TimeFilter> = _filter.asStateFlow()

    val state: StateFlow<ReportsState> = _filter.flatMapLatest { activeFilter ->
        val (start, end) = activeFilter.toDateRange()
        val (prevStart, prevEnd) = previousComparisonRange(activeFilter)

        combine(
            expenseRepo.getSpendByCategory(start, end),
            expenseRepo.getDailySpend(start, end),
            expenseRepo.getTotalSpend(start, end),
            categoryRepo.allCategories,
            incomeRepo.getTotalBetween(start, end),
            expenseRepo.getTotalSpend(prevStart, prevEnd),
            expenseRepo.getTopMerchants(start, end),
            expenseRepo.getSpendByPaymentMethod(start, end),
            expenseRepo.getTransactionCount(start, end),
            expenseRepo.getAverageTransaction(start, end),
            expenseRepo.getHighestTransaction(start, end)
        ) { arr ->
            @Suppress("UNCHECKED_CAST") val categorySpends = arr[0] as List<com.smartspend.data.db.CategorySpendRow>
            @Suppress("UNCHECKED_CAST") val daily = arr[1] as List<DaySpendRow>
            val total = arr[2] as Double
            @Suppress("UNCHECKED_CAST") val cats = arr[3] as List<Category>
            val income = arr[4] as Double
            val prevTotal = arr[5] as Double
            @Suppress("UNCHECKED_CAST") val topMerchants = arr[6] as List<MerchantSpendRow>
            @Suppress("UNCHECKED_CAST") val paymentMethods = arr[7] as List<PaymentMethodSpendRow>
            val txnCount = arr[8] as Int
            val avgTxn = arr[9] as Double
            val highest = arr[10] as Expense?

            val catMap = cats.associateBy { it.id }
            val withNames = categorySpends
                .sortedByDescending { it.total }
                .map { row -> catMap[row.categoryId] to row.total }

            ReportsState(
                filter = activeFilter,
                categorySpend = withNames,
                dailySpend = daily,
                totalSpend = total,
                totalIncome = income,
                previousTotalSpend = prevTotal,
                topMerchants = topMerchants,
                paymentMethodSpend = paymentMethods,
                transactionCount = txnCount,
                averageTransaction = avgTxn,
                highestTransaction = highest,
                highestTransactionCategory = highest?.categoryId?.let { catMap[it] },
                isLoading = false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsState())

    fun setFilter(newFilter: TimeFilter) { _filter.value = newFilter }

    /** For Month/Year filters this is just the previous month/year. For a custom Range,
     *  it's an equal-length window immediately before it — so a 7-day range always gets
     *  compared against the 7 days before it, not some arbitrary calendar period. */
    private fun previousComparisonRange(filter: TimeFilter): Pair<Long, Long> = when (filter) {
        is TimeFilter.Range -> {
            val duration = filter.end - filter.start
            Pair(filter.start - duration, filter.start)
        }
        else -> filter.previousPeriod().toDateRange()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Reports") }) }) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TimeFilterBar(filter = state.filter, onFilterChange = viewModel::setFilter)
            }

            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    TotalSummaryCard(state)
                }
            }

            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    QuickStatsGrid(state)
                }
            }

            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SectionCard("Daily Spending") {
                        if (state.dailySpend.isNotEmpty()) {
                            DailySpendBars(state.dailySpend)
                        } else {
                            EmptySectionText()
                        }
                    }
                }
            }

            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SectionCard("Spending by Category") {
                        if (state.categorySpend.isNotEmpty()) {
                            CategoryBreakdown(state.categorySpend)
                        } else {
                            EmptySectionText()
                        }
                    }
                }
            }

            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SectionCard("Top Merchants") {
                        if (state.topMerchants.isNotEmpty()) {
                            TopMerchantsList(state.topMerchants, state.totalSpend)
                        } else {
                            EmptySectionText()
                        }
                    }
                }
            }

            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SectionCard("Payment Methods") {
                        if (state.paymentMethodSpend.isNotEmpty()) {
                            PaymentMethodBreakdown(state.paymentMethodSpend, state.totalSpend)
                        } else {
                            EmptySectionText()
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun EmptySectionText() {
    Text(
        "No data yet",
        Modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TotalSummaryCard(state: ReportsState) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(state.filter.label(), style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatCurrency(state.totalSpend),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                state.spendChangePct?.let { pct ->
                    Spacer(Modifier.width(10.dp))
                    SpendChangeBadge(pct)
                }
            }
            Text(
                "total spent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryStat("Income", state.totalIncome, ReceivedColor)
                SummaryStat("Net", state.netSavings, if (state.netSavings >= 0) ReceivedColor else GivenColor)
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: Double, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatCurrency(value, compact = true), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Up/down/flat pill showing % change vs the previous equivalent period. More spend than
 *  before is flagged red (bad), less is green (good) — flipped from a typical "growth"
 *  badge since this is spending, not revenue. */
@Composable
private fun SpendChangeBadge(pct: Double) {
    val isUp = pct > 0.5
    val isDown = pct < -0.5
    val color = when {
        isUp -> GivenColor
        isDown -> ReceivedColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when {
        isUp -> Icons.Default.TrendingUp
        isDown -> Icons.Default.TrendingDown
        else -> Icons.Default.TrendingFlat
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        Text(
            "${if (pct > 0) "+" else ""}${"%.0f".format(pct)}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun QuickStatsGrid(state: ReportsState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Transactions", state.transactionCount.toString(), Modifier.weight(1f))
        StatTile("Avg / txn", formatCurrency(state.averageTransaction, compact = true), Modifier.weight(1f))
        StatTile("Daily avg", formatCurrency(state.dailyAverage(), compact = true), Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    state.highestTransaction?.let { txn ->
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(1.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Highest transaction", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${txn.merchantName ?: "Expense"}${state.highestTransactionCategory?.let { "  •  ${it.emoji} ${it.name}" } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(formatCurrency(txn.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GivenColor)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column {
            Text(
                title,
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

/**
 * Custom Canvas bar chart — no external chart library needed.
 * Draws a simple bar-per-day chart for the selected range.
 */
@Composable
private fun DailySpendBars(data: List<DaySpendRow>) {
    val maxVal = data.maxOfOrNull { it.total }?.takeIf { it > 0 } ?: 1.0
    val barColor = MaterialTheme.colorScheme.primary

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val barWidth = (size.width / data.size) * 0.6f
        val gap = (size.width / data.size) * 0.4f
        data.forEachIndexed { index, row ->
            val barHeight = (row.total / maxVal * size.height).toFloat()
            val x = index * (barWidth + gap)
            drawRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
            )
        }
    }

    // X-axis labels — show first, middle, last day
    if (data.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(data.first().day.takeLast(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(data[data.size / 2].day.takeLast(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(data.last().day.takeLast(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun CategoryBreakdown(data: List<Pair<Category?, Double>>) {
    val total = data.sumOf { it.second }.takeIf { it > 0 } ?: 1.0

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        data.forEach { (cat, amount) ->
            val pct = (amount / total).toFloat()
            val color = cat?.colorHex?.let {
                try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { Color.Gray }
            } ?: Color.Gray

            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cat?.emoji ?: "📌", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            cat?.name ?: "Other",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            formatCurrency(amount, compact = true),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = color,
                        trackColor = color.copy(alpha = 0.15f)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TopMerchantsList(data: List<MerchantSpendRow>, totalSpend: Double) {
    val denom = totalSpend.takeIf { it > 0 } ?: 1.0
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        data.forEachIndexed { index, row ->
            val pct = (row.total / denom * 100)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.merchantName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${row.count} transaction${if (row.count == 1) "" else "s"}  •  ${"%.0f".format(pct)}% of spend",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(formatCurrency(row.total, compact = true), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PaymentMethodBreakdown(data: List<PaymentMethodSpendRow>, totalSpend: Double) {
    val denom = totalSpend.takeIf { it > 0 } ?: 1.0
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        data.sortedByDescending { it.total }.forEach { row ->
            val pct = (row.total / denom).toFloat()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(paymentMethodEmoji(row.paymentMethod), fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(paymentMethodLabel(row.paymentMethod), style = MaterialTheme.typography.bodyMedium)
                        Text(formatCurrency(row.total, compact = true), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun paymentMethodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> "Cash"
    PaymentMethod.UPI -> "UPI"
    PaymentMethod.BANK -> "Bank Transfer"
    PaymentMethod.CARD -> "Card"
    PaymentMethod.PENDING -> "Pending"
}

private fun paymentMethodEmoji(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> "💵"
    PaymentMethod.UPI -> "📲"
    PaymentMethod.BANK -> "🏦"
    PaymentMethod.CARD -> "💳"
    PaymentMethod.PENDING -> "⏳"
}
