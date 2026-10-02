package com.smartspend.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PaymentMethod { CASH, UPI, BANK, CARD, PENDING }

/**
 * How an entry should be treated when totals, budgets, and reports are calculated.
 * - EXPENSE: a normal outgoing spend — counts towards totals as usual.
 * - CREDIT: money coming in (refund, cashback, reimbursement) — tracked but excluded
 *   from spend totals since it isn't a cost.
 * - EXCLUDED: recorded for reference only (e.g. self-transfer between your own accounts,
 *   or money moved into an investment) but should never be counted as spending.
 */
enum class EntryType(val displayName: String) {
    EXPENSE("Expense"),
    CREDIT("Credit"),
    EXCLUDED("Excluded")
}

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val amount: Double,
    val categoryId: Long,
    val merchantName: String? = null,
    val note: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val date: Long = System.currentTimeMillis(),

    // Marks this entry as a credit (money in) or excluded (self-transfer/investment) so it's
    // kept in history/search but never counted towards spend totals, budgets, or reports.
    // Defaults to EXPENSE so all existing behavior is unchanged.
    val entryType: EntryType = EntryType.EXPENSE,

    // Linked from auto-detection module (nullable — manual entries won't have these)
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isAutoDetected: Boolean = false,
    val rawSmsBody: String? = null,     // the exact SMS/notification text that created this
    val locationName: String? = null,   // resolved via Places API at detection time

    // Tags for filtering/search
    val tags: String? = null,   // comma separated

    val createdAt: Long = System.currentTimeMillis()
)
