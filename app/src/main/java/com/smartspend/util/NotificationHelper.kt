package com.smartspend.util

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smartspend.R
import com.smartspend.SmartSpendApp
import com.smartspend.data.models.Transaction
import com.smartspend.receiver.NotificationActionReceiver
import com.smartspend.ui.MainActivity

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    private const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
    const val ACTION_CONFIRM = "com.smartspend.ACTION_CONFIRM_TRANSACTION"
    // No ACTION_EDIT here anymore — "No, Edit" is now a direct PendingIntent.getActivity()
    // (see editIntent below) rather than a broadcast, so NotificationActionReceiver no longer
    // has, or needs, an edit action to handle. Keeping an unused broadcast action around for it
    // would just invite someone to wire it back up into a trampoline by mistake.
    const val ACTION_DISMISS = "com.smartspend.ACTION_DISMISS_TRANSACTION"

    /**
     * Shows a one-time, normal (swipe-dismissible) notification for a newly-detected pending
     * transaction.
     *
     * This used to be pinned open via a dedicated foreground service and re-posted itself the
     * instant it was swiped away, so it kept "coming back" until Confirm/Edit/Discard was
     * tapped. That's deliberately gone now: every pending transaction also shows as a
     * persistent "Action needed" card on the dashboard's transaction list (see
     * DashboardViewModel/DashboardScreen), so the notification only needs to surface the
     * detection once — the dashboard card is what stays around until the user actually
     * resolves it, not a notification that won't take no for an answer.
     */
    fun showTransactionConfirmNotification(context: Context, transaction: Transaction) {
        val notifId = transaction.id.toInt()
        val notification = buildTransactionConfirmNotification(context, transaction)
        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+) — the transaction is still saved as
            // pending and will show up as an "Action needed" card on the dashboard regardless.
            Log.w(TAG, "Notification permission not granted, skipping notify(): ${e.message}")
        }
    }

    private fun buildTransactionConfirmNotification(context: Context, transaction: Transaction): Notification {
        val notifId = transaction.id.toInt()

        val merchantDisplay = transaction.merchantName ?: "an unknown merchant"
        val amountStr = SmsParser.formatAmount(transaction.amount)
        val categoryEmoji = transaction.category.emoji
        // Tapping notification opens the main app
        val openAppIntent = PendingIntent.getActivity(
            context, notifId * 10,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TRANSACTION_ID, transaction.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Yes, Confirm" button
        val confirmIntent = PendingIntent.getBroadcast(
            context, notifId * 10 + 1,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_CONFIRM
                putExtra(EXTRA_TRANSACTION_ID, transaction.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "No, Edit" button — opens the app straight to the edit screen.
        //
        // This MUST be a direct PendingIntent.getActivity(), not a broadcast to
        // NotificationActionReceiver that then calls context.startActivity(). That
        // receiver -> startActivity() indirection is a "notification trampoline", and on
        // API 31+ the OS silently blocks the activity launch when it's triggered that way —
        // no exception, no crash, just nothing happening (logcat shows a line like
        // "Indirect notification activity start (trampoline) from <pkg> blocked", which is
        // easy to miss if you're only watching for a Java stack trace).
        //
        // Cleanup that used to live in NotificationActionReceiver's ACTION_EDIT branch now
        // happens inside MainActivity once it's actually opened — i.e. from the foreground.
        val editIntent = PendingIntent.getActivity(
            context, notifId * 10 + 2,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TRANSACTION_ID, transaction.id)
                putExtra("open_edit", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Discard button — actually removes/dismisses the pending transaction.
        val dismissIntent = PendingIntent.getBroadcast(
            context, notifId * 10 + 3,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_TRANSACTION_ID, transaction.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, SmartSpendApp.CHANNEL_TRANSACTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Normal, swipe-dismissible notification — not pinned/ongoing. The pending
            // transaction itself (and the "Action needed" card for it on the dashboard) is
            // unaffected by dismissing this; only Confirm/Discard change its state, and Edit
            // just navigates to the same form the dashboard card's Edit action opens.
            .setOngoing(false)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentIntent(openAppIntent)

        val notification = if (transaction.isAmountMissing) {
            // Samsung Wallet-style tap-to-pay: no amount was in the notification text,
            // so there's nothing to "confirm" — only edit-to-fill-in or discard.
            notificationBuilder
                .setContentTitle("$categoryEmoji Payment detected — amount unknown")
                .setContentText("Paid at $merchantDisplay. Tap to add the amount.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "A payment at $merchantDisplay was detected, but the amount wasn't in the notification.\n" +
                        "Tap 'No, Edit' to fill it in, or Discard to ignore it. It'll also stay listed under " +
                        "\"Action needed\" on your home screen either way."
                    )
                )
                .addAction(R.drawable.ic_edit,  "✎ No, Edit", editIntent)
                .addAction(R.drawable.ic_close, "✕ Discard",  dismissIntent)
                .build()
        } else {
            notificationBuilder
                .setContentTitle("$categoryEmoji Transaction Detected")
                .setContentText("Did you spend $amountStr at $merchantDisplay?")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Did you spend $amountStr at $merchantDisplay?\n" +
                        "${transaction.category.displayName} • ${transaction.bankName ?: "Unknown bank"}"
                    )
                )
                .addAction(R.drawable.ic_check, "✓ Yes, Confirm", confirmIntent)
                .addAction(R.drawable.ic_edit,  "✎ No, Edit",     editIntent)
                .addAction(R.drawable.ic_close, "✕ Discard",      dismissIntent)
                .build()
        }

        return notification
    }

    fun buildProcessingNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, SmartSpendApp.CHANNEL_PROCESSING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SmartSpend")
            .setContentText("Detecting transaction location…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun cancelNotification(context: Context, notifId: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(notifId)
    }

    /** Removes a pending-transaction notification — call this from Confirm/Edit/Discard. */
    fun dismissNotification(context: Context, notifId: Int) {
        NotificationManagerCompat.from(context).cancel(notifId)
    }
}
