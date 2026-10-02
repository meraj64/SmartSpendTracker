package com.smartspend.data.repository

import com.smartspend.data.db.KhataEntryDao
import com.smartspend.data.db.KhataPersonDao
import com.smartspend.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KhataRepository @Inject constructor(
    private val personDao: KhataPersonDao,
    private val entryDao: KhataEntryDao
) {
    val allPersons: Flow<List<KhataPerson>> = personDao.getAll()
    val allEntries: Flow<List<KhataEntry>> = entryDao.getAll()

    suspend fun insertPerson(person: KhataPerson): Long = personDao.insert(person)
    suspend fun updatePerson(person: KhataPerson) = personDao.update(person)
    suspend fun deletePerson(person: KhataPerson) = personDao.delete(person)
    fun searchPersons(query: String) = personDao.search(query)

    suspend fun insertEntry(entry: KhataEntry): Long = entryDao.insert(entry)
    suspend fun updateEntry(entry: KhataEntry) = entryDao.update(entry)
    suspend fun deleteEntry(entry: KhataEntry) = entryDao.delete(entry)
    suspend fun markSettled(id: Long) = entryDao.markSettled(id)

    fun getEntriesForPerson(personId: Long): Flow<List<KhataEntry>> = entryDao.getForPerson(personId)

    /** Per-person summary combining person info with totals — used in person list */
    fun getPersonSummaries(): Flow<List<KhataPersonSummary>> {
        return personDao.getAll().map { persons ->
            persons.map { person ->
                val given = entryDao.getTotalGivenToPerson(person.id)
                val received = entryDao.getTotalReceivedFromPerson(person.id)
                val lastDate = entryDao.getForPerson(person.id).first().maxOfOrNull { it.date }
                KhataPersonSummary(person, given, received, lastDate)
            }
        }
    }

    suspend fun getPersonSummary(personId: Long): KhataPersonSummary? {
        val person = personDao.getById(personId) ?: return null
        val given = entryDao.getTotalGivenToPerson(personId)
        val received = entryDao.getTotalReceivedFromPerson(personId)
        val lastDate = entryDao.getForPerson(personId).first().maxOfOrNull { it.date }
        return KhataPersonSummary(person, given, received, lastDate)
    }

    /** Overall ledger summary across all persons — drives the dashboard cards */
    val overallSummary: Flow<KhataOverallSummary> = combine(
        entryDao.getTotalGivenOverall(),
        entryDao.getTotalReceivedOverall(),
        entryDao.getTotalCashExpense(),
        entryDao.getTotalCashIncome()
    ) { given, received, cashExpense, cashIncome ->
        KhataOverallSummary(given, received, cashExpense, cashIncome)
    }

    /** Entries with money still pending settlement past N days — used for reminders */
    suspend fun getOverdueSettlements(daysThreshold: Int): List<KhataEntry> {
        val cutoff = System.currentTimeMillis() - daysThreshold.toLong() * 24 * 60 * 60 * 1000
        return entryDao.getPendingOlderThan(cutoff)
    }
}
