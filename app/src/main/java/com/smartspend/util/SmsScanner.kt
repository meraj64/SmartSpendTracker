package com.smartspend.util

import android.content.Context
import android.provider.Telephony
import android.util.Log

/** A single raw message read back from the device's SMS inbox. */
data class InboxSms(
    val sender: String,
    val body: String,
    val timestamp: Long
)

/**
 * Reads the device's SMS inbox (requires READ_SMS) for messages within [fromMillis, toMillis],
 * for use by the "Scan SMS for a date range" import feature. This is separate from
 * [SmsReceiver][com.smartspend.receiver.SmsReceiver], which only sees messages as they arrive live.
 */
object SmsScanner {

    private const val TAG = "SmsScanner"

    fun readInbox(context: Context, fromMillis: Long, toMillis: Long): List<InboxSms> {
        val results = mutableListOf<InboxSms>()

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection = "${Telephony.Sms.DATE} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(fromMillis.toString(), toMillis.toString())

        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val sender = if (addressIdx >= 0) cursor.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    if (body.isNotBlank()) {
                        results.add(InboxSms(sender = sender, body = body, timestamp = date))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read SMS inbox: ${e.message}")
        }

        return results
    }
}
