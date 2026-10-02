package com.smartspend.data.models

import java.io.Serializable

/** Holds data extracted from a "bill due" SMS (electricity, credit card statement, EMI, etc.) —
 *  distinct from [ParsedTransactionData], which is for completed debit transactions. */
data class ParsedBillData(
    val amount: Double,
    val dueDate: Long,
    val type: BillType,
    val title: String,
    val sender: String,
    val rawBody: String
) : Serializable
