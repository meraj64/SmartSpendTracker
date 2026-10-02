package com.smartspend.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.data.models.Bill
import com.smartspend.data.models.BudgetProgress
import com.smartspend.data.models.Category
import com.smartspend.data.models.Expense
import com.smartspend.data.models.Transaction
import com.smartspend.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** Which time window the dashboard's transaction list & summary card are showing. */
sealed class TimeFilter {
    data class Month(val month: Int, val year: Int) : TimeFilter()
    data class Year(val year: Int) : TimeFilter()
    data class Range(val start: Long, val end: Long) : TimeFilter()

    /** Converts this filter into a half-open [start, end) millis range for querying. */
    fun toDateRange(): Pair<Long, Long> = when (this) {
        is Month -> ExpenseRepository.monthRange(month, year)
        is Year -> ExpenseRepository.yearRange(year)
        is Range -> Pair(start, end)
    }

    fun nextPeriod(): TimeFilter = when (this) {
        is Month -> {
            val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
            cal.add(Calendar.MONTH, 1)
            Month(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
        }
        is Year -> Year(year + 1)
        is Range -> this
    }

    fun previousPeriod(): TimeFilter = when (this) {
        is Month -> {
            val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
            cal.add(Calendar.MONTH, -1)
            Month(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
        }
        is Year -> Year(year - 1)
        is Range -> this
    }

    /** "This Month", "July 2026", "This Year", "2024", or a "12 Jul – 18 Jul 2026" range.
     *  Shared with the search screen so a range carried over there reads the same way. */
    fun label(): String {
        val now = Calendar.getInstance()
        return when (this) {
            is Month -> {
                if (month == now.get(Calendar.MONTH) + 1 && year == now.get(Calendar.YEAR)) {
                    "This Month"
                } else {
                    val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
                    java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(cal.time)
                }
            }
            is Year -> {
                if (year == now.get(Calendar.YEAR)) "This Year" else year.toString()
            }
            is Range -> {
                val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                val inclusiveEnd = end - 1
                "${sdf.format(java.util.Date(start))} – ${sdf.format(java.util.Date(inclusiveEnd))}"
            }
        }
    }

    /** True if this period includes "today" — used to disable the forward-navigation arrow. */
    fun isCurrentOrFuture(): Boolean {
        val now = Calendar.getInstance()
        return when (this) {
            is Month -> year > now.get(Calendar.YEAR) ||
                (year == now.get(Calendar.YEAR) && month >= now.get(Calendar.MONTH) + 1)
            is Year -> year >= now.get(Calendar.YEAR)
            is Range -> true
        }
    }

    companion object {
        fun currentMonth(): Month {
            val cal = Calendar.getInstance()
            return Month(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
        }
    }
}

data class DashboardState(
    val monthlySpend: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val budgetProgressList: List<BudgetProgress> = emptyList(),
    val upcomingBills: List<Bill> = emptyList(),
    val recentExpenses: List<Expense> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val khataBalance: Double = 0.0,
    val filter: TimeFilter = TimeFilter.currentMonth(),
    // Transactions detected from SMS/notifications that haven't been confirmed, edited, or
    // discarded yet. Shown as "Action needed" cards at the top of the transaction list —
    // independent of the selected time filter, so a pending item never silently disappears
    // just because the user is looking at a different month.
    val pendingTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val incomeRepo: IncomeRepository,
    private val budgetRepo: BudgetRepository,
    private val billRepo: BillRepository,
    private val khataRepo: KhataRepository,
    private val categoryRepo: CategoryRepository,
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    private val _filter = MutableStateFlow<TimeFilter>(TimeFilter.currentMonth())
    val filter: StateFlow<TimeFilter> = _filter.asStateFlow()

    val state: StateFlow<DashboardState> = _filter.flatMapLatest { activeFilter ->
        val (start, end) = activeFilter.toDateRange()
        // Budget progress always reflects the *actual* current month — budgets are inherently
        // monthly, so they shouldn't shift just because the transaction list is showing a
        // different period.
        val cal = Calendar.getInstance()
        val actualMonth = cal.get(Calendar.MONTH) + 1
        val actualYear = cal.get(Calendar.YEAR)

        combine(
            expenseRepo.getTotalSpend(start, end),
            incomeRepo.getTotalBetween(start, end),
            budgetRepo.getProgressForMonth(actualMonth, actualYear),
            billRepo.unpaidBills,
            expenseRepo.getBetween(start, end),
            khataRepo.overallSummary,
            categoryRepo.allCategories,
            transactionRepo.pendingTransactions
        ) { spendArr ->
            val spend    = spendArr[0] as Double
            val income   = spendArr[1] as Double
            @Suppress("UNCHECKED_CAST")
            val budgets  = spendArr[2] as List<BudgetProgress>
            @Suppress("UNCHECKED_CAST")
            val bills    = spendArr[3] as List<Bill>
            @Suppress("UNCHECKED_CAST")
            val expenses = spendArr[4] as List<Expense>
            val khata    = spendArr[5] as com.smartspend.data.models.KhataOverallSummary
            @Suppress("UNCHECKED_CAST")
            val categories = spendArr[6] as List<Category>
            @Suppress("UNCHECKED_CAST")
            val pending = spendArr[7] as List<Transaction>

            DashboardState(
                monthlySpend        = spend,
                monthlyIncome       = income,
                budgetProgressList  = budgets.take(3),
                upcomingBills       = bills.sortedBy { it.dueDate }.take(5),
                recentExpenses      = expenses.sortedByDescending { it.date },
                categoriesById      = categories.associateBy { it.id },
                khataBalance        = khata.outstandingBalance,
                filter              = activeFilter,
                pendingTransactions = pending.sortedByDescending { it.detectedTime },
                isLoading           = false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    fun setFilter(newFilter: TimeFilter) {
        _filter.value = newFilter
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { expenseRepo.delete(expense) }
    }

    /** Confirms a detected transaction "as-is" straight from its Action-needed card. */
    fun confirmTransaction(transaction: Transaction) {
        viewModelScope.launch { transactionRepo.confirmAndCreateExpense(transaction.id) }
    }

    /** Discards a detected transaction straight from its Action-needed card. */
    fun discardTransaction(transaction: Transaction) {
        viewModelScope.launch { transactionRepo.dismissTransaction(transaction.id) }
    }
}
