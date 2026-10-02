package com.smartspend.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartspend.ui.dashboard.TimeFilter
import java.util.Calendar

// Shared Month/Year/Range filter bar + its range-picker dialog — originally built for the
// Dashboard, now reused by any screen (Reports, etc.) that needs the same time-window
// picker so the UX stays consistent across the app.

/** Which UI mode the filter bar is in — determines what secondary controls are shown. */
enum class FilterMode { MONTH, YEAR, RANGE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeFilterBar(filter: TimeFilter, onFilterChange: (TimeFilter) -> Unit) {
    val activeMode = when (filter) {
        is TimeFilter.Month -> FilterMode.MONTH
        is TimeFilter.Year -> FilterMode.YEAR
        is TimeFilter.Range -> FilterMode.RANGE
    }
    var showRangePicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = activeMode == FilterMode.MONTH,
                onClick = { onFilterChange(TimeFilter.currentMonth()) },
                label = { Text("Month") }
            )
            FilterChip(
                selected = activeMode == FilterMode.YEAR,
                onClick = { onFilterChange(TimeFilter.Year(Calendar.getInstance().get(Calendar.YEAR))) },
                label = { Text("Year") }
            )
            FilterChip(
                selected = activeMode == FilterMode.RANGE,
                onClick = { showRangePicker = true },
                label = { Text("Range") },
                leadingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp)) }
            )
        }

        Spacer(Modifier.height(6.dp))

        // ── Secondary control: prev/next for Month & Year, a re-open button for Range ──
        when (activeMode) {
            FilterMode.RANGE -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        filter.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showRangePicker = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Change")
                    }
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onFilterChange(filter.previousPeriod()) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, "Previous")
                    }
                    Text(
                        filter.label(),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = { onFilterChange(filter.nextPeriod()) },
                        enabled = !filter.isCurrentOrFuture(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, "Next")
                    }
                }
            }
        }
    }

    if (showRangePicker) {
        val existingRange = filter as? TimeFilter.Range
        RangePickerDialog(
            initialStart = existingRange?.start,
            initialEnd = existingRange?.end?.minus(1),
            onDismiss = { showRangePicker = false },
            onConfirm = { from, to ->
                val (start, end) = com.smartspend.data.repository.ExpenseRepository.dayInclusiveRange(from, to)
                onFilterChange(TimeFilter.Range(start, end))
                showRangePicker = false
            }
        )
    }
}

/** Two-step date picker: pick a "from" date, then a "to" date, then confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangePickerDialog(
    initialStart: Long?,
    initialEnd: Long?,
    onDismiss: () -> Unit,
    onConfirm: (from: Long, to: Long) -> Unit
) {
    var pickingFrom by remember { mutableStateOf(true) }
    var fromDate by remember { mutableStateOf(initialStart) }
    var toDate by remember { mutableStateOf(initialEnd) }

    if (pickingFrom) {
        val state = rememberDatePickerState(initialSelectedDateMillis = fromDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    fromDate = state.selectedDateMillis
                    pickingFrom = false
                }) { Text("Next: pick end date") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) {
            Column {
                Text(
                    "Start date",
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    style = MaterialTheme.typography.labelLarge
                )
                DatePicker(state = state)
            }
        }
    } else {
        val state = rememberDatePickerState(initialSelectedDateMillis = toDate ?: fromDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val from = fromDate
                    val to = state.selectedDateMillis
                    if (from != null && to != null) onConfirm(from, to)
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { pickingFrom = true }) { Text("Back") } }
        ) {
            Column {
                Text(
                    "End date",
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    style = MaterialTheme.typography.labelLarge
                )
                DatePicker(state = state)
            }
        }
    }
}
