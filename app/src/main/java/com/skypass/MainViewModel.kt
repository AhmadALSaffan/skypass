package com.skypass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.ObserveAppSettingsUseCase
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.domain.usecase.RefreshAccountSettingsUseCase
import com.skypass.core.domain.usecase.ResolveStartScreenUseCase
import com.skypass.core.model.AppSettings
import com.skypass.core.model.StartScreen
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The app shell: where it opens (holding the splash until known), the settings every screen
 * depends on (theme, currency), and the biometric lock.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    resolveStartScreen: Lazy<ResolveStartScreenUseCase>,
    observeSettings: Lazy<ObserveAppSettingsUseCase>,
    private val observeSession: Lazy<ObserveSessionUseCase>,
    private val refreshAccountSettings: Lazy<RefreshAccountSettingsUseCase>,
) : ViewModel() {

    private val _startScreen = MutableStateFlow<StartScreen?>(null)
    val startScreen: StateFlow<StartScreen?> = _startScreen.asStateFlow()

    private val _settings = MutableStateFlow<AppSettings?>(null)

    /** Null until the cached settings are read (a few ms), so the first frame has the right theme. */
    val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

    private val _locked = MutableStateFlow(false)

    /** True while the biometric lock is covering the app. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null

    init {
        // Everything Supabase-related is built off the main thread (see SkypassApplication).
        viewModelScope.launch {
            val settings = withContext(Dispatchers.Default) { observeSettings.get() }
            settings().collect { s ->
                if (_settings.value == null && s.device.biometricLock) _locked.value = true
                _settings.value = s
            }
        }
        viewModelScope.launch {
            _startScreen.value = withContext(Dispatchers.Default) { resolveStartScreen.get().invoke() }
        }
        // Each time someone signs in, pull their account settings (currency, notifications).
        viewModelScope.launch {
            val sessions = withContext(Dispatchers.Default) { observeSession.get().invoke() }
            sessions.map { it?.id }.distinctUntilChanged().filterNotNull().collect {
                refreshAccountSettings.get().invoke()
            }
        }
    }

    fun onBackground(now: Long) {
        backgroundedAt = now
    }

    /** Back after more than [LOCK_AFTER_MS] away: lock again if the traveller asked for it. */
    fun onForeground(now: Long) {
        val away = backgroundedAt?.let { now - it } ?: return
        backgroundedAt = null
        if (away > LOCK_AFTER_MS && _settings.value?.device?.biometricLock == true) _locked.value = true
    }

    fun unlocked() {
        _locked.value = false
    }

    private companion object {
        const val LOCK_AFTER_MS = 30_000L
    }
}
