package com.skypass.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** Whether the user has finished (or skipped through) onboarding. */
interface OnboardingRepository {
    val isComplete: Flow<Boolean>
    suspend fun markComplete()
}
