package com.smartspend.data.db

import androidx.room.*
import com.smartspend.data.models.Bill
import com.smartspend.data.models.BillPaymentHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: Bill): Long

    @Update
    suspend fun update(bill: Bill)

    @Delete
    suspend fun delete(bill: Bill)

    @Query("SELECT * FROM bills ORDER BY dueDate ASC")
    fun getAll(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE isPaid = 0 ORDER BY dueDate ASC")
    fun getUnpaid(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getById(id: Long): Bill?

    @Query("SELECT * FROM bills WHERE dueDate BETWEEN :from AND :to ORDER BY dueDate ASC")
    fun getDueBetween(from: Long, to: Long): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE isPaid = 0 AND dueDate < :now")
    suspend fun getOverdue(now: Long = System.currentTimeMillis()): List<Bill>

    @Query("SELECT sourceSmsBody FROM bills WHERE sourceSmsBody IS NOT NULL")
    suspend fun getAllSourceSmsBodies(): List<String>

    /** Used by the local backup feature to export every bill. */
    @Query("SELECT * FROM bills")
    suspend fun getAllOnce(): List<Bill>

    @Insert
    suspend fun insertPaymentHistory(history: BillPaymentHistory)

    @Query("SELECT * FROM bill_payment_history WHERE billId = :billId ORDER BY paidDate DESC")
    fun getPaymentHistory(billId: Long): Flow<List<BillPaymentHistory>>

    /** Used by the local backup feature to export every bill's payment history. */
    @Query("SELECT * FROM bill_payment_history")
    suspend fun getAllPaymentHistoryOnce(): List<BillPaymentHistory>
}
