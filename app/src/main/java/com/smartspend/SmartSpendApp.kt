package com.smartspend

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.android.libraries.places.api.Places
import com.smartspend.data.repository.CategoryRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SmartSpendApp : Application() {

    @Inject lateinit var categoryRepository: CategoryRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        android.util.Log.d("SmartSpend_KEY", "Key='${BuildConfig.PLACES_API_KEY}'")


        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, BuildConfig.PLACES_API_KEY)
        }

        createNotificationChannels()

        // Make sure the category picker (Add Expense, Budgets, etc.) always has something to
        // show — previously this only ran when the user opened the Categories screen, so a
        // fresh install showed an empty category dropdown everywhere else.
        applicationScope.launch { categoryRepository.seedDefaultsIfEmpty() }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_TRANSACTION, "Transaction Detected", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Prompts to confirm auto-detected transactions"
                    enableVibration(true)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROCESSING, "Processing Transaction", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Background location lookup for merchant detection"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_BUDGET, "Budget Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts when budget thresholds are crossed"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_BILLS, "Bill Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Upcoming and overdue bill reminders"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_KHATA, "Khata Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Settlement reminders for pending khata balances"
                }
            )
        }
    }

    companion object {
        const val CHANNEL_TRANSACTION = "channel_transaction_confirm"
        const val CHANNEL_PROCESSING  = "channel_processing"
        const val CHANNEL_BUDGET      = "channel_budget_alerts"
        const val CHANNEL_BILLS       = "channel_bill_reminders"
        const val CHANNEL_KHATA       = "channel_khata_reminders"
    }
}
