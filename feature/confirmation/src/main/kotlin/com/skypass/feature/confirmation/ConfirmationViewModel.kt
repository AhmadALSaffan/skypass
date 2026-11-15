package com.skypass.feature.confirmation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.GetBoardingPassesUseCase
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.model.BoardingPass
import com.skypass.core.model.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConfirmationUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val passes: List<BoardingPass> = emptyList(),
    val email: String? = null,
    /** The print animation plays once; coming back to the screen shows the passes straight away. */
    val printed: Boolean = false,
) {
    val pnrs: List<String> get() = passes.map { it.pnr }.distinct()
}

@HiltViewModel
class ConfirmationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getBoardingPasses: GetBoardingPassesUseCase,
    private val observeSession: ObserveSessionUseCase,
) : ViewModel() {

    private val bookingIds: List<String> = checkNotNull<String>(savedStateHandle["bookingIds"]).split(",").filter { it.isNotBlank() }

    private val _uiState = MutableStateFlow(ConfirmationUiState(printed = savedStateHandle[PRINTED] ?: false))
    val uiState: StateFlow<ConfirmationUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun onPrinted() {
        savedStateHandle[PRINTED] = true
        _uiState.update { it.copy(printed = true) }
    }

    private fun load() {
        _uiState.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val email = observeSession().first()?.email
            when (val result = getBoardingPasses(bookingIds)) {
                is Outcome.Success -> _uiState.update { it.copy(loading = false, passes = result.value, email = email) }
                is Outcome.Failure -> _uiState.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    private companion object {
        const val PRINTED = "printed"
    }
}
