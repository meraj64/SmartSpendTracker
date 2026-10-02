package com.smartspend.ui.khata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.data.models.*
import com.smartspend.data.repository.KhataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KhataUiState(
    val personSummaries: List<KhataPersonSummary> = emptyList(),
    val overallSummary: KhataOverallSummary = KhataOverallSummary(0.0, 0.0, 0.0, 0.0),
    val isLoading: Boolean = true
)

@HiltViewModel
class KhataViewModel @Inject constructor(
    private val khataRepo: KhataRepository
) : ViewModel() {

    val uiState: kotlinx.coroutines.flow.StateFlow<KhataUiState> = combine(
        khataRepo.getPersonSummaries(),
        khataRepo.overallSummary
    ) { summaries, overall ->
        KhataUiState(personSummaries = summaries, overallSummary = overall, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KhataUiState())

    fun addPerson(name: String, phone: String?, colorHex: String) {
        viewModelScope.launch {
            khataRepo.insertPerson(KhataPerson(name = name, phone = phone, avatarColorHex = colorHex))
        }
    }

    fun addEntry(
        personId: Long?, type: KhataEntryType, amount: Double,
        description: String?, paymentMethod: PaymentMethod, date: Long
    ) {
        viewModelScope.launch {
            khataRepo.insertEntry(
                KhataEntry(
                    personId = personId, type = type, amount = amount,
                    description = description, paymentMethod = paymentMethod, date = date
                )
            )
        }
    }

    fun markSettled(entryId: Long) {
        viewModelScope.launch { khataRepo.markSettled(entryId) }
    }

    fun deleteEntry(entry: KhataEntry) {
        viewModelScope.launch { khataRepo.deleteEntry(entry) }
    }
}
