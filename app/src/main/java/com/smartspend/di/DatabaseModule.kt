package com.smartspend.di

import android.content.Context
import com.smartspend.data.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides fun provideIncomeDao(db: AppDatabase): IncomeDao = db.incomeDao()
    @Provides fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideBillDao(db: AppDatabase): BillDao = db.billDao()
    @Provides fun provideKhataPersonDao(db: AppDatabase): KhataPersonDao = db.khataPersonDao()
    @Provides fun provideKhataEntryDao(db: AppDatabase): KhataEntryDao = db.khataEntryDao()
}
