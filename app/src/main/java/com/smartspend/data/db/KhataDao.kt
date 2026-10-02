package com.smartspend.data.db

import androidx.room.*
import com.smartspend.data.models.KhataEntry
import com.smartspend.data.models.KhataPerson
import kotlinx.coroutines.flow.Flow

@Dao
interface KhataPersonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: KhataPerson): Long

    @Update
    suspend fun update(person: KhataPerson)

    @Delete
    suspend fun delete(person: KhataPerson)

    @Query("SELECT * FROM khata_persons ORDER BY name ASC")
    fun getAll(): Flow<List<KhataPerson>>

    @Query("SELECT * FROM khata_persons WHERE id = :id")
    suspend fun getById(id: Long): KhataPerson?

    @Query("SELECT * FROM khata_persons WHERE name LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<KhataPerson>>

    /** Used by the local backup feature to export every khata person. */
    @Query("SELECT * FROM khata_persons")
    suspend fun getAllOnce(): List<KhataPerson>
}

@Dao
interface KhataEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: KhataEntry): Long

    @Update
    suspend fun update(entry: KhataEntry)

    @Delete
    suspend fun delete(entry: KhataEntry)

    @Query("SELECT * FROM khata_entries ORDER BY date DESC")
    fun getAll(): Flow<List<KhataEntry>>

    @Query("SELECT * FROM khata_entries WHERE personId = :personId ORDER BY date DESC")
    fun getForPerson(personId: Long): Flow<List<KhataEntry>>

    @Query("SELECT * FROM khata_entries WHERE id = :id")
    suspend fun getById(id: Long): KhataEntry?

    @Query("SELECT COALESCE(SUM(amount),0) FROM khata_entries WHERE personId = :personId AND type = 'MONEY_GIVEN'")
    suspend fun getTotalGivenToPerson(personId: Long): Double

    @Query("SELECT COALESCE(SUM(amount),0) FROM khata_entries WHERE personId = :personId AND type = 'MONEY_RECEIVED'")
    suspend fun getTotalReceivedFromPerson(personId: Long): Double

    @Query("SELECT COALESCE(SUM(amount),0) FROM khata_entries WHERE type = 'MONEY_GIVEN'")
    fun getTotalGivenOverall(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount),0) FROM khata_entries WHERE type = 'MONEY_RECEIVED'")
    fun getTotalReceivedOverall(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount),0) FROM khata_entries WHERE type = 'CASH_EXPENSE'")
    fun getTotalCashExpense(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount),0) FROM khata_entries WHERE type = 'CASH_INCOME'")
    fun getTotalCashIncome(): Flow<Double>

    @Query("""
        SELECT * FROM khata_entries
        WHERE status = 'PENDING' AND type = 'MONEY_GIVEN' AND date <= :cutoff
    """)
    suspend fun getPendingOlderThan(cutoff: Long): List<KhataEntry>

    @Query("UPDATE khata_entries SET status = 'SETTLED' WHERE id = :id")
    suspend fun markSettled(id: Long)

    /** Used by the local backup feature to export every khata entry. */
    @Query("SELECT * FROM khata_entries")
    suspend fun getAllOnce(): List<KhataEntry>
}
