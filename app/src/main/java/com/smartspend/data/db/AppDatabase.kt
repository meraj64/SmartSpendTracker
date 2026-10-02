package com.smartspend.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartspend.data.models.*

@Database(
    entities = [
        Transaction::class,
        Category::class,
        Expense::class,
        Income::class,
        Budget::class,
        Bill::class,
        BillPaymentHistory::class,
        KhataPerson::class,
        KhataEntry::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun budgetDao(): BudgetDao
    abstract fun billDao(): BillDao
    abstract fun khataPersonDao(): KhataPersonDao
    abstract fun khataEntryDao(): KhataEntryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** Adds the `sourceSmsBody` column used by "Sync from SMS" — a plain column add,
         *  so existing bills, expenses, budgets etc. are left completely untouched. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN sourceSmsBody TEXT")
            }
        }

        /** Adds the `entryType` column used to mark an expense as Credit or Excluded
         *  (self-transfers/investments) so it can be left out of spend totals. Every
         *  existing row defaults to 'EXPENSE', so nothing already recorded changes behavior. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN entryType TEXT NOT NULL DEFAULT 'EXPENSE'")
            }
        }

        /** Adds `isCredit` so a detected "credited"/"received" SMS or notification can be
         *  told apart from a normal debit once it reaches the pending queue. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isCredit INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_spend_db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    // Only used as a last resort for versions older than 2, which predate
                    // this migration chain. Every migration from here forward should be
                    // added explicitly above so user data is never silently wiped again.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
