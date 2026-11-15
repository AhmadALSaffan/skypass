package com.skypass.core.data.repository

import com.skypass.core.datastore.SkypassPreferences
import com.skypass.core.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class LocalOnboardingRepository @Inject constructor(
    private val preferences: SkypassPreferences,
) : OnboardingRepository {
    override val isComplete: Flow<Boolean> = preferences.onboardingComplete
    override suspend fun markComplete() = preferences.setOnboardingComplete()
}
