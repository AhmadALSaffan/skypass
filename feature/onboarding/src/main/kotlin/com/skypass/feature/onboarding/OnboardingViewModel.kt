package com.skypass.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val currentPage: Int = 0,
    val pageCount: Int = PAGE_COUNT,
    val finishing: Boolean = false,
) {
    val isLastPage: Boolean get() = currentPage == pageCount - 1

    companion object {
        const val PAGE_COUNT = 3
    }
}

sealed interface OnboardingEvent {
    /** "Next" on slides 1–2, "Get started" on the last slide. */
    data object Primary : OnboardingEvent
    data object Back : OnboardingEvent
    data object Skip : OnboardingEvent
    data class DotClicked(val page: Int) : OnboardingEvent
    data class PageSettled(val page: Int) : OnboardingEvent
}

sealed interface OnboardingEffect {
    data object Finished : OnboardingEffect
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.Primary -> if (_uiState.value.isLastPage) finish() else goTo(_uiState.value.currentPage + 1)
            OnboardingEvent.Back -> goTo(_uiState.value.currentPage - 1)
            // Skip lands on the last slide (as on the board) so the promise of offline passes is still seen.
            OnboardingEvent.Skip -> goTo(_uiState.value.pageCount - 1)
            is OnboardingEvent.DotClicked -> goTo(event.page)
            is OnboardingEvent.PageSettled -> goTo(event.page)
        }
    }

    private fun finish() {
        if (_uiState.value.finishing) return
        _uiState.update { it.copy(finishing = true) }
        viewModelScope.launch {
            completeOnboarding()
            _effects.send(OnboardingEffect.Finished)
        }
    }

    private fun goTo(page: Int) {
        _uiState.update { it.copy(currentPage = page.coerceIn(0, it.pageCount - 1)) }
    }
}
