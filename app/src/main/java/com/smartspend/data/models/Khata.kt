package com.smartspend.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "khata_persons")
data class KhataPerson(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val avatarColorHex: String = "#7F77DD",
    val createdAt: Long = System.currentTimeMillis()
)

enum class KhataEntryType(val displayName: String) {
    MONEY_GIVEN("Money Given"),       // you gave money to them (they owe you)
    MONEY_RECEIVED("Money Received"), // you received money from them (you owe less / settled)
    CASH_EXPENSE("Cash Expense"),
    CASH_INCOME("Cash Income")
}

enum class KhataEntryStatus { PENDING, SETTLED }

@Entity(tableName = "khata_entries")
data class KhataEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val personId: Long? = null,       // null for pure cash expense/income not tied to a person
    val type: KhataEntryType,
    val amount: Double,
    val description: String? = null,
    val categoryId: Long? = null,
    val date: Long = System.currentTimeMillis(),
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val status: KhataEntryStatus = KhataEntryStatus.PENDING,
    val attachmentUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Computed summary per person — not stored */
data class KhataPersonSummary(
    val person: KhataPerson,
    val totalGiven: Double,
    val totalReceived: Double,
    val lastEntryDate: Long?
) {
    /** Positive = they owe you. Negative = you owe them. */
    val balance: Double get() = totalGiven - totalReceived
    val isOutstanding: Boolean get() = balance != 0.0
}

/** Overall khata summary across all persons — not stored */
data class KhataOverallSummary(
    val totalGiven: Double,
    val totalReceived: Double,
    val totalCashExpense: Double,
    val totalCashIncome: Double
) {
    val outstandingBalance: Double get() = totalGiven - totalReceived
}
