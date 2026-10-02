package com.smartspend.data.repository

import com.smartspend.data.db.CategoryDao
import com.smartspend.data.db.ExpenseDao
import com.smartspend.data.models.Category
import com.smartspend.data.models.DefaultCategories
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao
) {
    val activeCategories: Flow<List<Category>> = categoryDao.getActiveCategories()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun seedDefaultsIfEmpty() {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(DefaultCategories.seed)
        }
    }

    suspend fun insert(category: Category): Long = categoryDao.insert(category)
    suspend fun update(category: Category) = categoryDao.update(category)
    suspend fun archive(id: Long) = categoryDao.archive(id)
    suspend fun getById(id: Long): Category? = categoryDao.getById(id)

    /** Merge source category into target: reassign all expenses then delete source */
    suspend fun merge(sourceId: Long, targetId: Long) {
        categoryDao.reassignExpenses(sourceId, targetId)
        categoryDao.deleteById(sourceId)
    }

    suspend fun delete(category: Category) = categoryDao.delete(category)
}
