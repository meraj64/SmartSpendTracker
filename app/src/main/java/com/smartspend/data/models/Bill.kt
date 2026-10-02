package com.smartspend.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class BillType(val displayName: String, val emoji: String) {
    CREDIT_CARD("Credit Card", "💳"),
    ELECTRICITY("Electricity", "💡"),
    WATER("Water", "🚰"),
    INTERNET("Internet", "🌐"),
    RENT("Rent", "🏠"),
    INSURANCE("Insurance", "🛡️"),
    EMI("EMI", "🏦"),
    SUBSCRIPTION("Subscription", "📺"),
    SCHOOL_FEES("School Fees", "🎒"),
    LOAN("Loan", "💰"),
    MOBILE_RECHARGE("Mobile Recharge", "📱"),
    CUSTOM("Custom", "📌")
}

enum class RecurrenceFrequency { MONTHLY, WEEKLY, QUARTERLY, YEARLY, CUSTOM, NONE }

enum class BillStatus { PENDING, PAID, OVERDUE, UPCOMING }

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,
    val type: BillType = BillType.CUSTOM,
    val amount: Double,
    val dueDate: Long,
    val repeatFrequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val customRepeatDays: Int? = null,   // used when repeatFrequency == CUSTOM

    val isPaid: Boolean = false,
    val paidDate: Long? = null,
    val categoryId: Long? = null,        // optional link to a spending category

    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),

    // The exact SMS text that created this bill via "Sync from SMS", used to avoid
    // creating duplicate bills on repeated syncs. Null for manually-added bills.
    val sourceSmsBody: String? = null
) {
    /** Status is computed dynamically, not stored, since "today" changes */
    fun computeStatus(now: Long = System.currentTimeMillis()): BillStatus {
        if (isPaid) return BillStatus.PAID
        val daysUntilDue = TimeUnit.MILLISECONDS.toDays(dueDate - now)
        return when {
            daysUntilDue < 0  -> BillStatus.OVERDUE
            daysUntilDue == 0L -> BillStatus.PENDING   // due today
            else -> BillStatus.UPCOMING
        }
    }

    fun daysRemaining(now: Long = System.currentTimeMillis()): Long =
        TimeUnit.MILLISECONDS.toDays(dueDate - now)

    /** Computes the next due date after marking this bill paid, for recurring bills */
    fun nextDueDate(): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dueDate }
        when (repeatFrequency) {
            RecurrenceFrequency.MONTHLY   -> cal.add(Calendar.MONTH, 1)
            RecurrenceFrequency.WEEKLY    -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RecurrenceFrequency.QUARTERLY -> cal.add(Calendar.MONTH, 3)
            RecurrenceFrequency.YEARLY    -> cal.add(Calendar.YEAR, 1)
            RecurrenceFrequency.CUSTOM    -> cal.add(Calendar.DAY_OF_MONTH, customRepeatDays ?: 30)
            RecurrenceFrequency.NONE      -> {}
        }
        return cal.timeInMillis
    }

    val isRecurring: Boolean get() = repeatFrequency != RecurrenceFrequency.NONE
}

/** History record kept when a recurring bill is marked paid (so we don't lose history) */
@Entity(tableName = "bill_payment_history")
data class BillPaymentHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billId: Long,
    val amountPaid: Double,
    val paidDate: Long = System.currentTimeMillis(),
    val dueDateAtPayment: Long
)
