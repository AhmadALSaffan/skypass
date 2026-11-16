package com.skypass.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.GetAirportsUseCase
import com.skypass.core.domain.usecase.GetProfileOverviewUseCase
import com.skypass.core.domain.usecase.ObserveSavedCountUseCase
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.domain.usecase.ProfileOverview
import com.skypass.core.domain.usecase.ProfileProblem
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.domain.usecase.UpdateProfileUseCase
import com.skypass.core.model.Airport
import com.skypass.core.model.AppError
import com.skypass.core.model.Outcome
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

/** Personal information: a draft that only replaces the profile when saved. */
data class PersonalInfoDraft(
    val fullName: String,
    val email: String?,
    val homeAirport: Airport?,
    val homeCode: String,
    val pickingAirport: Boolean = false,
    val airportQuery: String = "",
    val airports: List<Airport> = emptyList(),
    val problem: ProfileProblem? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
)

/** The sheets Profile can open over itself. */
sealed interface ProfileSheet {
    data class PersonalInfo(val draft: PersonalInfoDraft) : ProfileSheet
    data object Payments : ProfileSheet
    data object Help : ProfileSheet
}

data class ProfileUiState(
    val session: UserSession? = null,
    val overview: ProfileOverview? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val savedCount: Int = 0,
    val sheet: ProfileSheet? = null,
) {
    /** The account's name, else the session's, else a friendly guest label (null). */
    val displayName: String? get() = overview?.profile?.fullName?.takeIf { it.isNotBlank() } ?: session?.fullName?.takeIf { it.isNotBlank() }
    val initials: String
        get() = displayName?.split(' ')?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.first().uppercase() }
            ?.ifEmpty { null } ?: session?.initials ?: "G"
}

sealed interface ProfileEvent {
    data object Refresh : ProfileEvent
    data object Retry : ProfileEvent
    data object OpenSettings : ProfileEvent
    data object OpenPersonalInfo : ProfileEvent
    data object OpenPassengers : ProfileEvent
    data object OpenPayments : ProfileEvent
    data object OpenSaved : ProfileEvent
    data object OpenTrips : ProfileEvent
    data object OpenHelp : ProfileEvent
    data object DismissSheet : ProfileEvent

    data class NameChanged(val name: String) : ProfileEvent
    data object PickHomeAirport : ProfileEvent
    data class AirportQueryChanged(val text: String) : ProfileEvent
    data class AirportPicked(val airport: Airport) : ProfileEvent
    data object CancelAirportPick : ProfileEvent
    data object SavePersonalInfo : ProfileEvent
}

sealed interface ProfileEffect {
    data object Settings : ProfileEffect
    data object Passengers : ProfileEffect
    data object Saved : ProfileEffect
    data object Trips : ProfileEffect
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getOverview: GetProfileOverviewUseCase,
    observeSession: ObserveSessionUseCase,
    observeSavedCount: ObserveSavedCountUseCase,
    private val updateProfile: UpdateProfileUseCase,
    private val getAirports: GetAirportsUseCase,
    private val searchAirports: SearchAirportsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ProfileEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var airports: List<Airport> = emptyList()

    init {
        viewModelScope.launch { observeSession().collect { s -> _uiState.update { it.copy(session = s) } } }
        viewModelScope.launch { observeSavedCount().collect { n -> _uiState.update { it.copy(savedCount = n) } } }
        load()
    }

    fun onEvent(event: ProfileEvent) {
        when (event) {
            // Coming back to the tab: refresh quietly (a trip may have been booked meanwhile).
            ProfileEvent.Refresh -> load(quiet = true)
            ProfileEvent.Retry -> load()
            ProfileEvent.OpenSettings -> send(ProfileEffect.Settings)
            ProfileEvent.OpenPassengers -> send(ProfileEffect.Passengers)
            ProfileEvent.OpenSaved -> send(ProfileEffect.Saved)
            ProfileEvent.OpenTrips -> send(ProfileEffect.Trips)
            ProfileEvent.OpenPayments -> _uiState.update { it.copy(sheet = ProfileSheet.Payments) }
            ProfileEvent.OpenHelp -> _uiState.update { it.copy(sheet = ProfileSheet.Help) }
            ProfileEvent.DismissSheet -> {
                val draft = (_uiState.value.sheet as? ProfileSheet.PersonalInfo)?.draft
                if (draft?.saving != true) _uiState.update { it.copy(sheet = null) }
            }
            ProfileEvent.OpenPersonalInfo -> openPersonalInfo()
            is ProfileEvent.NameChanged -> updateDraft { it.copy(fullName = event.name.take(MAX_NAME), problem = null, failed = false) }
            ProfileEvent.PickHomeAirport -> updateDraft { it.copy(pickingAirport = true, airportQuery = "", airports = searchAirports(airports, "")) }
            is ProfileEvent.AirportQueryChanged -> updateDraft { it.copy(airportQuery = event.text, airports = searchAirports(airports, event.text)) }
            is ProfileEvent.AirportPicked -> updateDraft {
                it.copy(homeAirport = event.airport, homeCode = event.airport.code, pickingAirport = false, problem = null, failed = false)
            }
            ProfileEvent.CancelAirportPick -> updateDraft { it.copy(pickingAirport = false) }
            ProfileEvent.SavePersonalInfo -> savePersonalInfo()
        }
    }

    private fun load(quiet: Boolean = false) {
        if (!quiet) _uiState.update { it.copy(loading = it.overview == null, error = null) }
        viewModelScope.launch {
            when (val result = getOverview()) {
                is Outcome.Success -> _uiState.update { it.copy(overview = result.value, loading = false, error = null) }
                // A quiet refresh that fails keeps what's on screen.
                is Outcome.Failure -> _uiState.update { if (quiet && it.overview != null) it else it.copy(loading = false, error = result.error) }
            }
        }
    }

    private fun openPersonalInfo() {
        val state = _uiState.value
        val profile = state.overview?.profile
        val code = profile?.homeAirport ?: DEFAULT_HOME
        _uiState.update {
            it.copy(
                sheet = ProfileSheet.PersonalInfo(
                    PersonalInfoDraft(
                        fullName = state.displayName.orEmpty(),
                        email = profile?.email ?: state.session?.email,
                        homeAirport = airports.firstOrNull { a -> a.code == code },
                        homeCode = code,
                    ),
                ),
            )
        }
        if (airports.isEmpty()) {
            viewModelScope.launch {
                airports = (getAirports() as? Outcome.Success)?.value.orEmpty()
                updateDraft { d -> d.copy(homeAirport = d.homeAirport ?: airports.firstOrNull { it.code == d.homeCode }) }
            }
        }
    }

    private fun savePersonalInfo() {
        val draft = (_uiState.value.sheet as? ProfileSheet.PersonalInfo)?.draft ?: return
        if (draft.saving) return
        updateProfile.validate(draft.fullName, draft.homeCode)?.let { problem ->
            updateDraft { it.copy(problem = problem) }
            return
        }
        updateDraft { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            when (val result = updateProfile(draft.fullName, draft.homeCode)) {
                is Outcome.Success -> _uiState.update { s ->
                    s.copy(sheet = null, overview = s.overview?.copy(profile = result.value))
                }
                is Outcome.Failure -> updateDraft { it.copy(saving = false, failed = true) }
            }
        }
    }

    private fun updateDraft(transform: (PersonalInfoDraft) -> PersonalInfoDraft) = _uiState.update { s ->
        val sheet = s.sheet as? ProfileSheet.PersonalInfo ?: return@update s
        s.copy(sheet = ProfileSheet.PersonalInfo(transform(sheet.draft)))
    }

    private fun send(effect: ProfileEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val DEFAULT_HOME = "JFK"
        const val MAX_NAME = 80
    }
}
