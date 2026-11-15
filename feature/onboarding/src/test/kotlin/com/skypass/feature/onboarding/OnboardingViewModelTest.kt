package com.skypass.feature.onboarding

import app.cash.turbine.test
import com.skypass.core.domain.usecase.CompleteOnboardingUseCase
import com.skypass.core.testing.FakeOnboardingRepository
import com.skypass.core.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class OnboardingViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeOnboardingRepository()
    private val viewModel = OnboardingViewModel(CompleteOnboardingUseCase(repository))

    @Test
    fun `starts on the first slide`() {
        assertEquals(0, viewModel.uiState.value.currentPage)
        assertFalse(viewModel.uiState.value.isLastPage)
    }

    @Test
    fun `next walks through the slides`() {
        viewModel.onEvent(OnboardingEvent.Primary)
        assertEquals(1, viewModel.uiState.value.currentPage)
        viewModel.onEvent(OnboardingEvent.Primary)
        assertEquals(2, viewModel.uiState.value.currentPage)
        assertTrue(viewModel.uiState.value.isLastPage)
    }

    @Test
    fun `skip jumps to the last slide without finishing`() = runTest {
        viewModel.onEvent(OnboardingEvent.Skip)
        assertEquals(2, viewModel.uiState.value.currentPage)
        assertFalse(repository.isComplete.first())
    }

    @Test
    fun `back and dots stay within bounds`() {
        viewModel.onEvent(OnboardingEvent.Back)
        assertEquals(0, viewModel.uiState.value.currentPage)
        viewModel.onEvent(OnboardingEvent.DotClicked(7))
        assertEquals(2, viewModel.uiState.value.currentPage)
    }

    @Test
    fun `get started marks onboarding complete and finishes once`() = runTest {
        viewModel.onEvent(OnboardingEvent.Skip)
        viewModel.effects.test {
            viewModel.onEvent(OnboardingEvent.Primary)
            viewModel.onEvent(OnboardingEvent.Primary)
            assertEquals(OnboardingEffect.Finished, awaitItem())
            expectNoEvents()
        }
        assertEquals(1, repository.markCompleteCalls)
        assertTrue(repository.isComplete.first())
    }
}
