package com.smartspend.data.db

import androidx.room.*
import com.smartspend.data.models.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY name ASC")
    fun getActiveCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("UPDATE categories SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("""
        UPDATE expenses SET categoryId = :targetId WHERE categoryId = :sourceId
    """)
    suspend fun reassignExpenses(sourceId: Long, targetId: Long)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Used by the local backup feature to export every category, including archived ones. */
    @Query("SELECT * FROM categories")
    suspend fun getAllOnce(): List<Category>
}
