package com.skypass.core.domain.usecase

import com.skypass.core.domain.repository.AuthRepository
import com.skypass.core.domain.repository.OnboardingRepository
import com.skypass.core.model.StartScreen
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class CompleteOnboardingUseCase @Inject constructor(
    private val onboarding: OnboardingRepository,
) {
    suspend operator fun invoke() = onboarding.markComplete()
}

/** First launch → onboarding; signed in (Google or guest) → home; otherwise → sign in. */
class ResolveStartScreenUseCase @Inject constructor(
    private val onboarding: OnboardingRepository,
    private val auth: AuthRepository,
) {
    suspend operator fun invoke(): StartScreen = when {
        !onboarding.isComplete.first() -> StartScreen.Onboarding
        auth.currentSession() != null -> StartScreen.Home
        else -> StartScreen.SignIn
    }
}
