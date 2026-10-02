package com.smartspend.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.data.models.Category
import com.smartspend.data.repository.CategoryRepository
import com.smartspend.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryStats(
    val category: Category,
    val totalExpense: Double,
    val monthlySpend: Double,
    val highestExpense: Double,
    val avgTransaction: Double,
    val transactionCount: Int
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepo: CategoryRepository,
    private val expenseRepo: ExpenseRepository
) : ViewModel() {

    val categories = categoryRepo.activeCategories.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    init {
        viewModelScope.launch { categoryRepo.seedDefaultsIfEmpty() }
    }

    fun addCategory(name: String, emoji: String, colorHex: String, budget: Double?, notes: String?) {
        viewModelScope.launch {
            categoryRepo.insert(
                Category(
                    name = name, emoji = emoji, colorHex = colorHex,
                    monthlyBudget = budget, notes = notes, isCustom = true
                )
            )
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { categoryRepo.update(category) }
    }

    fun archiveCategory(id: Long) {
        viewModelScope.launch { categoryRepo.archive(id) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { categoryRepo.delete(category) }
    }

    fun mergeCategories(sourceId: Long, targetId: Long) {
        viewModelScope.launch { categoryRepo.merge(sourceId, targetId) }
    }

    suspend fun getStats(category: Category): CategoryStats {
        val (start, end) = ExpenseRepository.currentMonthRange()
        val monthlySpend = expenseRepo.getCategorySpend(category.id, start, end)
        val highest = expenseRepo.getMaxForCategory(category.id)
        val avg = expenseRepo.getAvgForCategory(category.id)
        val count = expenseRepo.getCountForCategory(category.id)
        return CategoryStats(
            category = category,
            totalExpense = 0.0, // filled by caller via flow if needed
            monthlySpend = 0.0,
            highestExpense = highest,
            avgTransaction = avg,
            transactionCount = count
        )
    }
}
