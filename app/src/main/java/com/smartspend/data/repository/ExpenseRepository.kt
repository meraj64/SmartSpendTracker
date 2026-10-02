package com.smartspend.data.repository

import com.smartspend.data.db.ExpenseDao
import com.smartspend.data.db.IncomeDao
import com.smartspend.data.models.Expense
import com.smartspend.data.models.Income
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {
    val allExpenses: Flow<List<Expense>> = expenseDao.getAll()

    suspend fun insert(expense: Expense): Long = expenseDao.insert(expense)
    suspend fun update(expense: Expense) = expenseDao.update(expense)
    suspend fun delete(expense: Expense) = expenseDao.delete(expense)
    suspend fun getById(id: Long): Expense? = expenseDao.getById(id)

    fun getBetween(from: Long, to: Long): Flow<List<Expense>> = expenseDao.getBetween(from, to)

    fun getCategorySpend(categoryId: Long, from: Long, to: Long): Flow<Double> =
        expenseDao.getCategorySpendBetween(categoryId, from, to)

    fun getTotalSpend(from: Long, to: Long): Flow<Double> = expenseDao.getTotalSpendBetween(from, to)

    fun getSpendByCategory(from: Long, to: Long) = expenseDao.getSpendByCategoryBetween(from, to)

    fun getDailySpend(from: Long, to: Long) = expenseDao.getDailySpendBetween(from, to)

    fun getTopMerchants(from: Long, to: Long) = expenseDao.getTopMerchantsBetween(from, to)

    fun getSpendByPaymentMethod(from: Long, to: Long) = expenseDao.getSpendByPaymentMethodBetween(from, to)

    fun getTransactionCount(from: Long, to: Long): Flow<Int> = expenseDao.getTransactionCountBetween(from, to)

    fun getAverageTransaction(from: Long, to: Long): Flow<Double> = expenseDao.getAverageTransactionBetween(from, to)

    fun getHighestTransaction(from: Long, to: Long): Flow<Expense?> = expenseDao.getHighestTransactionBetween(from, to)

    fun search(query: String): Flow<List<Expense>> = expenseDao.search(query)

    /** Same as [search] but also matches the linked category's name. */
    fun searchWithCategory(query: String): Flow<List<Expense>> = expenseDao.searchWithCategory(query)

    /** All expenses filed under a category, regardless of date. */
    fun getAllByCategory(categoryId: Long): Flow<List<Expense>> = expenseDao.getAllByCategory(categoryId)

    /** Same as [searchWithCategory] but scoped to [from, to). */
    fun searchWithCategoryInRange(query: String, from: Long, to: Long): Flow<List<Expense>> =
        expenseDao.searchWithCategoryInRange(query, from, to)

    /** All expenses in a category within [from, to) — reuses the existing date-scoped query. */
    fun getByCategoryInRange(categoryId: Long, from: Long, to: Long): Flow<List<Expense>> =
        expenseDao.getByCategoryBetween(categoryId, from, to)

    suspend fun getMaxForCategory(categoryId: Long) = expenseDao.getMaxExpenseForCategory(categoryId) ?: 0.0
    suspend fun getAvgForCategory(categoryId: Long) = expenseDao.getAvgExpenseForCategory(categoryId) ?: 0.0
    suspend fun getCountForCategory(categoryId: Long) = expenseDao.getCountForCategory(categoryId)

    /** Checks if a near-identical expense already exists within the same day */
    suspend fun checkDuplicate(merchant: String, amount: Double, date: Long): Boolean {
        val dayStart = Calendar.getInstance().apply {
            timeInMillis = date
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000
        return expenseDao.findPotentialDuplicates(merchant, amount, dayStart, dayEnd).isNotEmpty()
    }

    companion object {
        fun monthRange(month: Int, year: Int): Pair<Long, Long> {
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            val end = cal.timeInMillis
            return Pair(start, end)
        }

        fun currentMonthRange(): Pair<Long, Long> {
            val cal = Calendar.getInstance()
            return monthRange(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
        }

        fun yearRange(year: Int): Pair<Long, Long> {
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.YEAR, 1)
            val end = cal.timeInMillis
            return Pair(start, end)
        }

        fun currentYearRange(): Pair<Long, Long> = yearRange(Calendar.getInstance().get(Calendar.YEAR))

        /** Normalizes an arbitrary [from, to] pick (e.g. from a date picker) into a
         *  half-open range covering the full day for both ends: [start-of-day(from), start-of-day(to) + 1 day). */
        fun dayInclusiveRange(fromMillis: Long, toMillis: Long): Pair<Long, Long> {
            val startCal = Calendar.getInstance().apply {
                timeInMillis = minOf(fromMillis, toMillis)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                timeInMillis = maxOf(fromMillis, toMillis)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_MONTH, 1)
            }
            return Pair(startCal.timeInMillis, endCal.timeInMillis)
        }
    }
}

@Singleton
class IncomeRepository @Inject constructor(
    private val incomeDao: IncomeDao
) {
    val allIncomes: Flow<List<Income>> = incomeDao.getAll()

    suspend fun insert(income: Income): Long = incomeDao.insert(income)
    suspend fun update(income: Income) = incomeDao.update(income)
    suspend fun delete(income: Income) = incomeDao.delete(income)
    fun getTotalBetween(from: Long, to: Long): Flow<Double> = incomeDao.getTotalBetween(from, to)
}
