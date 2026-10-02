package com.smartspend.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import com.smartspend.data.db.AppDatabase
import com.smartspend.data.models.Transaction
import com.smartspend.data.models.TransactionSource
import com.smartspend.receiver.SmsReceiver
import com.smartspend.util.LocationHelper
import com.smartspend.util.NotificationHelper
import com.smartspend.util.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Background Service running when an SMS is intercepted.
 * Coordinates Location fetching, Google Places SDK lookup, and database mirroring pipelines.
 */
class TransactionProcessingService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        private const val TAG = "TxProcessingService"

        fun start(
            context: Context,
            smsBody: String,
            sender: String,
            packageName: String? = null,
            allowMissingAmount: Boolean = false
        ) {
            val intent = Intent(context, TransactionProcessingService::class.java).apply {
                putExtra("extra_sms_body", smsBody)
                putExtra("extra_sender", sender)
                putExtra("extra_package_name", packageName)
                putExtra("extra_allow_missing_amount", allowMissingAmount)
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val body = intent?.getStringExtra("extra_sms_body")
        val sender = intent?.getStringExtra("extra_sender")
        val packageName = intent?.getStringExtra("extra_package_name")
        val allowMissingAmount = intent?.getBooleanExtra("extra_allow_missing_amount", false) ?: false

        if (body != null && sender != null) {
            scope.launch {
                processPipeline(applicationContext, body, sender, packageName, allowMissingAmount)
                stopSelf(startId) // Clean closure once asynchronous workflow satisfies processing
            }
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private suspend fun processPipeline(
        context: Context,
        smsBody: String,
        sender: String,
        packageName: String?,
        allowMissingAmount: Boolean
    ) {
        // Step 1: Execute SMS Regex Tokenizer Engines
        val parsed = SmsParser.parse(smsBody, sender, allowMissingAmount) ?: return
        Log.d(TAG, "Successfully parsed transaction text token: Amount=${parsed.amount}, Hint=${parsed.merchantHint}")

        // Step 2: Extract current background device coordinates
        val location: Location? = LocationHelper.getCurrentLocation(context)
        if (location != null) {
            Log.d(TAG, "Pipeline acquired device metadata coordinates: Lat=${location.latitude}, Lng=${location.longitude}")
        } else {
            Log.w(TAG, "Location capture context returned empty. Skipping Google Places mapping.")
        }

        // 🟢 FIXED HERE: Re-scoped variables cleanly away from name shadowing conflicts
        // 🟢 FIXED HERE: Removed parsed.category and replaced with your default enum fallback
        var finalMerchantName = parsed.merchantHint ?: "SMS Expense"
        var finalMerchantAddress: String? = null
        var finalMerchantPlaceId: String? = null

        // Use whatever your default/fallback enum is named (e.g., OTHER, UNKNOWN, or UNCATEGORIZED)
        var finalCategory = com.smartspend.data.models.TransactionCategory.OTHER

        if (location != null) {
            try {
                // Trigger the Google Places SDK to find nearby business venues
                val nearbyPlaces = LocationHelper.findNearbyPlaces(
                    context = applicationContext,
                    location = location,
                    merchantHint = parsed.merchantHint
                )

                // Cross-match raw string tokens to map exact merchant data
                val bestMerchant = LocationHelper.pickBestMerchant(nearbyPlaces, parsed.merchantHint)
                if (bestMerchant != null) {
                    finalMerchantName = bestMerchant.name
                    finalMerchantAddress = bestMerchant.address
                    finalMerchantPlaceId = bestMerchant.placeId
                    finalCategory = bestMerchant.category
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error matching Google Places API resolution inside pipeline: ${e.message}")
            }
        }

        // Step 3: Save to Room Database Architecture
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.transactionDao()

        // Dedup guard: the same real-world payment is often detected twice — once from the
        // bank's SMS and again a few seconds/minutes later from the UPI app's own
        // notification (or vice versa). Without this check both would land as separate
        // pending rows, each with its own "Action needed" card and notification, for what
        // is really a single transaction.
        val dedupWindowMs = 10 * 60 * 1000L // 10 minutes either side
        val existingPending = dao.findPendingDuplicate(
            amount = parsed.amount,
            from = System.currentTimeMillis() - dedupWindowMs,
            to = System.currentTimeMillis() + dedupWindowMs
        )
        if (existingPending != null) {
            Log.d(TAG, "Skipping duplicate pending transaction — matches existing id=${existingPending.id} " +
                    "amount=${parsed.amount}")
            return
        }

        val transaction = Transaction(
            amount          = parsed.amount,
            merchantName    = finalMerchantName,
            merchantPlaceId = finalMerchantPlaceId,
            merchantAddress = finalMerchantAddress,
            category        = finalCategory,
            latitude        = location?.latitude,
            longitude       = location?.longitude,
            rawSmsBody      = parsed.rawBody,
            bankName        = parsed.bankName,
            referenceNumber = parsed.referenceNumber,
            source          = if (packageName != null) TransactionSource.NOTIFICATION else TransactionSource.SMS,
            isConfirmed     = false,
            isPending       = true,
            isAmountMissing = parsed.isAmountMissing,
            isCredit        = parsed.isCredit
        )

        val savedId = dao.insert(transaction)
        val savedTransaction = dao.getById(savedId) ?: return

        // NOTE: We intentionally do NOT create an Expense here. This transaction is only
        // a *pending* detection — the actual Expense is created when the user taps
        // "Yes, Confirm" (NotificationActionReceiver) or edits & saves it (AddExpenseScreen).
        // Creating it here as well used to silently double-record every detected transaction
        // and made "Discard" a no-op, since the expense already existed by the time the
        // notification was even shown.

        // Step 4: Raise transaction approval user notifications
        NotificationHelper.showTransactionConfirmNotification(context, savedTransaction)
        Log.d(TAG, "Notification shown for txn id=$savedId amount=${parsed.amount}")
    }

    override fun onDestroy() {
        job.cancel() // Safe teardown cleanup processing
        super.onDestroy()
    }
}