package com.smartspend.ui.khata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.data.models.KhataEntry
import com.smartspend.data.models.KhataPersonSummary
import com.smartspend.data.repository.KhataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonDetailState(
    val summary: KhataPersonSummary? = null,
    val entries: List<KhataEntry> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class KhataPersonDetailViewModel @Inject constructor(
    private val repo: KhataRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PersonDetailState())
    val state: StateFlow<PersonDetailState> = _state.asStateFlow()

    fun load(personId: Long) {
        viewModelScope.launch {
            repo.getEntriesForPerson(personId).collect { entries ->
                val summary = repo.getPersonSummary(personId)
                _state.value = PersonDetailState(summary = summary, entries = entries, isLoading = false)
            }
        }
    }

    fun markSettled(entryId: Long) {
        viewModelScope.launch { repo.markSettled(entryId) }
    }

    fun deleteEntry(entry: KhataEntry) {
        viewModelScope.launch { repo.deleteEntry(entry) }
    }
}
