package com.smartspend.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incomes")
data class Income(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val source: String,
    val note: String? = null,
    val date: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,
    val recurrenceFrequency: RecurrenceFrequency? = null
)
