package com.skypass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.ResolveStartScreenUseCase
import com.skypass.core.model.StartScreen
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Holds the splash screen until we know where the app opens. The use case is lazy and resolved off
 * the main thread: building it builds the Supabase client, which shouldn't block the first frame.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    resolveStartScreen: Lazy<ResolveStartScreenUseCase>,
) : ViewModel() {

    private val _startScreen = MutableStateFlow<StartScreen?>(null)
    val startScreen: StateFlow<StartScreen?> = _startScreen.asStateFlow()

    init {
        viewModelScope.launch {
            _startScreen.value = withContext(Dispatchers.Default) { resolveStartScreen.get().invoke() }
        }
    }
}
