package com.smartspend.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionCategory(val displayName: String, val emoji: String) {
    FOOD_DINING("Food & Dining", "🍽️"),
    GROCERIES("Groceries", "🛒"),
    SHOPPING("Shopping", "🛍️"),
    MEDICAL("Medical", "💊"),
    FUEL("Fuel", "⛽"),
    TRANSPORT("Transport", "🚗"),
    ENTERTAINMENT("Entertainment", "🎬"),
    UTILITIES("Utilities", "💡"),
    EDUCATION("Education", "📚"),
    TRAVEL("Travel", "✈️"),
    ATM("ATM / Cash", "💵"),
    TRANSFER("Transfer", "🔄"),
    OTHER("Other", "📌");

    companion object {
        /** Map Google Places types → our categories */
        fun fromPlaceType(types: List<String>): TransactionCategory {
            return when {
                types.any { it in listOf("restaurant","food","cafe","bakery","bar","meal_takeaway","meal_delivery") } -> FOOD_DINING
                types.any { it in listOf("grocery_or_supermarket","supermarket") } -> GROCERIES
                types.any { it in listOf("pharmacy","hospital","doctor","dentist","health","drugstore") } -> MEDICAL
                types.any { it in listOf("gas_station","fuel") } -> FUEL
                types.any { it in listOf("shopping_mall","clothing_store","electronics_store","store","department_store") } -> SHOPPING
                types.any { it in listOf("transit_station","subway_station","bus_station","taxi_stand","airport") } -> TRANSPORT
                types.any { it in listOf("movie_theater","amusement_park","bowling_alley","casino","night_club") } -> ENTERTAINMENT
                types.any { it in listOf("school","university","library") } -> EDUCATION
                types.any { it in listOf("lodging","hotel") } -> TRAVEL
                types.any { it in listOf("atm") } -> ATM
                else -> OTHER
            }
        }
    }
}

enum class TransactionSource { SMS, NOTIFICATION, MANUAL }

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val amount: Double,
    val currency: String = "INR",

    // Merchant info
    val merchantName: String?,
    val merchantPlaceId: String?,   // Google Places ID for future lookups
    val merchantAddress: String?,

    // Category
    val category: TransactionCategory = TransactionCategory.OTHER,

    // Location at time of transaction
    val latitude: Double?,
    val longitude: Double?,

    // Timestamps
    val transactionTime: Long = System.currentTimeMillis(),
    val detectedTime: Long = System.currentTimeMillis(),

    // Raw data
    val rawSmsBody: String?,
    val bankName: String?,
    val referenceNumber: String?,

    // State
    val source: TransactionSource = TransactionSource.SMS,
    val isConfirmed: Boolean = false,
    val isPending: Boolean = true,   // waiting for user confirm
    val isAmountMissing: Boolean = false,   // true for wallet notifications that never stated an amount
    val isCredit: Boolean = false,   // true when the SMS/notification said "credited"/"received" (money in)
    val note: String? = null
)
