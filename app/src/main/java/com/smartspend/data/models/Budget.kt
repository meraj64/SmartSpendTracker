package com.smartspend.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val categoryId: Long? = null,   // null = overall monthly budget
    val amount: Double,
    val month: Int,                  // 1-12
    val year: Int,
    val notificationsEnabled: Boolean = true,
    val alertsSent: String = "",     // comma separated thresholds already notified e.g. "80,90"
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun currentMonth(): Pair<Int, Int> {
            val cal = Calendar.getInstance()
            return Pair(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
        }
    }
}

/** Computed, not stored — built by combining Budget + sum of Expenses */
data class BudgetProgress(
    val budget: Budget,
    val category: Category?,         // null for overall budget
    val spent: Double,
    val daysRemainingInMonth: Int,
    val estimatedMonthEndSpend: Double
) {
    val remaining: Double get() = budget.amount - spent
    val percentUsed: Float get() = if (budget.amount <= 0) 0f else (spent / budget.amount * 100).toFloat().coerceAtLeast(0f)
    val isOverBudget: Boolean get() = spent > budget.amount

    enum class Status { SAFE, WARNING, DANGER, OVER }

    val status: Status get() = when {
        percentUsed >= 100f -> Status.OVER
        percentUsed >= 90f  -> Status.DANGER
        percentUsed >= 70f  -> Status.WARNING
        else -> Status.SAFE
    }
}
