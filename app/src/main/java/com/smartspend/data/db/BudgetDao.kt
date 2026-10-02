package com.smartspend.data.db

import androidx.room.*
import com.smartspend.data.models.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year ORDER BY categoryId IS NULL DESC")
    fun getForMonth(month: Int, year: Int): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE categoryId IS NULL AND month = :month AND year = :year LIMIT 1")
    suspend fun getOverallBudget(month: Int, year: Int): Budget?

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND month = :month AND year = :year LIMIT 1")
    suspend fun getCategoryBudget(categoryId: Long, month: Int, year: Int): Budget?

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): Budget?

    @Query("UPDATE budgets SET alertsSent = :alertsSent WHERE id = :id")
    suspend fun updateAlertsSent(id: Long, alertsSent: String)

    /** Used by the local backup feature to export every budget regardless of month/year. */
    @Query("SELECT * FROM budgets")
    suspend fun getAllOnce(): List<Budget>
}
