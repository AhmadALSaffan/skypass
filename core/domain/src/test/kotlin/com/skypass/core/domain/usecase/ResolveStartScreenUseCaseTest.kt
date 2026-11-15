package com.skypass.core.domain.usecase

import com.skypass.core.model.StartScreen
import com.skypass.core.testing.FakeAuthRepository
import com.skypass.core.testing.FakeOnboardingRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResolveStartScreenUseCaseTest {

    @Test
    fun `first launch shows onboarding even if a session exists`() = runTest {
        val useCase = ResolveStartScreenUseCase(FakeOnboardingRepository(complete = false), FakeAuthRepository(FakeAuthRepository.TINA))
        assertEquals(StartScreen.Onboarding, useCase())
    }

    @Test
    fun `signed out after onboarding shows sign in`() = runTest {
        val useCase = ResolveStartScreenUseCase(FakeOnboardingRepository(complete = true), FakeAuthRepository())
        assertEquals(StartScreen.SignIn, useCase())
    }

    @Test
    fun `restored session opens home, guests included`() = runTest {
        assertEquals(
            StartScreen.Home,
            ResolveStartScreenUseCase(FakeOnboardingRepository(true), FakeAuthRepository(FakeAuthRepository.TINA))(),
        )
        assertEquals(
            StartScreen.Home,
            ResolveStartScreenUseCase(FakeOnboardingRepository(true), FakeAuthRepository(FakeAuthRepository.GUEST))(),
        )
    }
}
