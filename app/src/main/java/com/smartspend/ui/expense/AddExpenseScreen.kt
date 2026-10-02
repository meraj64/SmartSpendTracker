package com.smartspend.ui.expense

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.data.models.EntryType
import com.smartspend.data.models.Expense
import com.smartspend.data.models.PaymentMethod
import com.smartspend.data.models.Transaction
import com.smartspend.data.repository.CategoryRepository
import com.smartspend.data.repository.ExpenseRepository
import com.smartspend.data.repository.TransactionRepository
import com.smartspend.util.LocationHelper
import com.smartspend.util.NearbyPlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/** What the form is currently doing — controls title, button label, and save/delete behavior. */
enum class ExpenseFormMode { NEW, EDIT_EXPENSE, RESOLVE_TRANSACTION }

data class AddExpenseFormState(
    val mode: ExpenseFormMode = ExpenseFormMode.NEW,
    val isLoading: Boolean = false,
    val amount: String = "",
    val merchant: String = "",
    val note: String = "",
    val categoryId: Long? = null,
    val method: PaymentMethod = PaymentMethod.UPI,
    val entryType: EntryType = EntryType.EXPENSE,
    val date: Long = System.currentTimeMillis(),
    val existingExpense: Expense? = null,
    val sourceTransaction: Transaction? = null,
    val didFinish: Boolean = false,
    // Nearby-places suggestions (Places API) shown so the user can pick a merchant
    // instead of typing it in — see loadNearbyPlaces().
    val nearbyPlaces: List<NearbyPlace> = emptyList(),
    val isLoadingNearby: Boolean = false,
    val nearbyPlacesLoaded: Boolean = false,
    // Manual merchant search (Places "Search by Text") — lets the user look up a merchant by
    // name instead of relying only on what was auto-detected nearby. See updateSearchQuery().
    val searchQuery: String = "",
    val searchResults: List<NearbyPlace> = emptyList(),
    val isSearching: Boolean = false
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val categoryRepo: CategoryRepository,
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    val categories = categoryRepo.activeCategories.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    var formState by mutableStateOf(AddExpenseFormState())
        private set

    private var loadedFor: Pair<Long?, Long?>? = null

    /** Loads existing data to prefill the form. Safe to call repeatedly (e.g. on recomposition). */
    fun load(expenseId: Long?, transactionId: Long?) {
        val key = expenseId to transactionId
        if (loadedFor == key) return
        loadedFor = key

        if (expenseId == null && transactionId == null) {
            formState = AddExpenseFormState(mode = ExpenseFormMode.NEW)
            return
        }

        formState = formState.copy(isLoading = true)
        viewModelScope.launch {
            when {
                expenseId != null -> {
                    val expense = expenseRepo.getById(expenseId)
                    formState = if (expense != null) {
                        AddExpenseFormState(
                            mode = ExpenseFormMode.EDIT_EXPENSE,
                            amount = formatAmountForInput(expense.amount),
                            merchant = expense.merchantName ?: "",
                            note = expense.note ?: "",
                            categoryId = expense.categoryId,
                            method = expense.paymentMethod,
                            entryType = expense.entryType,
                            date = expense.date,
                            existingExpense = expense,
                            isLoading = false
                        )
                    } else {
                        AddExpenseFormState(mode = ExpenseFormMode.NEW, isLoading = false)
                    }
                }
                transactionId != null -> {
                    val transaction = transactionRepo.getById(transactionId)
                    formState = if (transaction != null) {
                        val matchedCategoryId = categories.value
                            .firstOrNull { it.name == transaction.category.displayName }?.id
                        AddExpenseFormState(
                            mode = ExpenseFormMode.RESOLVE_TRANSACTION,
                            // Leave amount blank (rather than "0") when it wasn't in the
                            // notification at all — forces the user to actually type it in.
                            amount = if (transaction.isAmountMissing) "" else formatAmountForInput(transaction.amount),
                            merchant = transaction.merchantName ?: "",
                            note = transaction.note ?: "",
                            categoryId = matchedCategoryId,
                            method = PaymentMethod.UPI,
                            entryType = if (transaction.isCredit) EntryType.CREDIT else EntryType.EXPENSE,
                            date = transaction.transactionTime,
                            sourceTransaction = transaction,
                            isLoading = false
                        )
                    } else {
                        AddExpenseFormState(mode = ExpenseFormMode.NEW, isLoading = false)
                    }
                }
            }
        }
    }

    fun updateAmount(value: String) { formState = formState.copy(amount = value.filter { it.isDigit() || it == '.' }) }
    fun updateMerchant(value: String) { formState = formState.copy(merchant = value) }
    fun updateNote(value: String) { formState = formState.copy(note = value) }
    fun updateCategory(id: Long) { formState = formState.copy(categoryId = id) }
    fun updateMethod(method: PaymentMethod) { formState = formState.copy(method = method) }
    fun updateEntryType(type: EntryType) { formState = formState.copy(entryType = type) }
    fun updateDate(date: Long) { formState = formState.copy(date = date) }

    /**
     * Fetches the top nearby places (via Places API) around the detected transaction/expense
     * location, so the user can tap one directly instead of typing the merchant name.
     * [force] re-fetches even if we already loaded suggestions once (used by the refresh action).
     */
    fun loadNearbyPlaces(context: android.content.Context, force: Boolean = false) {
        val s = formState
        if (s.isLoadingNearby) return
        if (s.nearbyPlacesLoaded && !force) return

        val lat = s.sourceTransaction?.latitude ?: s.existingExpense?.latitude
        val lng = s.sourceTransaction?.longitude ?: s.existingExpense?.longitude
        if (lat == null || lng == null) {
            formState = formState.copy(nearbyPlacesLoaded = true)
            return
        }

        formState = formState.copy(isLoadingNearby = true)
        viewModelScope.launch {
            val location = Location("smartspend").apply {
                latitude = lat
                longitude = lng
            }
            val places = LocationHelper.findNearbyPlaces(
                context = context,
                location = location,
                merchantHint = formState.merchant.ifBlank { null }
            )
            formState = formState.copy(
                nearbyPlaces = places.take(5),
                isLoadingNearby = false,
                nearbyPlacesLoaded = true
            )
        }
    }

    /** User tapped a suggested nearby place — use it as the merchant name directly. */
    fun selectNearbyPlace(place: NearbyPlace) {
        formState = formState.copy(merchant = place.name)
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    /**
     * Runs a debounced free-text merchant search as the user types in the search dialog —
     * lets them find a merchant by name instead of being limited to the auto-detected nearby
     * list (e.g. wrong outlet picked at a mall, or no location was available at all).
     */
    fun updateSearchQuery(query: String, context: android.content.Context) {
        formState = formState.copy(searchQuery = query)
        searchJob?.cancel()

        if (query.isBlank()) {
            formState = formState.copy(searchResults = emptyList(), isSearching = false)
            return
        }

        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400) // debounce so we're not firing a request per keystroke
            formState = formState.copy(isSearching = true)

            val lat = formState.sourceTransaction?.latitude ?: formState.existingExpense?.latitude
            val lng = formState.sourceTransaction?.longitude ?: formState.existingExpense?.longitude
            val biasLocation = if (lat != null && lng != null) {
                android.location.Location("smartspend").apply { latitude = lat; longitude = lng }
            } else null

            val results = LocationHelper.searchPlacesByText(context, query, biasLocation)
            // Only apply results if this is still the latest query (guards against a slower
            // earlier request resolving after a newer one).
            if (formState.searchQuery == query) {
                formState = formState.copy(searchResults = results, isSearching = false)
            }
        }
    }

    /** User picked a result from the merchant search dialog. */
    fun selectSearchResult(place: NearbyPlace) {
        formState = formState.copy(merchant = place.name, searchQuery = "", searchResults = emptyList())
    }

    /** Clears search state — call when the search dialog is dismissed. */
    fun clearSearch() {
        searchJob?.cancel()
        formState = formState.copy(searchQuery = "", searchResults = emptyList(), isSearching = false)
    }

    /** Persists the form. Handles all three modes: create, edit, and confirm-from-transaction. */
    fun save() {
        val s = formState
        val amount = s.amount.toDoubleOrNull() ?: return
        val categoryId = s.categoryId ?: return

        viewModelScope.launch {
            when (s.mode) {
                ExpenseFormMode.NEW -> {
                    expenseRepo.insert(
                        Expense(
                            amount = amount,
                            categoryId = categoryId,
                            merchantName = s.merchant.ifBlank { null },
                            note = s.note.ifBlank { null },
                            paymentMethod = s.method,
                            entryType = s.entryType,
                            date = s.date
                        )
                    )
                }
                ExpenseFormMode.EDIT_EXPENSE -> {
                    val existing = s.existingExpense ?: return@launch
                    expenseRepo.update(
                        existing.copy(
                            amount = amount,
                            categoryId = categoryId,
                            merchantName = s.merchant.ifBlank { null },
                            note = s.note.ifBlank { null },
                            paymentMethod = s.method,
                            entryType = s.entryType,
                            date = s.date
                        )
                    )
                }
                ExpenseFormMode.RESOLVE_TRANSACTION -> {
                    val transaction = s.sourceTransaction ?: return@launch
                    expenseRepo.insert(
                        Expense(
                            amount = amount,
                            categoryId = categoryId,
                            merchantName = s.merchant.ifBlank { null },
                            note = s.note.ifBlank { null },
                            paymentMethod = s.method,
                            entryType = s.entryType,
                            date = s.date,
                            latitude = transaction.latitude,
                            longitude = transaction.longitude,
                            isAutoDetected = true,
                            rawSmsBody = transaction.rawSmsBody,
                            locationName = transaction.merchantAddress
                        )
                    )
                    transactionRepo.confirmTransaction(transaction.id)
                }
            }
            formState = formState.copy(didFinish = true)
        }
    }

    /** Deletes the existing expense (edit mode) or dismisses the detected transaction (resolve mode). */
    fun deleteOrDismiss() {
        val s = formState
        viewModelScope.launch {
            when (s.mode) {
                ExpenseFormMode.EDIT_EXPENSE -> s.existingExpense?.let { expenseRepo.delete(it) }
                ExpenseFormMode.RESOLVE_TRANSACTION -> s.sourceTransaction?.let { transactionRepo.dismissTransaction(it.id) }
                ExpenseFormMode.NEW -> {}
            }
            formState = formState.copy(didFinish = true)
        }
    }

    private fun formatAmountForInput(amount: Double): String =
        if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    onBack: () -> Unit,
    expenseId: Long? = null,
    transactionId: Long? = null,
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    LaunchedEffect(expenseId, transactionId) { viewModel.load(expenseId, transactionId) }

    val categories by viewModel.categories.collectAsState()
    val state = viewModel.formState
    var catExpanded by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMerchantSearch by remember { mutableStateOf(false) }

    // Auto-fetch nearby-place suggestions once we know where the transaction/expense happened,
    // so the user can pick the right merchant instead of typing it.
    val context = LocalContext.current
    val nearbyLat = state.sourceTransaction?.latitude ?: state.existingExpense?.latitude
    val nearbyLng = state.sourceTransaction?.longitude ?: state.existingExpense?.longitude
    LaunchedEffect(state.isLoading, nearbyLat, nearbyLng) {
        if (!state.isLoading && nearbyLat != null && nearbyLng != null) {
            viewModel.loadNearbyPlaces(context)
        }
    }

    LaunchedEffect(state.didFinish) {
        if (state.didFinish) onBack()
    }

    val title = when (state.mode) {
        ExpenseFormMode.NEW -> "Add Expense"
        ExpenseFormMode.EDIT_EXPENSE -> "Edit Expense"
        ExpenseFormMode.RESOLVE_TRANSACTION -> "Confirm Transaction"
    }
    val saveLabel = if (state.mode == ExpenseFormMode.RESOLVE_TRANSACTION) "Save & Confirm" else "Save Expense"
    val deleteLabel = if (state.mode == ExpenseFormMode.RESOLVE_TRANSACTION) "Dismiss" else "Delete"
    val canDelete = state.mode != ExpenseFormMode.NEW

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    if (canDelete) {
                        IconButton(onClick = { viewModel.deleteOrDismiss() }) {
                            Icon(Icons.Default.Delete, deleteLabel, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            if (state.mode == ExpenseFormMode.RESOLVE_TRANSACTION) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Detected from ${state.sourceTransaction?.bankName ?: "SMS"}" +
                                if (state.sourceTransaction?.isCredit == true) " · looks like money received" else "",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            "Review the details below, then save to confirm.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            val detectedRawSms = state.sourceTransaction?.rawSmsBody ?: state.existingExpense?.rawSmsBody
            val detectedLocation = state.sourceTransaction?.merchantAddress ?: state.existingExpense?.locationName
            val detectedLat = state.sourceTransaction?.latitude ?: state.existingExpense?.latitude
            val detectedLng = state.sourceTransaction?.longitude ?: state.existingExpense?.longitude
            if (detectedRawSms != null || detectedLocation != null || detectedLat != null) {
                DetectedDetailsCard(
                    rawSmsBody = detectedRawSms,
                    locationName = detectedLocation,
                    latitude = detectedLat,
                    longitude = detectedLng
                )
            }

            OutlinedTextField(
                value = state.amount,
                onValueChange = viewModel::updateAmount,
                label = { Text("Amount") },
                prefix = { Text("₹") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium,
                shape = RoundedCornerShape(16.dp)
            )

            val merchantIsFromSms = state.sourceTransaction?.rawSmsBody != null || state.existingExpense?.rawSmsBody != null
            OutlinedTextField(
                value = state.merchant, onValueChange = viewModel::updateMerchant,
                label = { Text(if (merchantIsFromSms) "Merchant name (from SMS)" else "Merchant / Shop Name") },
                trailingIcon = {
                    IconButton(onClick = { showMerchantSearch = true }) {
                        Icon(Icons.Default.Search, "Search for a merchant")
                    }
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )

            EntryTypeSelector(
                selected = state.entryType,
                onSelect = viewModel::updateEntryType
            )

            if (nearbyLat != null && nearbyLng != null) {
                NearbyPlacesPicker(
                    isLoading = state.isLoadingNearby,
                    places = state.nearbyPlaces,
                    selectedName = state.merchant,
                    onSelect = viewModel::selectNearbyPlace,
                    onRefresh = { viewModel.loadNearbyPlaces(context, force = true) }
                )
            }

            ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }) {
                OutlinedTextField(
                    value = categories.firstOrNull { it.id == state.categoryId }?.let { "${it.emoji} ${it.name}" } ?: "",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Category") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text("${cat.emoji} ${cat.name}") },
                            onClick = { viewModel.updateCategory(cat.id); catExpanded = false }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(expanded = methodExpanded, onExpandedChange = { methodExpanded = it }) {
                OutlinedTextField(
                    value = state.method.name, onValueChange = {}, readOnly = true,
                    label = { Text("Payment Method") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                    PaymentMethod.values().forEach { m ->
                        DropdownMenuItem(text = { Text(m.name) }, onClick = { viewModel.updateMethod(m); methodExpanded = false })
                    }
                }
            }

            OutlinedTextField(
                value = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(state.date)),
                onValueChange = {}, readOnly = true, enabled = false,
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            OutlinedTextField(
                value = state.note, onValueChange = viewModel::updateNote,
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 2
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { viewModel.save() },
                enabled = state.amount.toDoubleOrNull() != null && state.categoryId != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(saveLabel) }

            if (canDelete) {
                OutlinedButton(
                    onClick = { viewModel.deleteOrDismiss() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(deleteLabel) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = state.date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { dpState.selectedDateMillis?.let { viewModel.updateDate(it) }; showDatePicker = false }) {
                    Text("OK")
                }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    if (showMerchantSearch) {
        MerchantSearchDialog(
            query = state.searchQuery,
            results = state.searchResults,
            isSearching = state.isSearching,
            onQueryChange = { viewModel.updateSearchQuery(it, context) },
            onSelect = { place ->
                viewModel.selectSearchResult(place)
                showMerchantSearch = false
            },
            onDismiss = {
                viewModel.clearSearch()
                showMerchantSearch = false
            }
        )
    }
}

/**
 * Full-text merchant search dialog — lets the user look up a place by name (Places "Search by
 * Text") instead of being limited to the auto-detected nearby list. Opened via the search icon
 * on the Merchant field.
 */
@Composable
private fun MerchantSearchDialog(
    query: String,
    results: List<NearbyPlace>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (NearbyPlace) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp).heightIn(max = 480.dp)) {
                Text("Search merchant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("e.g. Starbucks, Big Bazaar…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    shape = RoundedCornerShape(12.dp)
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                Spacer(Modifier.height(8.dp))

                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when {
                        isSearching -> {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Searching…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        query.isBlank() -> {
                            Text(
                                "Start typing to find a merchant or place",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                        results.isEmpty() -> {
                            Text(
                                "No matches for \"$query\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                        else -> {
                            results.forEach { place ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(place) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(place.name, style = MaterialTheme.typography.bodyMedium)
                                        val subtitle = listOfNotNull(
                                            place.distanceMeters?.let { formatDistance(it) },
                                            place.address
                                        ).joinToString(" • ")
                                        if (subtitle.isNotBlank()) {
                                            Text(
                                                subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * Shows the top nearby places (from Places API) as a tappable list so the user can select the
 * correct merchant directly instead of typing it in after editing the detected location.
 */
@Composable
private fun NearbyPlacesPicker(
    isLoading: Boolean,
    places: List<NearbyPlace>,
    selectedName: String,
    onSelect: (NearbyPlace) -> Unit,
    onRefresh: () -> Unit
) {
    if (!isLoading && places.isEmpty()) return

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Nearby places",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Refresh, "Refresh nearby places",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isLoading) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Looking up nearby places…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                places.forEach { place ->
                    val isSelected = place.name.equals(selectedName, ignoreCase = true)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(place) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn, null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                place.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            val subtitle = listOfNotNull(
                                place.distanceMeters?.let { formatDistance(it) },
                                place.address
                            ).joinToString(" • ")
                            if (subtitle.isNotBlank()) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lets the user mark this entry as a normal Expense, a Credit (money in — refund, cashback),
 * or Excluded (self-transfer between own accounts, investments) — the latter two are kept in
 * history but never count towards spend totals, budgets, or reports.
 */
@Composable
private fun EntryTypeSelector(selected: com.smartspend.data.models.EntryType, onSelect: (com.smartspend.data.models.EntryType) -> Unit) {
    Column {
        Text(
            "Entry type",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            com.smartspend.data.models.EntryType.values().forEach { type ->
                val isSelected = type == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(type) },
                    label = { Text(type.displayName) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (selected != com.smartspend.data.models.EntryType.EXPENSE) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (selected == com.smartspend.data.models.EntryType.CREDIT)
                    "Won't be counted in your spend totals — use for refunds, cashback, or money received."
                else
                    "Won't be counted in your spend totals — use for self-transfers or investments.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDistance(meters: Float): String =
    if (meters < 1000f) "${meters.toInt()} m" else "%.1f km".format(meters / 1000f)

/** Read-only card showing the raw SMS/notification text and location data behind an auto-detected expense. */
@Composable
private fun DetectedDetailsCard(
    rawSmsBody: String?,
    locationName: String?,
    latitude: Double?,
    longitude: Double?
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Detected details", style = MaterialTheme.typography.labelMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            if (locationName != null) {
                Spacer(Modifier.height(6.dp))
                Text("📍 $locationName", style = MaterialTheme.typography.bodySmall)
            }
            if (latitude != null && longitude != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Lat/Lng: %.5f, %.5f".format(latitude, longitude),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded && rawSmsBody != null) {
                Spacer(Modifier.height(8.dp))
                Text("Original message", style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    rawSmsBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
