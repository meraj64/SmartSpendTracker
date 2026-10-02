package com.smartspend.data.repository

import com.smartspend.data.db.BillDao
import com.smartspend.data.models.Bill
import com.smartspend.data.models.BillPaymentHistory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val billDao: BillDao
) {
    val allBills: Flow<List<Bill>> = billDao.getAll()
    val unpaidBills: Flow<List<Bill>> = billDao.getUnpaid()

    suspend fun insert(bill: Bill): Long = billDao.insert(bill)
    suspend fun update(bill: Bill) = billDao.update(bill)
    suspend fun delete(bill: Bill) = billDao.delete(bill)
    suspend fun getById(id: Long) = billDao.getById(id)
    fun getDueBetween(from: Long, to: Long) = billDao.getDueBetween(from, to)
    suspend fun getOverdue() = billDao.getOverdue()

    fun getPaymentHistory(billId: Long) = billDao.getPaymentHistory(billId)

    /** Raw SMS bodies of bills already created via "Sync from SMS", used to skip duplicates on re-scan. */
    suspend fun existingSourceSmsBodies(): Set<String> = billDao.getAllSourceSmsBodies().toSet()

    /**
     * Marks a bill paid. For recurring bills, logs payment history and
     * rolls the due date forward instead of deleting the bill.
     */
    suspend fun markPaid(bill: Bill) {
        billDao.insertPaymentHistory(
            BillPaymentHistory(
                billId = bill.id,
                amountPaid = bill.amount,
                dueDateAtPayment = bill.dueDate
            )
        )

        if (bill.isRecurring) {
            // Roll forward — keep the recurring bill alive with a new due date
            billDao.update(
                bill.copy(
                    dueDate = bill.nextDueDate(),
                    isPaid = false,
                    paidDate = null
                )
            )
        } else {
            billDao.update(bill.copy(isPaid = true, paidDate = System.currentTimeMillis()))
        }
    }
}
