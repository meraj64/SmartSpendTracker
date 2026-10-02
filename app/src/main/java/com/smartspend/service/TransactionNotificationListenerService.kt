package com.smartspend.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smartspend.util.SmsParser

/**
 * Catches push notifications from UPI / banking apps.
 *
 * Many modern banks (HDFC, ICICI, Axis) and UPI apps (GPay, PhonePe, Paytm) deliver
 * transaction alerts as push notifications rather than SMS.
 *
 * ⚠️  The user MUST grant this access manually:
 *     Settings → Apps → Special app access → Notification access → SmartSpend → Enable
 *
 * WHY THIS USED TO MISS NOTIFICATIONS
 * ------------------------------------
 * Two separate gaps caused real transaction alerts to be silently dropped:
 *
 * 1. A hard-coded whitelist of ~15 package names. Bank/UPI app package IDs vary by OEM
 *    build, get renamed across major app updates, and there are far more banks in India
 *    than any fixed list can keep up with (Union Bank, BoB, IDBI, RBL, Jupiter, Fi Money,
 *    Slice, WhatsApp Pay, dozens of regional/cooperative banks, etc). Any bank not in the
 *    list was invisible to the app no matter how well-formatted its alert was.
 *
 * 2. Only `android.text` / `android.bigText` were read. The *default* Messages app (and
 *    several OEM SMS apps) deliver a conversation thread using Android's MessagingStyle,
 *    which stores the actual message text in a structured `android.messages` extra instead
 *    — so an SMS arriving as a notification (rather than the SMS_RECEIVED broadcast) could
 *    have real content that this service never even looked at.
 *
 * Both are fixed below: every notification is now inspected (aside from a short exclusion
 * list of definitely-irrelevant system packages and our own app), and text is pulled from
 * every place Android might have put it. Whether something is actually treated as a
 * transaction still depends entirely on SmsParser's strict matching (a currency amount plus
 * a debit/credit keyword) — so this doesn't turn into "process every notification as
 * spending", it just stops silently ignoring ones that would otherwise have matched fine.
 */
class TransactionNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "TxNotifListener"
    }

    /** Packages where a tap-to-pay notification may omit the amount (user fills it in via "No, Edit"). */
    private val AMOUNT_OPTIONAL_PACKAGES = setOf(
        "com.samsung.android.spay",
        "com.samsung.android.spaysdk"
    )

    /**
     * Packages we never want to process — not because they can't send transaction alerts,
     * but because they structurally can't (system UI, launchers, our own app re-notifying
     * itself) or would otherwise cause obvious noise/duplication.
     */
    private val EXCLUDED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.googlequicksearchbox",
        "com.google.android.gms",         // Play Services system notifications (updates, etc.)
        "com.google.android.apps.nexuslauncher",
        "com.google.android.inputmethod.latin",
        "com.android.chrome",
        "com.android.vending"              // Play Store
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName ?: return
            if (packageName == applicationContext.packageName) return
            if (packageName in EXCLUDED_PACKAGES) return

            val text = extractNotificationText(sbn) ?: return
            val allowMissingAmount = packageName in AMOUNT_OPTIONAL_PACKAGES

            val parsed = SmsParser.parse(text.body, text.title.ifBlank { packageName }, allowMissingAmount)
            if (parsed == null) {
                Log.d(TAG, "No transaction match from $packageName")
                return
            }

            Log.d(TAG, "Transaction detected from $packageName: amount=${parsed.amount}, credit=${parsed.isCredit}")
            TransactionProcessingService.start(
                context = applicationContext,
                smsBody = text.body,
                sender = text.title.ifBlank { packageName },
                packageName = packageName,
                allowMissingAmount = allowMissingAmount
            )
        } catch (e: Exception) {
            // A malformed/unusual notification from some third-party app should never be able
            // to crash the listener — that would silently kill detection for every other app
            // until the user re-enables notification access.
            Log.e(TAG, "Error handling notification from ${sbn.packageName}: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No action needed
    }

    private data class NotificationText(val title: String, val body: String)

    /**
     * Pulls the most complete text out of a notification, trying every shape a bank/UPI/SMS
     * app might use, in order of how much content they typically carry:
     *   1. MessagingStyle conversations (`android.messages`) — used by the default Messages
     *      app and several OEM SMS apps, especially for anything after the first message in
     *      a thread.
     *   2. Big text (expanded notifications, most bank apps).
     *   3. Plain text.
     *   4. Inbox-style multi-line text.
     */
    private fun extractNotificationText(sbn: StatusBarNotification): NotificationText? {
        val extras = sbn.notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

        val messagingText = extractMessagingStyleText(extras)
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it.toString() }

        val body = messagingText ?: bigText ?: text ?: lines ?: return null
        if (body.isBlank()) return null
        return NotificationText(title, body)
    }

    /** Extracts the latest message text out of a NotificationCompat.MessagingStyle notification. */
    private fun extractMessagingStyleText(extras: Bundle): String? {
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        return messages.mapNotNull { msg ->
            (msg as? Bundle)?.getCharSequence("text")?.toString()
        }.filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { null }
    }
}
