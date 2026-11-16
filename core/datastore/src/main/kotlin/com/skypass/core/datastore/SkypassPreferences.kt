package com.skypass.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Small local values: device-only settings, and caches of account settings for an instant start. */
data class CachedAccountSettings(
    val currency: String?,
    val push: Boolean?,
    val gateAlerts: Boolean?,
    val priceAlerts: Boolean?,
)

/** Small local flags. Settings that follow the user across devices live in Supabase instead. */
@Singleton
class SkypassPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete() {
        dataStore.edit { it[ONBOARDING_COMPLETE] = true }
    }

    /** Last known upcoming trip as JSON, so Home shows it offline. Null when there is none. */
    val cachedUpcomingTrip: Flow<String?> = dataStore.data.map { it[UPCOMING_TRIP] }

    suspend fun setCachedUpcomingTrip(json: String?) {
        dataStore.edit { if (json == null) it.remove(UPCOMING_TRIP) else it[UPCOMING_TRIP] = json }
    }

    /** "System", "Light" or "Dark". */
    val themeMode: Flow<String?> = dataStore.data.map { it[THEME_MODE] }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[THEME_MODE] = mode }
    }

    val biometricLock: Flow<Boolean> = dataStore.data.map { it[BIOMETRIC_LOCK] ?: false }

    suspend fun setBiometricLock(enabled: Boolean) {
        dataStore.edit { it[BIOMETRIC_LOCK] = enabled }
    }

    val cachedAccountSettings: Flow<CachedAccountSettings> = dataStore.data.map {
        CachedAccountSettings(it[CURRENCY], it[PUSH], it[GATE_ALERTS], it[PRICE_ALERTS])
    }

    suspend fun cacheAccountSettings(settings: CachedAccountSettings) {
        dataStore.edit { prefs ->
            settings.currency?.let { prefs[CURRENCY] = it }
            settings.push?.let { prefs[PUSH] = it }
            settings.gateAlerts?.let { prefs[GATE_ALERTS] = it }
            settings.priceAlerts?.let { prefs[PRICE_ALERTS] = it }
        }
    }

    /** On sign-out: the next account must not inherit this one's account settings. */
    suspend fun clearAccountCaches() {
        dataStore.edit { prefs ->
            listOf(UPCOMING_TRIP, CURRENCY).forEach { prefs.remove(it) }
            listOf(PUSH, GATE_ALERTS, PRICE_ALERTS).forEach { prefs.remove(it) }
        }
    }

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val UPCOMING_TRIP = stringPreferencesKey("upcoming_trip")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val CURRENCY = stringPreferencesKey("currency")
        val PUSH = booleanPreferencesKey("push_notifications")
        val GATE_ALERTS = booleanPreferencesKey("gate_alerts")
        val PRICE_ALERTS = booleanPreferencesKey("price_alerts")
    }
}
