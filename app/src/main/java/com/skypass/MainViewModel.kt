package com.skypass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.ResolveStartScreenUseCase
import com.skypass.core.model.StartScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Holds the splash screen until we know where the app opens. */
@HiltViewModel
class MainViewModel @Inject constructor(
    resolveStartScreen: ResolveStartScreenUseCase,
) : ViewModel() {

    private val _startScreen = MutableStateFlow<StartScreen?>(null)
    val startScreen: StateFlow<StartScreen?> = _startScreen.asStateFlow()

    init {
        viewModelScope.launch { _startScreen.value = resolveStartScreen() }
    }
}
