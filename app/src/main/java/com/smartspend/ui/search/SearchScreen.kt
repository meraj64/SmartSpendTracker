package com.smartspend.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.data.models.Category
import com.smartspend.data.models.EntryType
import com.smartspend.data.models.Expense
import com.smartspend.data.repository.CategoryRepository
import com.smartspend.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import java.net.URLDecoder
import javax.inject.Inject
import com.smartspend.ui.common.EmojiAvatar
import com.smartspend.ui.common.formatCurrency
import com.smartspend.ui.theme.GivenColor
import java.text.SimpleDateFormat
import java.util.*

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: ExpenseRepository,
    private val categoryRepo: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow<Long?>(null)

    // The time window search arrived with (e.g. whatever the dashboard was showing when
    // the person tapped the search icon). Absent -> search covers all-time by default.
    private val rangeStart: Long = savedStateHandle.get<Long>("rangeStart") ?: -1L
    private val rangeEnd: Long = savedStateHandle.get<Long>("rangeEnd") ?: -1L
    val rangeLabel: String = savedStateHandle.get<String>("rangeLabel")
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        ?: ""
    val hasRange: Boolean = rangeStart >= 0 && rangeEnd > rangeStart

    // Whether search is currently limited to [rangeStart, rangeEnd). Defaults to true
    // whenever a range was supplied, since that matches what the person was just looking at.
    private val scopeToRange = MutableStateFlow(hasRange)
    val isScopedToRange: StateFlow<Boolean> = scopeToRange.asStateFlow()

    val currentQuery: StateFlow<String> = query.asStateFlow()
    val selectedCategoryId: StateFlow<Long?> = categoryFilter.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepo.activeCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Text query (matching merchant name, note, OR category name), an optional category
    // chip filter, and whether to stay scoped to the incoming date range — all combined.
    val results: StateFlow<List<Expense>> = combine(
        query.debounce(300),
        categoryFilter,
        scopeToRange
    ) { q, catId, scoped -> Triple(q, catId, scoped) }
        .flatMapLatest { (q, catId, scoped) ->
            val useRange = scoped && hasRange
            when {
                q.isBlank() && catId == null -> flowOf(emptyList())
                q.isBlank() && catId != null && useRange -> repo.getByCategoryInRange(catId, rangeStart, rangeEnd)
                q.isBlank() && catId != null -> repo.getAllByCategory(catId)
                useRange -> repo.searchWithCategoryInRange(q, rangeStart, rangeEnd).mapFilterCategory(catId)
                else -> repo.searchWithCategory(q).mapFilterCategory(catId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun Flow<List<Expense>>.mapFilterCategory(catId: Long?): Flow<List<Expense>> =
        if (catId == null) this else map { list -> list.filter { it.categoryId == catId } }

    // Sum of the currently filtered/listed results. Mirrors the rest of the app's
    // convention of excluding CREDIT/EXCLUDED entries from spend totals.
    val filteredTotal: StateFlow<Double> = results
        .map { list -> list.filter { it.entryType == EntryType.EXPENSE }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun onQuery(q: String) { query.value = q }
    fun onCategorySelected(categoryId: Long?) {
        categoryFilter.value = if (categoryFilter.value == categoryId) null else categoryId
    }
    fun toggleRangeScope() { scopeToRange.value = !scopeToRange.value }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onResultClick: (Long) -> Unit = {}, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.currentQuery.collectAsState()
    val results by viewModel.results.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val filteredTotal by viewModel.filteredTotal.collectAsState()
    val isScopedToRange by viewModel.isScopedToRange.collectAsState()
    val focusRequester = remember { FocusRequester() }

    val hasFilter = query.isNotBlank() || selectedCategoryId != null

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        TextField(
                            value = query,
                            onValueChange = viewModel::onQuery,
                            placeholder = { Text("Search expenses, merchants, categories…") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            trailingIcon = {
                                if (query.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onQuery("") }) {
                                        Icon(Icons.Default.Clear, "Clear")
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
                )
                if (viewModel.hasRange) {
                    RangeScopeRow(
                        rangeLabel = viewModel.rangeLabel,
                        scoped = isScopedToRange,
                        onToggle = viewModel::toggleRangeScope
                    )
                }
                if (categories.isNotEmpty()) {
                    CategoryFilterBar(
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        onSelect = viewModel::onCategorySelected
                    )
                }
                Divider()
            }
        },
        bottomBar = {
            if (hasFilter && results.isNotEmpty()) {
                FilteredTotalBar(total = filteredTotal, count = results.size)
            }
        }
    ) { padding ->
        when {
            !hasFilter -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Type to search, or tap a category", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            results.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        val label = if (query.isNotBlank()) "No results for \"$query\"" else "No transactions in this category"
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        if (viewModel.hasRange && isScopedToRange) {
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = viewModel::toggleRangeScope) {
                                Text("Search entire data instead")
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text("${results.size} result(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                    }
                    items(results, key = { it.id }) { expense ->
                        val category = categories.firstOrNull { it.id == expense.categoryId }
                        SearchResultRow(expense, category, onClick = { onResultClick(expense.id) })
                    }
                }
            }
        }
    }
}

/** Shows the active date scope ("This Month") with a one-tap way to widen the search to
 *  all-time, and back again — instead of silently searching every transaction ever made. */
@Composable
private fun RangeScopeRow(rangeLabel: String, scoped: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (scoped) Icons.Default.DateRange else Icons.Default.Public,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (scoped) "Searching within $rangeLabel" else "Searching entire data",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onToggle) {
            Text(if (scoped) "Search entire data" else "Limit to $rangeLabel")
        }
    }
}

/** Compact, single-line category filter — tapping it opens a bottom sheet grid of every
 *  category (Samsung One UI-style "filter chip -> sheet" pattern) instead of permanently
 *  occupying screen space with a full wrapped chip list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterBar(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedCategoryId }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = { showSheet = true },
            label = { Text(selected?.let { "${it.emoji} ${it.name}" } ?: "All categories") },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        )
        if (selected != null) {
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { onSelect(null) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear category filter", modifier = Modifier.size(16.dp))
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Text(
                "Filter by category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                gridItems(categories, key = { it.id }) { category ->
                    CategoryGridCell(
                        category = category,
                        selected = category.id == selectedCategoryId,
                        onClick = {
                            onSelect(category.id)
                            showSheet = false
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CategoryGridCell(category: Category, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmojiAvatar(category.emoji, category.colorHex, sizeDp = 40)
        Spacer(Modifier.height(6.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FilteredTotalBar(total: Double, count: Int) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Total for $count transaction${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(formatCurrency(total), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = GivenColor)
        }
    }
}

@Composable
private fun SearchResultRow(expense: Expense, category: Category?, onClick: () -> Unit = {}) {
    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(expense.date))
    Card(onClick = onClick, shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (category != null) {
                EmojiAvatar(category.emoji, category.colorHex, sizeDp = 36)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row {
                    Text(expense.merchantName ?: "Expense", fontWeight = FontWeight.Medium)
                    if (expense.entryType != EntryType.EXPENSE) {
                        Text(
                            "  •  ${expense.entryType.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                expense.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row {
                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (category != null) {
                        Text(
                            "  •  ${category.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                formatCurrency(expense.amount),
                fontWeight = FontWeight.Bold,
                color = if (expense.entryType == EntryType.CREDIT) com.smartspend.ui.theme.ReceivedColor else GivenColor
            )
        }
    }
}
