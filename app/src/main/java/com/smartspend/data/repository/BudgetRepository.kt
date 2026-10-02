package com.smartspend.data.repository

import com.smartspend.data.db.BudgetDao
import com.smartspend.data.db.CategoryDao
import com.smartspend.data.db.ExpenseDao
import com.smartspend.data.models.Budget
import com.smartspend.data.models.BudgetProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao
) {
    suspend fun insert(budget: Budget): Long = budgetDao.insert(budget)
    suspend fun update(budget: Budget) = budgetDao.update(budget)
    suspend fun delete(budget: Budget) = budgetDao.delete(budget)
    suspend fun getOverall(month: Int, year: Int) = budgetDao.getOverallBudget(month, year)
    suspend fun getForCategory(categoryId: Long, month: Int, year: Int) =
        budgetDao.getCategoryBudget(categoryId, month, year)

    /** All budgets for the month, joined with live spend + computed progress */
    fun getProgressForMonth(month: Int, year: Int): Flow<List<BudgetProgress>> {
        val (start, end) = ExpenseRepository.monthRange(month, year)
        val today = Calendar.getInstance()
        val daysInMonth = today.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = if (today.get(Calendar.MONTH) + 1 == month && today.get(Calendar.YEAR) == year) {
            today.get(Calendar.DAY_OF_MONTH)
        } else daysInMonth
        val daysRemaining = (daysInMonth - currentDay).coerceAtLeast(0)

        return budgetDao.getForMonth(month, year).combine(
            expenseDao.getSpendByCategoryBetween(start, end)
        ) { budgets, categorySpends ->
            val totalSpend = categorySpends.sumOf { it.total }
            budgets.map { budget ->
                val spent = if (budget.categoryId == null) {
                    totalSpend
                } else {
                    categorySpends.firstOrNull { it.categoryId == budget.categoryId }?.total ?: 0.0
                }
                val category = budget.categoryId?.let { categoryDao.getById(it) }

                // Estimated month-end spend: linear projection based on days elapsed
                val dailyAvg = if (currentDay > 0) spent / currentDay else 0.0
                val estimated = dailyAvg * daysInMonth

                BudgetProgress(
                    budget = budget,
                    category = category,
                    spent = spent,
                    daysRemainingInMonth = daysRemaining,
                    estimatedMonthEndSpend = estimated
                )
            }
        }
    }

    suspend fun markAlertSent(budgetId: Long, threshold: Int) {
        val budget = budgetDao.getById(budgetId) ?: return
        val sent = budget.alertsSent.split(",").filter { it.isNotBlank() }.toMutableSet()
        sent.add(threshold.toString())
        budgetDao.updateAlertsSent(budgetId, sent.joinToString(","))
    }

    suspend fun wasAlertSent(budget: Budget, threshold: Int): Boolean =
        budget.alertsSent.split(",").contains(threshold.toString())
}
