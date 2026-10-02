package com.smartspend.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("smart_spend_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val CURRENCY = stringPreferencesKey("currency")
        val BUDGET_NOTIFS = booleanPreferencesKey("budget_notifications")
        val BILL_NOTIFS = booleanPreferencesKey("bill_notifications")
        val KHATA_NOTIFS = booleanPreferencesKey("khata_notifications")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val isDarkMode: Flow<Boolean?> = context.dataStore.data.map { it[Keys.DARK_MODE] }
    val currency: Flow<String> = context.dataStore.data.map { it[Keys.CURRENCY] ?: "INR" }
    val budgetNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BUDGET_NOTIFS] ?: true }
    val billNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BILL_NOTIFS] ?: true }
    val khataNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.KHATA_NOTIFS] ?: true }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setDarkMode(enabled: Boolean?) {
        context.dataStore.edit { prefs ->
            if (enabled == null) prefs.remove(Keys.DARK_MODE) else prefs[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun setCurrency(code: String) {
        context.dataStore.edit { it[Keys.CURRENCY] = code }
    }

    suspend fun setBudgetNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BUDGET_NOTIFS] = enabled }
    }

    suspend fun setBillNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BILL_NOTIFS] = enabled }
    }

    suspend fun setKhataNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KHATA_NOTIFS] = enabled }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }
}
