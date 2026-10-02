package com.smartspend.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.data.models.Bill
import com.smartspend.data.models.BillStatus
import com.smartspend.data.models.BillType
import com.smartspend.data.models.RecurrenceFrequency
import com.smartspend.data.repository.BillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillsUiState(
    val bills: List<Bill> = emptyList(),
    val isLoading: Boolean = true
) {
    val grouped: Map<BillStatus, List<Bill>> get() = bills.groupBy { it.computeStatus() }
    val totalUpcoming: Double get() = bills.filter { !it.isPaid }.sumOf { it.amount }
}

@HiltViewModel
class BillViewModel @Inject constructor(
    private val billRepo: BillRepository
) : ViewModel() {

    val uiState = billRepo.allBills
        .map { bills -> BillsUiState(bills = bills.sortedBy { it.dueDate }, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillsUiState())

    fun addBill(
        title: String, type: BillType, amount: Double, dueDate: Long,
        repeat: RecurrenceFrequency, customDays: Int?, categoryId: Long?, notes: String?
    ) {
        viewModelScope.launch {
            billRepo.insert(
                Bill(
                    title = title, type = type, amount = amount, dueDate = dueDate,
                    repeatFrequency = repeat, customRepeatDays = customDays,
                    categoryId = categoryId, notes = notes
                )
            )
        }
    }

    fun markPaid(bill: Bill) {
        viewModelScope.launch { billRepo.markPaid(bill) }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch { billRepo.delete(bill) }
    }

    fun updateBill(bill: Bill) {
        viewModelScope.launch { billRepo.update(bill) }
    }
}
