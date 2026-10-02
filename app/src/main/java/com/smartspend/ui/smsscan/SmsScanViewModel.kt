package com.smartspend.ui.smsscan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.data.models.Transaction
import com.smartspend.data.models.TransactionCategory
import com.smartspend.data.models.TransactionSource
import com.smartspend.data.repository.TransactionRepository
import com.smartspend.util.SmsParser
import com.smartspend.util.SmsScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

enum class ScanStep { IDLE, SCANNING, DONE, ERROR }

data class SmsScanState(
    val fromDate: Long = defaultFromDate(),
    val toDate: Long = System.currentTimeMillis(),
    val step: ScanStep = ScanStep.IDLE,
    val totalMessagesScanned: Int = 0,
    val debitedFound: Int = 0,
    val imported: Int = 0,
    val skippedDuplicate: Int = 0,
    val errorMessage: String? = null
) {
    companion object {
        /** Default the range picker to the last 30 days. */
        fun defaultFromDate(): Long = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -30)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
    }
}

@HiltViewModel
class SmsScanViewModel @Inject constructor(
    application: Application,
    private val transactionRepo: TransactionRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SmsScanState())
    val state: StateFlow<SmsScanState> = _state.asStateFlow()

    fun setFromDate(millis: Long) {
        _state.value = _state.value.copy(fromDate = millis)
    }

    fun setToDate(millis: Long) {
        // Inclusive of the whole selected day.
        val endOfDay = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis
        _state.value = _state.value.copy(toDate = endOfDay)
    }

    fun reset() {
        _state.value = _state.value.copy(
            step = ScanStep.IDLE,
            totalMessagesScanned = 0,
            debitedFound = 0,
            imported = 0,
            skippedDuplicate = 0,
            errorMessage = null
        )
    }

    fun scan() {
        val current = _state.value
        if (current.step == ScanStep.SCANNING) return
        if (current.fromDate > current.toDate) {
            _state.value = current.copy(step = ScanStep.ERROR, errorMessage = "Start date must be before end date")
            return
        }

        _state.value = current.copy(
            step = ScanStep.SCANNING,
            totalMessagesScanned = 0,
            debitedFound = 0,
            imported = 0,
            skippedDuplicate = 0,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val messages = withContext(Dispatchers.IO) {
                    SmsScanner.readInbox(context, current.fromDate, current.toDate)
                }

                val alreadyKnownBodies = withContext(Dispatchers.IO) {
                    transactionRepo.existingRawSmsBodies().toMutableSet()
                }

                var debitedFound = 0
                var imported = 0
                var skippedDuplicate = 0

                for (sms in messages) {
                    val parsed = SmsParser.parse(sms.body, sms.sender, allowMissingAmount = false) ?: continue
                    debitedFound++

                    if (sms.body in alreadyKnownBodies) {
                        skippedDuplicate++
                        continue
                    }

                    val transaction = Transaction(
                        amount = parsed.amount,
                        merchantName = parsed.merchantHint ?: "SMS Expense",
                        merchantPlaceId = null,
                        merchantAddress = null,
                        category = TransactionCategory.OTHER,
                        latitude = null,
                        longitude = null,
                        transactionTime = sms.timestamp,
                        detectedTime = System.currentTimeMillis(),
                        rawSmsBody = parsed.rawBody,
                        bankName = parsed.bankName,
                        referenceNumber = parsed.referenceNumber,
                        source = TransactionSource.SMS,
                        isConfirmed = false,
                        isPending = true,
                        isAmountMissing = false,
                        isCredit = parsed.isCredit
                    )

                    val wasImported = withContext(Dispatchers.IO) {
                        transactionRepo.importScannedTransaction(transaction)
                    }
                    alreadyKnownBodies.add(sms.body)

                    if (wasImported) imported++ else skippedDuplicate++

                    _state.value = _state.value.copy(
                        totalMessagesScanned = messages.size,
                        debitedFound = debitedFound,
                        imported = imported,
                        skippedDuplicate = skippedDuplicate
                    )
                }

                _state.value = _state.value.copy(
                    step = ScanStep.DONE,
                    totalMessagesScanned = messages.size,
                    debitedFound = debitedFound,
                    imported = imported,
                    skippedDuplicate = skippedDuplicate
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(step = ScanStep.ERROR, errorMessage = e.message ?: "Something went wrong while scanning")
            }
        }
    }
}
