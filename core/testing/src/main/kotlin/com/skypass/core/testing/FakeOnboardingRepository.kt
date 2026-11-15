package com.skypass.core.testing

import com.skypass.core.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeOnboardingRepository(complete: Boolean = false) : OnboardingRepository {
    private val state = MutableStateFlow(complete)
    override val isComplete: StateFlow<Boolean> = state

    var markCompleteCalls = 0
        private set

    override suspend fun markComplete() {
        markCompleteCalls++
        state.value = true
    }
}
