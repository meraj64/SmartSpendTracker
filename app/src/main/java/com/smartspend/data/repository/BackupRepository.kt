package com.smartspend.data.repository

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.smartspend.data.db.*
import com.smartspend.data.models.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The full contents of the local database, as written to / read from a backup JSON file. */
data class BackupPayload(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val incomes: List<Income> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val billPaymentHistory: List<BillPaymentHistory> = emptyList(),
    val khataPersons: List<KhataPerson> = emptyList(),
    val khataEntries: List<KhataEntry> = emptyList()
)

sealed class BackupResult {
    data class Success(val counts: Map<String, Int>) : BackupResult()
    data class Failure(val message: String) : BackupResult()
}

/**
 * Exports the entire local database (expenses, transactions, categories, incomes, budgets,
 * bills, and khata records) to a single JSON file the user picks a location for via the
 * system file picker (Storage Access Framework), and restores from one — entirely offline,
 * nothing leaves the device.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao,
    private val incomeDao: IncomeDao,
    private val budgetDao: BudgetDao,
    private val billDao: BillDao,
    private val khataPersonDao: KhataPersonDao,
    private val khataEntryDao: KhataEntryDao
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** Suggested file name for a new backup, e.g. smartspend_backup_2026-07-27_1530.json */
    fun suggestedFileName(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.getDefault())
        return "smartspend_backup_${sdf.format(java.util.Date())}.json"
    }

    /** Serializes the entire local database to JSON and writes it to the given SAF Uri. */
    suspend fun exportBackup(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val payload = buildPayload()
            val json = gson.toJson(payload)
            val stream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext BackupResult.Failure("Couldn't open the selected location for writing")
            stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            BackupResult.Success(payload.toCounts())
        } catch (e: Exception) {
            BackupResult.Failure(e.message ?: "Backup failed")
        }
    }

    /**
     * Reads a backup JSON file and restores it, REPLACING everything currently in the
     * database. Record IDs are preserved from the backup so relationships (category ↔
     * expense, person ↔ khata entry, etc.) stay intact.
     */
    suspend fun importBackup(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext BackupResult.Failure("Couldn't open the selected file for reading")
            val json = stream.use { it.readBytes().toString(Charsets.UTF_8) }

            val payload = try {
                gson.fromJson(json, BackupPayload::class.java)
            } catch (e: Exception) {
                null
            } ?: return@withContext BackupResult.Failure("This file doesn't look like a SmartSpend backup")

            db.clearAllTables()

            categoryDao.insertAll(payload.categories)
            payload.expenses.forEach { expenseDao.insert(it) }
            payload.incomes.forEach { incomeDao.insert(it) }
            payload.budgets.forEach { budgetDao.insert(it) }
            payload.bills.forEach { billDao.insert(it) }
            payload.billPaymentHistory.forEach { billDao.insertPaymentHistory(it) }
            payload.khataPersons.forEach { khataPersonDao.insert(it) }
            payload.khataEntries.forEach { khataEntryDao.insert(it) }
            payload.transactions.forEach { transactionDao.insert(it) }

            BackupResult.Success(payload.toCounts())
        } catch (e: Exception) {
            BackupResult.Failure(e.message ?: "Restore failed — the file may be corrupted or from an incompatible version")
        }
    }

    private suspend fun buildPayload(): BackupPayload = BackupPayload(
        transactions = transactionDao.getAllOnce(),
        categories = categoryDao.getAllOnce(),
        expenses = expenseDao.getAllOnce(),
        incomes = incomeDao.getAllOnce(),
        budgets = budgetDao.getAllOnce(),
        bills = billDao.getAllOnce(),
        billPaymentHistory = billDao.getAllPaymentHistoryOnce(),
        khataPersons = khataPersonDao.getAllOnce(),
        khataEntries = khataEntryDao.getAllOnce()
    )

    private fun BackupPayload.toCounts(): Map<String, Int> = mapOf(
        "Expenses" to expenses.size,
        "Transactions" to transactions.size,
        "Categories" to categories.size,
        "Incomes" to incomes.size,
        "Budgets" to budgets.size,
        "Bills" to bills.size,
        "Khata entries" to khataEntries.size
    )
}
