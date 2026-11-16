package com.skypass.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.GetAirportsUseCase
import com.skypass.core.domain.usecase.GetProfileUseCase
import com.skypass.core.domain.usecase.ObserveAppSettingsUseCase
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.domain.usecase.SetBiometricLockUseCase
import com.skypass.core.domain.usecase.SetThemeModeUseCase
import com.skypass.core.domain.usecase.SignOutUseCase
import com.skypass.core.domain.usecase.UpdateAccountSettingsUseCase
import com.skypass.core.domain.usecase.UpdateHomeAirportUseCase
import com.skypass.core.model.Airport
import com.skypass.core.model.AppSettings
import com.skypass.core.model.DisplayCurrency
import com.skypass.core.model.Outcome
import com.skypass.core.model.ThemeMode
import com.skypass.core.model.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The sheets Settings opens. */
sealed interface SettingsSheet {
    data object Currency : SettingsSheet
    data object Appearance : SettingsSheet
    data class HomeAirport(val query: String = "", val results: List<Airport> = emptyList()) : SettingsSheet
    data object ConnectedAccounts : SettingsSheet
    /** Guests lose their trips when they sign out, so they're asked first. */
    data object ConfirmGuestSignOut : SettingsSheet
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val session: UserSession? = null,
    val homeAirport: String? = null,
    val sheet: SettingsSheet? = null,
    /** A change the server refused (offline): the switch went back. */
    val saveFailed: Boolean = false,
    val signingOut: Boolean = false,
) {
    val isGuest: Boolean get() = session?.isGuest == true
}

sealed interface SettingsEvent {
    data class SetPush(val enabled: Boolean) : SettingsEvent
    data class SetGateAlerts(val enabled: Boolean) : SettingsEvent
    data class SetPriceAlerts(val enabled: Boolean) : SettingsEvent
    data object OpenCurrency : SettingsEvent
    data class CurrencyPicked(val currency: DisplayCurrency) : SettingsEvent
    data object OpenAppearance : SettingsEvent
    data class ThemePicked(val mode: ThemeMode) : SettingsEvent
    data object OpenHomeAirport : SettingsEvent
    data class AirportQueryChanged(val text: String) : SettingsEvent
    data class AirportPicked(val airport: Airport) : SettingsEvent
    data object OpenConnectedAccounts : SettingsEvent
    /** Sent once the person has proven it's them (turning the lock on or off). */
    data class SetBiometricLock(val enabled: Boolean) : SettingsEvent
    data object DismissSheet : SettingsEvent
    data object DismissSaveFailed : SettingsEvent
    data object SignOut : SettingsEvent
    data object ConfirmSignOut : SettingsEvent
}

sealed interface SettingsEffect {
    data object SignedOut : SettingsEffect
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveAppSettingsUseCase,
    observeSession: ObserveSessionUseCase,
    private val updateAccount: UpdateAccountSettingsUseCase,
    private val setThemeMode: SetThemeModeUseCase,
    private val setBiometricLock: SetBiometricLockUseCase,
    private val updateHomeAirport: UpdateHomeAirportUseCase,
    private val getProfile: GetProfileUseCase,
    private val getAirports: GetAirportsUseCase,
    private val searchAirports: SearchAirportsUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var airports: List<Airport> = emptyList()

    init {
        viewModelScope.launch { observeSettings().collect { s -> _uiState.update { it.copy(settings = s) } } }
        viewModelScope.launch { observeSession().collect { s -> _uiState.update { it.copy(session = s) } } }
        viewModelScope.launch {
            (getProfile() as? Outcome.Success)?.value?.let { p -> _uiState.update { it.copy(homeAirport = p.homeAirport) } }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SetPush -> saveAccount { updateAccount.notifications { n -> n.copy(push = event.enabled) } }
            is SettingsEvent.SetGateAlerts -> saveAccount { updateAccount.notifications { n -> n.copy(gateAlerts = event.enabled) } }
            is SettingsEvent.SetPriceAlerts -> saveAccount { updateAccount.notifications { n -> n.copy(priceAlerts = event.enabled) } }
            SettingsEvent.OpenCurrency -> _uiState.update { it.copy(sheet = SettingsSheet.Currency) }
            is SettingsEvent.CurrencyPicked -> {
                _uiState.update { it.copy(sheet = null) }
                saveAccount { updateAccount.currency(event.currency) }
            }
            SettingsEvent.OpenAppearance -> _uiState.update { it.copy(sheet = SettingsSheet.Appearance) }
            is SettingsEvent.ThemePicked -> {
                _uiState.update { it.copy(sheet = null) }
                viewModelScope.launch { setThemeMode(event.mode) }
            }
            SettingsEvent.OpenHomeAirport -> viewModelScope.launch {
                if (airports.isEmpty()) airports = (getAirports() as? Outcome.Success)?.value.orEmpty()
                _uiState.update { it.copy(sheet = SettingsSheet.HomeAirport(results = searchAirports(airports, ""))) }
            }
            is SettingsEvent.AirportQueryChanged -> _uiState.update { s ->
                if (s.sheet is SettingsSheet.HomeAirport) s.copy(sheet = SettingsSheet.HomeAirport(event.text, searchAirports(airports, event.text))) else s
            }
            is SettingsEvent.AirportPicked -> {
                val before = _uiState.value.homeAirport
                _uiState.update { it.copy(sheet = null, homeAirport = event.airport.code) }
                viewModelScope.launch {
                    if (updateHomeAirport(event.airport.code) is Outcome.Failure) _uiState.update { it.copy(homeAirport = before, saveFailed = true) }
                }
            }
            SettingsEvent.OpenConnectedAccounts -> _uiState.update { it.copy(sheet = SettingsSheet.ConnectedAccounts) }
            is SettingsEvent.SetBiometricLock -> viewModelScope.launch { setBiometricLock(event.enabled) }
            SettingsEvent.DismissSheet -> _uiState.update { it.copy(sheet = null) }
            SettingsEvent.DismissSaveFailed -> _uiState.update { it.copy(saveFailed = false) }
            SettingsEvent.SignOut -> if (_uiState.value.isGuest) _uiState.update { it.copy(sheet = SettingsSheet.ConfirmGuestSignOut) } else signOutNow()
            SettingsEvent.ConfirmSignOut -> signOutNow()
        }
    }

    private fun saveAccount(block: suspend () -> Outcome<Unit, *>) {
        viewModelScope.launch {
            if (block() is Outcome.Failure) _uiState.update { it.copy(saveFailed = true) }
        }
    }

    private fun signOutNow() {
        if (_uiState.value.signingOut) return
        _uiState.update { it.copy(signingOut = true, sheet = null) }
        viewModelScope.launch {
            signOut()
            _effects.send(SettingsEffect.SignedOut)
        }
    }
}
