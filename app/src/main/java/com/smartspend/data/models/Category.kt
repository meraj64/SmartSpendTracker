package com.smartspend.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val emoji: String = "📌",
    val colorHex: String = "#7F77DD",      // c-purple 400 default
    val monthlyBudget: Double? = null,      // null = no budget set
    val notes: String? = null,
    val isCustom: Boolean = true,
    val isArchived: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/** Seed categories created on first app launch */
object DefaultCategories {
    val seed = listOf(
        Category(name = "Food & Dining", emoji = "🍽️", colorHex = "#D85A30", isCustom = false),
        Category(name = "Groceries",     emoji = "🛒", colorHex = "#639922", isCustom = false),
        Category(name = "Shopping",      emoji = "🛍️", colorHex = "#D4537E", isCustom = false),
        Category(name = "Medical",       emoji = "💊", colorHex = "#E24B4A", isCustom = false),
        Category(name = "Fuel",          emoji = "⛽", colorHex = "#BA7517", isCustom = false),
        Category(name = "Transport",     emoji = "🚗", colorHex = "#378ADD", isCustom = false),
        Category(name = "Entertainment", emoji = "🎬", colorHex = "#7F77DD", isCustom = false),
        Category(name = "Utilities",     emoji = "💡", colorHex = "#EF9F27", isCustom = false),
        Category(name = "Education",     emoji = "📚", colorHex = "#1D9E75", isCustom = false),
        Category(name = "Travel",        emoji = "✈️", colorHex = "#85B7EB", isCustom = false),
        Category(name = "ATM / Cash",    emoji = "💵", colorHex = "#888780", isCustom = false),
        Category(name = "Other",         emoji = "📌", colorHex = "#5F5E5A", isCustom = false)
    )
}
