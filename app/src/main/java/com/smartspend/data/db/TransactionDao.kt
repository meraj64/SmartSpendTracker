package com.smartspend.data.db

import androidx.room.*
import com.smartspend.data.models.Transaction
import com.smartspend.data.models.TransactionCategory
import kotlinx.coroutines.flow.Flow

data class CategorySpend(
    val category: TransactionCategory,
    val total: Double
)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY transactionTime DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE isPending = 1 ORDER BY detectedTime DESC")
    fun getPendingTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    /**
     * Looks for an already-pending transaction with the same amount detected within
     * [from, to] — used to skip inserting (and notifying about) a second pending row when
     * the same real-world payment is detected twice, e.g. once from the bank SMS and again
     * from a UPI app's notification a few seconds later. Amount is compared with a small
     * epsilon since it's stored as a Double.
     */
    @Query("""
        SELECT * FROM transactions
        WHERE isPending = 1
          AND ABS(amount - :amount) < 0.005
          AND detectedTime BETWEEN :from AND :to
        LIMIT 1
    """)
    suspend fun findPendingDuplicate(amount: Double, from: Long, to: Long): Transaction?

    // Change this in TransactionDao.kt
    @Query("SELECT * FROM transactions WHERE (isConfirmed = 1 OR isPending = 1) ORDER BY transactionTime DESC LIMIT :limit")
    fun getRecentConfirmed(limit: Int = 50): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE isConfirmed = 1 AND transactionTime >= :from")
    fun getTotalSpendSince(from: Long): Flow<Double?>

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE isConfirmed = 1 AND transactionTime >= :from GROUP BY category ORDER BY total DESC")
    fun getSpendByCategory(from: Long): Flow<List<CategorySpend>>

    @Query("DELETE FROM transactions WHERE isPending = 1 AND detectedTime < :olderThan")
    suspend fun deleteOldPending(olderThan: Long)

    @Query("UPDATE transactions SET isConfirmed = 1, isPending = 0 WHERE id = :id")
    suspend fun confirmTransaction(id: Long)

    @Query("UPDATE transactions SET isPending = 0, isConfirmed = 0 WHERE id = :id")
    suspend fun dismissTransaction(id: Long)

    /** Used by the SMS range-scan feature to avoid re-importing a message it already recorded. */
    @Query("SELECT rawSmsBody FROM transactions WHERE rawSmsBody IS NOT NULL")
    suspend fun getAllRawSmsBodies(): List<String>

    /** Used by the local backup feature to export every detected transaction. */
    @Query("SELECT * FROM transactions")
    suspend fun getAllOnce(): List<Transaction>
}
