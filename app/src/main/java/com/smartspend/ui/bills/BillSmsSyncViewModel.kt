package com.smartspend.ui.bills

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.data.models.Bill
import com.smartspend.data.repository.BillRepository
import com.smartspend.data.repository.ExpenseRepository
import com.smartspend.util.BillSmsParser
import com.smartspend.util.SmsScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class BillSyncStep { IDLE, SCANNING, DONE, ERROR }

data class BillSyncState(
    val step: BillSyncStep = BillSyncStep.IDLE,
    val totalMessagesScanned: Int = 0,
    val billsFound: Int = 0,
    val created: Int = 0,
    val skippedDuplicate: Int = 0,
    val createdBills: List<Bill> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class BillSmsSyncViewModel @Inject constructor(
    application: Application,
    private val billRepo: BillRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(BillSyncState())
    val state: StateFlow<BillSyncState> = _state.asStateFlow()

    fun reset() {
        _state.value = BillSyncState()
    }

    /** Reads all SMS for the current calendar month and creates a Bill for every payment-due message found. */
    fun syncCurrentMonth() {
        val current = _state.value
        if (current.step == BillSyncStep.SCANNING) return

        _state.value = BillSyncState(step = BillSyncStep.SCANNING)

        viewModelScope.launch {
            try {
                val (start, end) = ExpenseRepository.currentMonthRange()
                val context = getApplication<Application>()

                val messages = withContext(Dispatchers.IO) {
                    SmsScanner.readInbox(context, start, end)
                }

                val alreadyKnownBodies = withContext(Dispatchers.IO) {
                    billRepo.existingSourceSmsBodies().toMutableSet()
                }

                var billsFound = 0
                var created = 0
                var skippedDuplicate = 0
                val createdBills = mutableListOf<Bill>()

                for (sms in messages) {
                    val parsed = BillSmsParser.parse(sms.body, sms.sender, sms.timestamp) ?: continue
                    billsFound++

                    if (sms.body in alreadyKnownBodies) {
                        skippedDuplicate++
                        continue
                    }

                    val bill = Bill(
                        title = parsed.title,
                        type = parsed.type,
                        amount = parsed.amount,
                        dueDate = parsed.dueDate,
                        sourceSmsBody = parsed.rawBody
                    )

                    val id = withContext(Dispatchers.IO) { billRepo.insert(bill) }
                    alreadyKnownBodies.add(sms.body)
                    createdBills.add(bill.copy(id = id))
                    created++

                    _state.value = _state.value.copy(
                        totalMessagesScanned = messages.size,
                        billsFound = billsFound,
                        created = created,
                        skippedDuplicate = skippedDuplicate,
                        createdBills = createdBills.toList()
                    )
                }

                _state.value = _state.value.copy(
                    step = BillSyncStep.DONE,
                    totalMessagesScanned = messages.size,
                    billsFound = billsFound,
                    created = created,
                    skippedDuplicate = skippedDuplicate,
                    createdBills = createdBills.toList()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(step = BillSyncStep.ERROR, errorMessage = e.message ?: "Something went wrong while syncing")
            }
        }
    }
}
