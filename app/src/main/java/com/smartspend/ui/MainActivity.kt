package com.smartspend.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import androidx.work.*
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.smartspend.ui.navigation.AppNavGraph
import com.smartspend.ui.navigation.BottomNavBar
import com.smartspend.ui.navigation.Screen
import com.smartspend.ui.theme.SmartSpendTheme
import com.smartspend.work.BillReminderWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results handled silently */ }

    /** Set when the activity is opened/resumed via the "No, Edit" notification action. */
    private var pendingEditTransactionId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()
        scheduleBillReminders()
        handleIntent(intent)

        setContent {
            SmartSpendTheme {
                SmartSpendApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Reads the notification's "No, Edit" extras and queues navigation into the edit flow. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("open_edit", false)) {
            val transactionId = intent.getLongExtra("extra_transaction_id", -1L)
            if (transactionId != -1L) {
                pendingEditTransactionId.value = transactionId
                // "No, Edit" is now a direct PendingIntent.getActivity() straight into
                // MainActivity (see NotificationHelper.editIntent) rather than a broadcast that
                // used to do this cleanup itself — routing an activity launch through a
                // BroadcastReceiver is a "notification trampoline" and gets silently blocked by
                // the OS on API 31+. So the notification teardown that used to happen in
                // NotificationActionReceiver's ACTION_EDIT branch happens here instead, now that
                // we're actually in the foreground.
                com.smartspend.util.NotificationHelper.dismissNotification(
                    this, transactionId.toInt()
                )
            }
        }
    }

    @Composable
    private fun SmartSpendApp() {
        val navController = rememberNavController()

        // Make system bars transparent for edge-to-edge
        val systemUiController = rememberSystemUiController()
        SideEffect {
            systemUiController.setSystemBarsColor(color = Color.Transparent, darkIcons = true)
        }

        val pendingTransactionId by pendingEditTransactionId
        LaunchedEffect(pendingTransactionId) {
            pendingTransactionId?.let { transactionId ->
                navController.navigate(Screen.AddExpense.fromTransaction(transactionId))
                pendingEditTransactionId.value = null
            }
        }

        Scaffold(
            bottomBar = { BottomNavBar(navController) }
        ) { innerPadding ->
            Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                AppNavGraph(
                    navController = navController
                )
            }
        }
    }

    private fun requestPermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun scheduleBillReminders() {
        val request = PeriodicWorkRequestBuilder<BillReminderWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BillReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
