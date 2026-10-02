package com.smartspend.data.db

import androidx.room.*
import com.smartspend.data.models.Expense
import com.smartspend.data.models.Income
import kotlinx.coroutines.flow.Flow

data class CategorySpendRow(val categoryId: Long, val total: Double)
data class DaySpendRow(val day: String, val total: Double)
data class MerchantSpendRow(val merchantName: String, val total: Double, val count: Int)
data class PaymentMethodSpendRow(val paymentMethod: com.smartspend.data.models.PaymentMethod, val total: Double)

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun getBetween(from: Long, to: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId AND date BETWEEN :from AND :to ORDER BY date DESC")
    fun getByCategoryBetween(categoryId: Long, from: Long, to: Long): Flow<List<Expense>>

    // Note: "spend"/total queries below are restricted to entryType = 'EXPENSE' so that
    // entries marked as Credit or Excluded (self-transfers, investments) never count towards
    // totals, budgets, or reports — they're still visible in getAll/getBetween/search though.

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE categoryId = :categoryId AND date BETWEEN :from AND :to AND entryType = 'EXPENSE'")
    fun getCategorySpendBetween(categoryId: Long, from: Long, to: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE date BETWEEN :from AND :to AND entryType = 'EXPENSE'")
    fun getTotalSpendBetween(from: Long, to: Long): Flow<Double>

    @Query("SELECT categoryId, COALESCE(SUM(amount),0) as total FROM expenses WHERE date BETWEEN :from AND :to AND entryType = 'EXPENSE' GROUP BY categoryId")
    fun getSpendByCategoryBetween(from: Long, to: Long): Flow<List<CategorySpendRow>>

    @Query("""
        SELECT strftime('%Y-%m-%d', date / 1000, 'unixepoch') as day, COALESCE(SUM(amount),0) as total
        FROM expenses WHERE date BETWEEN :from AND :to AND entryType = 'EXPENSE'
        GROUP BY day ORDER BY day ASC
    """)
    fun getDailySpendBetween(from: Long, to: Long): Flow<List<DaySpendRow>>

    @Query("SELECT * FROM expenses WHERE merchantName LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' ORDER BY date DESC LIMIT 100")
    fun search(query: String): Flow<List<Expense>>

    // Same as [search] but also matches against the linked category's name, so typing
    // e.g. "grocer" surfaces every expense filed under the "Groceries" category even if
    // the merchant/note text doesn't contain that word. Purely additive — does not
    // change the schema or affect [search].
    @Query("""
        SELECT expenses.* FROM expenses
        INNER JOIN categories ON categories.id = expenses.categoryId
        WHERE expenses.merchantName LIKE '%' || :query || '%'
           OR expenses.note LIKE '%' || :query || '%'
           OR categories.name LIKE '%' || :query || '%'
        ORDER BY expenses.date DESC LIMIT 200
    """)
    fun searchWithCategory(query: String): Flow<List<Expense>>

    // All expenses in a single category, no date restriction — used when the search
    // screen's category chip is selected without any typed text.
    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId ORDER BY date DESC LIMIT 200")
    fun getAllByCategory(categoryId: Long): Flow<List<Expense>>

    // Same as [searchWithCategory] but scoped to a date range — used when the search
    // screen is limiting results to the currently selected time window (e.g. "This Month").
    @Query("""
        SELECT expenses.* FROM expenses
        INNER JOIN categories ON categories.id = expenses.categoryId
        WHERE (expenses.merchantName LIKE '%' || :query || '%'
           OR expenses.note LIKE '%' || :query || '%'
           OR categories.name LIKE '%' || :query || '%')
          AND expenses.date BETWEEN :from AND :to
        ORDER BY expenses.date DESC LIMIT 200
    """)
    fun searchWithCategoryInRange(query: String, from: Long, to: Long): Flow<List<Expense>>

    @Query("SELECT MAX(amount) FROM expenses WHERE categoryId = :categoryId AND entryType = 'EXPENSE'")
    suspend fun getMaxExpenseForCategory(categoryId: Long): Double?

    @Query("SELECT AVG(amount) FROM expenses WHERE categoryId = :categoryId AND entryType = 'EXPENSE'")
    suspend fun getAvgExpenseForCategory(categoryId: Long): Double?

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId AND entryType = 'EXPENSE'")
    suspend fun getCountForCategory(categoryId: Long): Int

    // ── Reports/analytics — all scoped to a [from, to) range and to entryType = 'EXPENSE',
    // matching the existing "spend" query convention above. ────────────────────────────

    @Query("""
        SELECT COALESCE(merchantName, 'Unknown') as merchantName, COALESCE(SUM(amount),0) as total, COUNT(*) as count
        FROM expenses
        WHERE date BETWEEN :from AND :to AND entryType = 'EXPENSE'
        GROUP BY merchantName
        ORDER BY total DESC
        LIMIT 5
    """)
    fun getTopMerchantsBetween(from: Long, to: Long): Flow<List<MerchantSpendRow>>

    @Query("""
        SELECT paymentMethod, COALESCE(SUM(amount),0) as total
        FROM expenses
        WHERE date BETWEEN :from AND :to AND entryType = 'EXPENSE'
        GROUP BY paymentMethod
        ORDER BY total DESC
    """)
    fun getSpendByPaymentMethodBetween(from: Long, to: Long): Flow<List<PaymentMethodSpendRow>>

    @Query("SELECT COUNT(*) FROM expenses WHERE date BETWEEN :from AND :to AND entryType = 'EXPENSE'")
    fun getTransactionCountBetween(from: Long, to: Long): Flow<Int>

    @Query("SELECT COALESCE(AVG(amount), 0) FROM expenses WHERE date BETWEEN :from AND :to AND entryType = 'EXPENSE'")
    fun getAverageTransactionBetween(from: Long, to: Long): Flow<Double>

    @Query("SELECT * FROM expenses WHERE date BETWEEN :from AND :to AND entryType = 'EXPENSE' ORDER BY amount DESC LIMIT 1")
    fun getHighestTransactionBetween(from: Long, to: Long): Flow<Expense?>

    @Query("""
        SELECT * FROM expenses
        WHERE merchantName = :merchant AND amount = :amount AND date BETWEEN :from AND :to
    """)
    suspend fun findPotentialDuplicates(merchant: String, amount: Double, from: Long, to: Long): List<Expense>

    /** Used by the local backup feature to export every expense regardless of type/date. */
    @Query("SELECT * FROM expenses")
    suspend fun getAllOnce(): List<Expense>
}

@Dao
interface IncomeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(income: Income): Long

    @Update
    suspend fun update(income: Income)

    @Delete
    suspend fun delete(income: Income)

    @Query("SELECT * FROM incomes ORDER BY date DESC")
    fun getAll(): Flow<List<Income>>

    @Query("SELECT COALESCE(SUM(amount),0) FROM incomes WHERE date BETWEEN :from AND :to")
    fun getTotalBetween(from: Long, to: Long): Flow<Double>

    /** Used by the local backup feature to export every income record. */
    @Query("SELECT * FROM incomes")
    suspend fun getAllOnce(): List<Income>
}
