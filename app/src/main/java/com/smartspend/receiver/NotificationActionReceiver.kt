package com.smartspend.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smartspend.data.db.AppDatabase
import com.smartspend.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles taps on the Confirm / Edit / Discard action buttons in the transaction confirmation
 * notification. The notification itself is a normal, swipe-dismissible one — see
 * NotificationHelper — since pending transactions now also live as "Action needed" cards on
 * the dashboard, so there's no need to pin or re-post it.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "NotifActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra("extra_transaction_id", -1L)
        if (transactionId == -1L) return

        val db = AppDatabase.getInstance(context)
        val transactionDao = db.transactionDao()
        val expenseDao = db.expenseDao()
        val categoryDao = db.categoryDao()
        // Plain (non-Hilt) construction — this receiver isn't a DI entry point, but
        // TransactionRepository is just a thin wrapper around these three DAOs, so we can build
        // it directly to share confirmAndCreateExpense() with the dashboard's "Action needed"
        // card instead of keeping two copies of the confirm logic in sync.
        val transactionRepo = com.smartspend.data.repository.TransactionRepository(transactionDao, expenseDao, categoryDao)
        val notifId = transactionId.toInt()

        when (intent.action) {
            NotificationHelper.ACTION_CONFIRM -> {
                scope.launch {
                    try {
                        transactionRepo.confirmAndCreateExpense(transactionId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error matching transaction confirmation to expense table: ${e.message}")
                    }
                }
                try {
                    NotificationHelper.dismissNotification(context, notifId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to dismiss notification (id=$notifId) after Confirm tap: ${e.message}")
                }
            }

            NotificationHelper.ACTION_DISMISS -> {
                scope.launch {
                    try {
                        transactionDao.dismissTransaction(transactionId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error dismissing transaction: ${e.message}")
                    }
                }
                try {
                    NotificationHelper.dismissNotification(context, notifId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to dismiss notification (id=$notifId) after Discard tap: ${e.message}")
                }
            }

            // "No, Edit" no longer comes through here at all. It used to: this receiver would
            // call context.startActivity(MainActivity) after being woken by the notification
            // action's PendingIntent. That receiver -> startActivity() hop is a "notification
            // trampoline", and on API 31+ Android silently blocks the activity launch when it's
            // triggered that way — no exception, nothing in the app's own logs, just no-op. The
            // fix was to make editIntent in NotificationHelper a direct
            // PendingIntent.getActivity() straight to MainActivity, with the notification
            // cleanup moved into MainActivity itself (see its handleIntent()), where it always
            // runs from the foreground.

            // ACTION_SWIPED is intentionally not handled here anymore. The transaction
            // confirmation notification used to be pinned as non-dismissible (via
            // PendingTransactionService) and re-posted itself if swiped away, which is exactly
            // the "keeps coming back" nagging behavior this was meant to prevent from being
            // avoidable. Now the notification is a normal, swipe-dismissible one — swiping it
            // away just clears it from the shade. The transaction stays pending either way and
            // remains visible as an "Action needed" card on the dashboard until the user
            // actually confirms, edits, or discards it from there (or from the notification's
            // buttons, while it's still showing).
        }
    }
}