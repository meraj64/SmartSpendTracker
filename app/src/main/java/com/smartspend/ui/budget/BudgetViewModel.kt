package com.smartspend.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.data.models.Budget
import com.smartspend.data.models.BudgetProgress
import com.smartspend.data.models.Category
import com.smartspend.data.repository.BudgetRepository
import com.smartspend.data.repository.CategoryRepository
import com.smartspend.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetUiState(
    val selectedMonth: Int,
    val selectedYear: Int,
    val budgetProgressList: List<BudgetProgress> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val dailySpendTrend: List<Pair<String, Double>> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepo: BudgetRepository,
    private val categoryRepo: CategoryRepository,
    private val expenseRepo: ExpenseRepository
) : ViewModel() {

    private val currentMonthYear = Budget.currentMonth()
    private val selectedMonth = MutableStateFlow(currentMonthYear.first)
    private val selectedYear = MutableStateFlow(currentMonthYear.second)

    val uiState: StateFlow<BudgetUiState> = combine(
        selectedMonth, selectedYear, categoryRepo.activeCategories
    ) { month, year, categories -> Triple(month, year, categories) }
        .flatMapLatest { (month, year, categories) ->
            budgetRepo.getProgressForMonth(month, year).map { progressList ->
                BudgetUiState(
                    selectedMonth = month,
                    selectedYear = year,
                    budgetProgressList = progressList.sortedBy { it.budget.categoryId != null },
                    availableCategories = categories,
                    isLoading = false
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState(currentMonthYear.first, currentMonthYear.second))

    fun changeMonth(month: Int, year: Int) {
        selectedMonth.value = month
        selectedYear.value = year
    }

    fun createOrUpdateBudget(categoryId: Long?, amount: Double) {
        viewModelScope.launch {
            val existing = if (categoryId == null) {
                budgetRepo.getOverall(selectedMonth.value, selectedYear.value)
            } else {
                budgetRepo.getForCategory(categoryId, selectedMonth.value, selectedYear.value)
            }
            if (existing != null) {
                budgetRepo.update(existing.copy(amount = amount))
            } else {
                budgetRepo.insert(
                    Budget(
                        categoryId = categoryId,
                        amount = amount,
                        month = selectedMonth.value,
                        year = selectedYear.value
                    )
                )
            }
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch { budgetRepo.delete(budget) }
    }

    fun toggleNotifications(budget: Budget) {
        viewModelScope.launch {
            budgetRepo.update(budget.copy(notificationsEnabled = !budget.notificationsEnabled))
        }
    }
}
