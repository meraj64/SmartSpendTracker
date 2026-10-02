package com.smartspend.work

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartspend.R
import com.smartspend.SmartSpendApp
import com.smartspend.data.models.Bill
import com.smartspend.data.repository.BillRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Runs once daily (scheduled via WorkManager periodic request).
 * Checks all unpaid bills and fires reminder notifications at:
 * 7 days before, 3 days before, 1 day before, due today, and overdue.
 */
@HiltWorker
class BillReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val billRepo: BillRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val unpaidBills = billRepo.unpaidBills

        // Collect first emission (one-shot daily check)
        var bills: List<Bill> = emptyList()
        unpaidBills.collect { bills = it; return@collect }

        bills.forEach { bill ->
            val daysRemaining = bill.daysRemaining(now)
            when (daysRemaining) {
                7L -> notify(bill, "Bill due in 7 days", "${bill.title} (${formatAmount(bill.amount)}) is due in a week")
                3L -> notify(bill, "Bill due in 3 days", "${bill.title} (${formatAmount(bill.amount)}) is due soon")
                1L -> notify(bill, "Bill due tomorrow", "${bill.title} (${formatAmount(bill.amount)}) is due tomorrow")
                0L -> notify(bill, "Bill due today", "${bill.title} (${formatAmount(bill.amount)}) is due today")
            }
            if (daysRemaining < 0) {
                notify(bill, "Overdue Bill", "${bill.title} is overdue by ${-daysRemaining} day(s)")
            }
        }

        return Result.success()
    }

    private fun notify(bill: Bill, title: String, message: String) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(applicationContext, SmartSpendApp.CHANNEL_BILLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${bill.type.emoji} $title")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(("bill_" + bill.id).hashCode(), notification)
    }

    private fun formatAmount(amount: Double) = "₹%.2f".format(amount)

    companion object {
        const val WORK_NAME = "bill_reminder_worker"
        const val INTERVAL_HOURS = 24L
    }
}
