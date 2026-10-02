package com.smartspend.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.data.repository.BackupRepository
import com.smartspend.data.repository.BackupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Settings screen is currently showing feedback for, if anything. */
sealed class BackupUiState {
    object Idle : BackupUiState()
    object InProgress : BackupUiState()
    data class Done(val result: BackupResult, val wasImport: Boolean) : BackupUiState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupRepo: BackupRepository
) : ViewModel() {

    var uiState by mutableStateOf<BackupUiState>(BackupUiState.Idle)
        private set

    fun suggestedFileName(): String = backupRepo.suggestedFileName()

    fun backupTo(uri: Uri) {
        uiState = BackupUiState.InProgress
        viewModelScope.launch {
            val result = backupRepo.exportBackup(uri)
            uiState = BackupUiState.Done(result, wasImport = false)
        }
    }

    fun restoreFrom(uri: Uri) {
        uiState = BackupUiState.InProgress
        viewModelScope.launch {
            val result = backupRepo.importBackup(uri)
            uiState = BackupUiState.Done(result, wasImport = true)
        }
    }

    fun dismissFeedback() { uiState = BackupUiState.Idle }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.backupTo(it) } }

    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirm = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Save all your expenses, transactions, budgets, bills, and khata records to a file on " +
                    "your device, or bring them back from a previous backup. Everything stays local — " +
                    "nothing is uploaded anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingsActionCard(
                icon = Icons.Default.CloudUpload,
                title = "Backup now",
                subtitle = "Save all your data to a file you choose",
                buttonLabel = "Choose location",
                enabled = viewModel.uiState !is BackupUiState.InProgress,
                onClick = { createBackupLauncher.launch(viewModel.suggestedFileName()) }
            )

            SettingsActionCard(
                icon = Icons.Default.CloudDownload,
                title = "Restore from backup",
                subtitle = "Replace current data with a backup file",
                buttonLabel = "Choose backup file",
                enabled = viewModel.uiState !is BackupUiState.InProgress,
                onClick = { openBackupLauncher.launch(arrayOf("application/json")) }
            )

            if (viewModel.uiState is BackupUiState.InProgress) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Working…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    // ── Confirm before restoring — this replaces everything currently saved ──
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false; pendingRestoreUri = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Replace current data?") },
            text = {
                Text(
                    "Restoring this backup will replace ALL expenses, transactions, budgets, bills, " +
                        "and khata records currently on this device. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri?.let { viewModel.restoreFrom(it) }
                    showRestoreConfirm = false
                    pendingRestoreUri = null
                }) { Text("Replace data", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false; pendingRestoreUri = null }) { Text("Cancel") }
            }
        )
    }

    // ── Result feedback ──
    val state = viewModel.uiState
    if (state is BackupUiState.Done) {
        val result = state.result
        AlertDialog(
            onDismissRequest = viewModel::dismissFeedback,
            title = { Text(if (result is BackupResult.Success) "Success" else "Something went wrong") },
            text = {
                when (result) {
                    is BackupResult.Success -> Column {
                        Text(if (state.wasImport) "Your data has been restored:" else "Your data has been saved:")
                        Spacer(Modifier.height(8.dp))
                        result.counts.forEach { (label, count) ->
                            Text("• $label: $count", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is BackupResult.Failure -> Text(result.message)
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissFeedback) { Text("OK") }
            }
        )
    }
}

@Composable
private fun SettingsActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    buttonLabel: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth()) {
            Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(buttonLabel)
            }
        }
    }
}
