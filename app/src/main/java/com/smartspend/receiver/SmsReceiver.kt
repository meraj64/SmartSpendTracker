
package com.smartspend.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.smartspend.service.TransactionProcessingService
import com.smartspend.util.SmsParser

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called, action=${intent.action}")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages: Array<SmsMessage>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } else {
            // Fallback for very old devices
            @Suppress("DEPRECATION")
            val pdus = intent.extras?.get("pdus") as? Array<*>
            pdus?.mapNotNull { pdu ->
                @Suppress("DEPRECATION")
                SmsMessage.createFromPdu(pdu as ByteArray)
            }?.toTypedArray()
        }

        if (messages.isNullOrEmpty()) {
            Log.w(TAG, "No messages found in intent")
            return
        }

        // Combine multi-part SMS into single body
        val sender = messages[0].displayOriginatingAddress ?: ""
        val body = messages.joinToString("") { it.messageBody ?: "" }

        Log.d(TAG, "SMS from=$sender, body=$body")
        Log.d(TAG, SmsParser.debugParse(body, sender))

        val parsed = SmsParser.parse(body, sender)
        if (parsed != null) {
            Log.d(TAG, "Parsed: amount=${parsed.amount}, bank=${parsed.bankName}")
            // 🟢 FIXED: Changed method name to match the Service's companion object
            TransactionProcessingService.start(context, body, sender)
        } else {
            Log.d(TAG, "SMS did not match any transaction pattern")
        }
    }
}
