package com.smartspend.data.repository

import com.smartspend.data.db.CategoryDao
import com.smartspend.data.db.ExpenseDao
import com.smartspend.data.db.TransactionDao
import com.smartspend.data.db.CategorySpend
import com.smartspend.data.models.Expense
import com.smartspend.data.models.PaymentMethod
import com.smartspend.data.models.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) {
    val allTransactions: Flow<List<Transaction>>     = dao.getAllTransactions()
    val pendingTransactions: Flow<List<Transaction>>  = dao.getPendingTransactions()
    val recentConfirmed: Flow<List<Transaction>>      = dao.getRecentConfirmed(50)

    fun getMonthlyTotal(): Flow<Double?> {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        return dao.getTotalSpendSince(start)
    }

    fun getMonthlyCategoryBreakdown(): Flow<List<CategorySpend>> {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        return dao.getSpendByCategory(start)
    }

    suspend fun update(transaction: Transaction) = dao.update(transaction)
    suspend fun delete(transaction: Transaction) = dao.delete(transaction)
    suspend fun confirmTransaction(id: Long)     = dao.confirmTransaction(id)
    suspend fun dismissTransaction(id: Long)     = dao.dismissTransaction(id)
    suspend fun getById(id: Long): Transaction?  = dao.getById(id)

    /**
     * Confirms a pending transaction "as-is" and records the matching Expense — this is what
     * powers both the notification's "Yes, Confirm" button and the "Confirm" quick action on
     * the dashboard's "Action needed" card, so the two behave identically. Skips creating the
     * Expense (but still marks the transaction confirmed) if a near-identical expense already
     * exists for that merchant/amount/day, to avoid double-recording.
     *
     * Returns true if a new Expense was created, false if it was skipped as a duplicate or the
     * transaction couldn't be found.
     */
    suspend fun confirmAndCreateExpense(transactionId: Long): Boolean {
        val textTx = dao.getById(transactionId) ?: return false
        dao.confirmTransaction(transactionId)

        val activeCategoriesList = categoryDao.getActiveCategories().first()
        val matchedCat = activeCategoriesList.firstOrNull { it.name == textTx.category.displayName }

        val cal = Calendar.getInstance().apply {
            timeInMillis = textTx.transactionTime
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000

        val duplicates = expenseDao.findPotentialDuplicates(textTx.merchantName ?: "", textTx.amount, dayStart, dayEnd)
        if (duplicates.isNotEmpty()) return false

        expenseDao.insert(
            Expense(
                amount = textTx.amount,
                categoryId = matchedCat?.id ?: 1L,
                merchantName = textTx.merchantName ?: "Confirmed Transaction",
                paymentMethod = PaymentMethod.UPI,
                date = textTx.transactionTime,
                // The "Yes, Confirm" quick action doesn't show the entry-type picker, so we
                // carry over whatever the SMS/notification itself indicated — a message that
                // said "credited"/"received" becomes a Credit entry, not a normal expense,
                // so it won't inflate spend totals. The user can still change this later from
                // the edit screen.
                entryType = if (textTx.isCredit) com.smartspend.data.models.EntryType.CREDIT else com.smartspend.data.models.EntryType.EXPENSE,
                isAutoDetected = true,
                rawSmsBody = textTx.rawSmsBody,
                locationName = textTx.merchantAddress,
                latitude = textTx.latitude,
                longitude = textTx.longitude
            )
        )
        return true
    }

    // ── SMS range-scan import ───────────────────────────────────────────────

    /** Raw SMS bodies already recorded as a Transaction, used to skip re-importing on re-scan. */
    suspend fun existingRawSmsBodies(): Set<String> = dao.getAllRawSmsBodies().toSet()

    /**
     * Records a transaction found by the range-scan into the pending queue — same as a
     * real-time SMS/notification detection — instead of auto-confirming it straight into the
     * Expense table. Bulk imports were previously written directly as confirmed expenses,
     * which meant a wrong category, misread merchant name, or a credit mistaken for a debit
     * had already counted towards totals before the user ever saw it. Now every scanned
     * transaction shows up under "Action needed" for a quick review/edit/confirm, exactly
     * like transactions detected live.
     *
     * Returns false if a matching pending/confirmed transaction already exists for that
     * merchant/amount/day (the raw-SMS-body check in the scan loop itself catches exact
     * re-scans; this catches the same real-world payment showing up with slightly different
     * SMS wording).
     */
    suspend fun importScannedTransaction(transaction: Transaction): Boolean {
        val dayStart = Calendar.getInstance().apply {
            timeInMillis = transaction.transactionTime
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000
        val merchant = transaction.merchantName ?: "SMS Expense"

        val alreadyExists = expenseDao.findPotentialDuplicates(merchant, transaction.amount, dayStart, dayEnd).isNotEmpty()
        if (alreadyExists) return false

        val pending = transaction.copy(isPending = true, isConfirmed = false)
        dao.insert(pending)
        return true
    }
}
