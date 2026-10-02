package com.smartspend.data.models

import java.io.Serializable

/** Holds data extracted from a bank SMS or notification */
data class ParsedTransactionData(
    val amount: Double,
    val bankName: String?,
    val referenceNumber: String?,
    val merchantHint: String?,   // partial merchant name from SMS e.g. "APOLLO PHARM"
    val rawBody: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isAmountMissing: Boolean = false,   // true for wallet notifications that never state an amount
    val isCredit: Boolean = false           // true for "credited"/"received" style messages (money in)
) : Serializable
