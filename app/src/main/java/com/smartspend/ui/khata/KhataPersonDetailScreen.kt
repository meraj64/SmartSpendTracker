package com.smartspend.ui.khata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartspend.data.models.KhataEntry
import com.smartspend.data.models.KhataEntryStatus
import com.smartspend.data.models.KhataEntryType
import com.smartspend.data.models.KhataPersonSummary
import com.smartspend.ui.common.formatCurrency
import com.smartspend.ui.theme.GivenColor
import com.smartspend.ui.theme.ReceivedColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhataPersonDetailScreen(
    personId: Long,
    onBack: () -> Unit,
    viewModel: KhataPersonDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(personId) { viewModel.load(personId) }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.summary?.person?.name ?: "Person Ledger") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.summary?.let { summary ->
                PersonBalanceHeader(summary)
            }

            if (state.entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Running balance timeline
                    var running = 0.0
                    val withBalance = state.entries.sortedBy { it.date }.map { entry ->
                        running += if (entry.type == KhataEntryType.MONEY_GIVEN) entry.amount else -entry.amount
                        entry to running
                    }.reversed()

                    items(withBalance, key = { it.first.id }) { (entry, balance) ->
                        KhataEntryRow(
                            entry = entry,
                            runningBalance = balance,
                            onMarkSettled = { viewModel.markSettled(entry.id) },
                            onDelete = { viewModel.deleteEntry(entry) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PersonBalanceHeader(summary: KhataPersonSummary) {
    val color = if (summary.balance >= 0) GivenColor else ReceivedColor
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            StatCol("Given", summary.totalGiven, GivenColor)
            StatCol("Received", summary.totalReceived, ReceivedColor)
            StatCol(
                if (summary.balance >= 0) "Owes You" else "You Owe",
                kotlin.math.abs(summary.balance),
                color
            )
        }
    }
}

@Composable
private fun StatCol(label: String, amount: Double, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatCurrency(amount, compact = true), fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KhataEntryRow(
    entry: KhataEntry,
    runningBalance: Double,
    onMarkSettled: () -> Unit,
    onDelete: () -> Unit
) {
    val isGiven = entry.type == KhataEntryType.MONEY_GIVEN
    val amountColor = if (isGiven) GivenColor else ReceivedColor
    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(entry.date))
    val settled = entry.status == KhataEntryStatus.SETTLED

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (settled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (settled) 0.dp else 1.dp)
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .size(40.dp)
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(if (isGiven) "↑" else "↓", fontWeight = FontWeight.Bold, color = amountColor)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.type.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                entry.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatCurrency(entry.amount), fontWeight = FontWeight.Bold, color = amountColor)
                Text(
                    "Bal: ${formatCurrency(kotlin.math.abs(runningBalance), compact = true)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            if (!settled) {
                IconButton(onClick = onMarkSettled, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Check, "Settle", tint = ReceivedColor, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}
