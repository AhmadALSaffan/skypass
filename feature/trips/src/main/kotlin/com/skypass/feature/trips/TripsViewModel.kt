package com.skypass.feature.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.repository.CancelError
import com.skypass.core.domain.usecase.CancelReservationUseCase
import com.skypass.core.domain.usecase.GetMyTripsUseCase
import com.skypass.core.domain.usecase.MyTrips
import com.skypass.core.model.AppError
import com.skypass.core.model.CarBooking
import com.skypass.core.model.FlightBooking
import com.skypass.core.model.Outcome
import com.skypass.core.model.StayBooking
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

enum class TripsTab { Flights, Stays, Cars }

/** The booking open in the detail sheet. */
sealed interface OpenBooking {
    data class Stay(val booking: StayBooking) : OpenBooking
    data class Car(val booking: CarBooking) : OpenBooking
}

/** Cancelling from the detail sheet: ask first, then call the server. */
enum class CancelStep { None, Confirming, Cancelling }

data class TripsUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val trips: MyTrips? = null,
    val tab: TripsTab = TripsTab.Flights,
    val open: OpenBooking? = null,
    val cancel: CancelStep = CancelStep.None,
    val cancelError: CancelError? = null,
    val today: LocalDate = LocalDate.now(),
    val now: LocalDateTime = LocalDateTime.now(),
)

sealed interface TripsEvent {
    data class SelectTab(val tab: TripsTab) : TripsEvent
    data class OpenFlight(val flight: FlightBooking) : TripsEvent
    data class OpenStay(val stay: StayBooking) : TripsEvent
    data class OpenCar(val car: CarBooking) : TripsEvent
    data object CloseDetail : TripsEvent
    data object Directions : TripsEvent
    data object AskCancel : TripsEvent
    data object KeepBooking : TripsEvent
    data object ConfirmCancel : TripsEvent
    data object FindMore : TripsEvent
    data object Retry : TripsEvent
}

sealed interface TripsEffect {
    data class BoardingPass(val bookingId: String) : TripsEffect
    data class Directions(val query: String) : TripsEffect
    data object FindFlights : TripsEffect
    data object FindStays : TripsEffect
    data object FindCars : TripsEffect
}

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getMyTrips: GetMyTripsUseCase,
    private val cancelReservation: CancelReservationUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TripsUiState(
            // [TripsDestination.tab], or the tab picked before process death.
            tab = (savedStateHandle.get<String>(KEY_TAB) ?: savedStateHandle.get<String>("tab"))
                ?.let { name -> TripsTab.entries.firstOrNull { it.name == name } } ?: TripsTab.Flights,
            today = LocalDate.now(clock),
            now = LocalDateTime.now(clock),
        ),
    )
    val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<TripsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: TripsEvent) {
        when (event) {
            is TripsEvent.SelectTab -> {
                savedStateHandle[KEY_TAB] = event.tab.name
                _uiState.update { it.copy(tab = event.tab) }
            }
            is TripsEvent.OpenFlight -> send(TripsEffect.BoardingPass(event.flight.trip.bookingId))
            is TripsEvent.OpenStay -> _uiState.update { it.copy(open = OpenBooking.Stay(event.stay), cancel = CancelStep.None, cancelError = null) }
            is TripsEvent.OpenCar -> _uiState.update { it.copy(open = OpenBooking.Car(event.car), cancel = CancelStep.None, cancelError = null) }
            TripsEvent.CloseDetail -> {
                if (_uiState.value.cancel != CancelStep.Cancelling) _uiState.update { it.copy(open = null, cancel = CancelStep.None, cancelError = null) }
            }
            TripsEvent.Directions -> when (val open = _uiState.value.open) {
                is OpenBooking.Stay -> send(TripsEffect.Directions("${open.booking.hotel.name}, ${open.booking.hotel.city}"))
                is OpenBooking.Car -> send(TripsEffect.Directions(open.booking.pickupLocation))
                null -> Unit
            }
            TripsEvent.AskCancel -> _uiState.update { it.copy(cancel = CancelStep.Confirming, cancelError = null) }
            TripsEvent.KeepBooking -> _uiState.update { it.copy(cancel = CancelStep.None) }
            TripsEvent.ConfirmCancel -> cancel()
            TripsEvent.FindMore -> send(
                when (_uiState.value.tab) {
                    TripsTab.Flights -> TripsEffect.FindFlights
                    TripsTab.Stays -> TripsEffect.FindStays
                    TripsTab.Cars -> TripsEffect.FindCars
                },
            )
            TripsEvent.Retry -> load()
        }
    }

    private fun load(quiet: Boolean = false) {
        if (!quiet) _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = getMyTrips()) {
                is Outcome.Failure -> _uiState.update { it.copy(loading = false, error = result.error) }
                is Outcome.Success -> _uiState.update { s ->
                    // Keep the open sheet showing the fresh copy (e.g. now cancelled).
                    val open = when (val o = s.open) {
                        is OpenBooking.Stay -> result.value.stays.all().firstOrNull { it.reference == o.booking.reference }?.let { OpenBooking.Stay(it) }
                        is OpenBooking.Car -> result.value.cars.all().firstOrNull { it.reference == o.booking.reference }?.let { OpenBooking.Car(it) }
                        null -> null
                    }
                    s.copy(loading = false, error = null, trips = result.value, open = open)
                }
            }
        }
    }

    private fun cancel() {
        val reference = when (val open = _uiState.value.open) {
            is OpenBooking.Stay -> open.booking.reference
            is OpenBooking.Car -> open.booking.reference
            null -> return
        }
        if (_uiState.value.cancel == CancelStep.Cancelling) return
        _uiState.update { it.copy(cancel = CancelStep.Cancelling, cancelError = null) }
        viewModelScope.launch {
            when (val result = cancelReservation(reference)) {
                is Outcome.Failure -> _uiState.update { it.copy(cancel = CancelStep.Confirming, cancelError = result.error) }
                is Outcome.Success -> {
                    _uiState.update { it.copy(cancel = CancelStep.None) }
                    load(quiet = true)
                }
            }
        }
    }

    private fun send(effect: TripsEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val KEY_TAB = "selectedTab"
    }
}

private fun <T> com.skypass.core.domain.usecase.TripSection<T>.all(): List<T> = upcoming + past
