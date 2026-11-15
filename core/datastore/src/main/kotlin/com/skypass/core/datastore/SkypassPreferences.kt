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

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val UPCOMING_TRIP = stringPreferencesKey("upcoming_trip")
    }
}
