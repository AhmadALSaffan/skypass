package com.skypass.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.ConsumeSearchIntentUseCase
import com.skypass.core.domain.usecase.CreateDefaultSearchUseCase
import com.skypass.core.domain.usecase.ObserveSearchIntentUseCase
import com.skypass.core.domain.usecase.DayPart
import com.skypass.core.domain.usecase.FindNearestAirportUseCase
import com.skypass.core.domain.usecase.GetAirportsUseCase
import com.skypass.core.domain.usecase.GetPopularDestinationsUseCase
import com.skypass.core.domain.usecase.GetProfileUseCase
import com.skypass.core.domain.usecase.GreetingUseCase
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.domain.usecase.ObserveUpcomingTripUseCase
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.model.Airport
import com.skypass.core.model.Cabin
import com.skypass.core.model.Destination
import com.skypass.core.model.FlightSearchQuery
import com.skypass.core.model.Outcome
import com.skypass.core.model.SearchIntent
import com.skypass.core.model.TripType
import com.skypass.core.model.UpcomingTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class AirportField { From, To }
enum class DateField { Depart, Return }

data class HomeUiState(
    val dayPart: DayPart = DayPart.Evening,
    val travellerName: String? = null,
    val initials: String = "",
    val isGuest: Boolean = false,
    /** Null until airports have loaded. */
    val search: FlightSearchQuery? = null,
    /** Each swap turns the button another half turn. */
    val swapTurns: Int = 0,
    val trip: UpcomingTrip? = null,
    val tripLoaded: Boolean = false,
    val homeCity: String = "",
    val popular: List<Destination> = emptyList(),
    val loadFailed: Boolean = false,
    val airportPicker: AirportField? = null,
    val airportQuery: String = "",
    val airportResults: List<Airport> = emptyList(),
    val datePicker: DateField? = null,
    val cabinMenuOpen: Boolean = false,
    /** True when FROM was filled from the traveller's location. */
    val fromIsNearby: Boolean = false,
    val firstBookableDate: LocalDate = LocalDate.now(),
    val lastBookableDate: LocalDate = LocalDate.now().plusDays(CreateDefaultSearchUseCase.BOOKING_WINDOW_DAYS),
)

sealed interface HomeEvent {
    data class TripTypeSelected(val type: TripType) : HomeEvent
    data object Swap : HomeEvent
    data class OpenAirportPicker(val field: AirportField) : HomeEvent
    data class AirportQueryChanged(val text: String) : HomeEvent
    data class AirportPicked(val airport: Airport) : HomeEvent
    data object DismissAirportPicker : HomeEvent
    data class OpenDatePicker(val field: DateField) : HomeEvent
    data class DatePicked(val date: LocalDate) : HomeEvent
    data object DismissDatePicker : HomeEvent
    data object AddReturn : HomeEvent
    data object OpenCabinMenu : HomeEvent
    data class CabinSelected(val cabin: Cabin) : HomeEvent
    data object DismissCabinMenu : HomeEvent
    /** The user just allowed location: look for the nearest airport. */
    data object LocationPermissionGranted : HomeEvent
    data object AddAdult : HomeEvent
    data object RemoveAdult : HomeEvent
    data object CycleCabin : HomeEvent
    data object Search : HomeEvent
    data class DestinationTapped(val destination: Destination) : HomeEvent
    data object Retry : HomeEvent
}

sealed interface HomeEffect {
    data class OpenResults(val query: FlightSearchQuery) : HomeEffect
    /** A destination card filled the search card: bring it into view. */
    data object ScrollToSearch : HomeEffect
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeSession: ObserveSessionUseCase,
    private val getProfile: GetProfileUseCase,
    private val getAirports: GetAirportsUseCase,
    private val searchAirports: SearchAirportsUseCase,
    private val getPopular: GetPopularDestinationsUseCase,
    private val observeUpcomingTrip: ObserveUpcomingTripUseCase,
    private val createDefaultSearch: CreateDefaultSearchUseCase,
    private val findNearestAirport: FindNearestAirportUseCase,
    private val observeSearchIntent: ObserveSearchIntentUseCase,
    private val consumeSearchIntent: ConsumeSearchIntentUseCase,
    greeting: GreetingUseCase,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val _uiState = MutableStateFlow(
        HomeUiState(
            dayPart = greeting(),
            firstBookableDate = today,
            lastBookableDate = today.plusDays(CreateDefaultSearchUseCase.BOOKING_WINDOW_DAYS),
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var airports: List<Airport> = emptyList()
    /** Once the traveller picks FROM themselves, location never overrides it. */
    private var fromChosenByUser = false
    private var listeningForIntents = false

    init {
        load()
        viewModelScope.launch {
            observeUpcomingTrip().collect { trip -> _uiState.update { it.copy(trip = trip, tripLoaded = true) } }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.TripTypeSelected -> {
                updateSearch { q ->
                    if (event.type == TripType.RoundTrip) q.copy(tripType = event.type) else q.copy(tripType = event.type, returnDate = null)
                }
                // Round trip without a return yet: ask for it straight away, never guess it.
                if (event.type == TripType.RoundTrip && _uiState.value.search?.returnDate == null) {
                    _uiState.update { it.copy(datePicker = DateField.Return) }
                }
            }
            HomeEvent.Swap -> {
                fromChosenByUser = true
                _uiState.update { it.copy(fromIsNearby = false) }
                updateSearch { it.copy(from = it.to, to = it.from) }
                _uiState.update { it.copy(swapTurns = it.swapTurns + 1) }
            }
            is HomeEvent.OpenAirportPicker -> _uiState.update {
                it.copy(airportPicker = event.field, airportQuery = "", airportResults = searchAirports(airports, ""))
            }
            is HomeEvent.AirportQueryChanged -> _uiState.update {
                it.copy(airportQuery = event.text, airportResults = searchAirports(airports, event.text))
            }
            is HomeEvent.AirportPicked -> pickAirport(event.airport)
            HomeEvent.DismissAirportPicker -> _uiState.update { it.copy(airportPicker = null) }
            is HomeEvent.OpenDatePicker -> _uiState.update { it.copy(datePicker = event.field) }
            is HomeEvent.DatePicked -> pickDate(event.date)
            HomeEvent.DismissDatePicker -> _uiState.update { it.copy(datePicker = null) }
            // "+ Add return" opens the calendar; the trip becomes Round trip once a date is picked.
            HomeEvent.AddReturn -> _uiState.update { it.copy(datePicker = DateField.Return) }
            HomeEvent.OpenCabinMenu -> _uiState.update { it.copy(cabinMenuOpen = true) }
            is HomeEvent.CabinSelected -> {
                updateSearch { it.copy(cabin = event.cabin) }
                _uiState.update { it.copy(cabinMenuOpen = false) }
            }
            HomeEvent.DismissCabinMenu -> _uiState.update { it.copy(cabinMenuOpen = false) }
            HomeEvent.LocationPermissionGranted -> useNearestAirport()
            HomeEvent.AddAdult -> updateSearch { it.copy(adults = (it.adults + 1).coerceAtMost(FlightSearchQuery.MAX_ADULTS)) }
            HomeEvent.RemoveAdult -> updateSearch { it.copy(adults = (it.adults - 1).coerceAtLeast(FlightSearchQuery.MIN_ADULTS)) }
            HomeEvent.CycleCabin -> updateSearch { it.copy(cabin = it.cabin.next()) }
            HomeEvent.Search -> _uiState.value.search?.takeIf { it.isValid }?.let { send(HomeEffect.OpenResults(it)) }
            is HomeEvent.DestinationTapped -> airports.firstOrNull { it.code == event.destination.airportCode }?.let { airport ->
                pickAirportFor(AirportField.To, airport)
                send(HomeEffect.ScrollToSearch)
            }
            HomeEvent.Retry -> load()
        }
    }

    private fun load() {
        _uiState.update { it.copy(loadFailed = false) }
        viewModelScope.launch {
            val session = observeSession().first()
            val profile = (getProfile() as? Outcome.Success)?.value
            val loaded = getAirports()
            if (loaded !is Outcome.Success) {
                _uiState.update { it.copy(loadFailed = true) }
                return@launch
            }
            airports = loaded.value
            val home = airports.firstOrNull { it.code == profile?.homeAirport }
                ?: airports.firstOrNull { it.code == DEFAULT_HOME } ?: airports.first()
            val destination = airports.firstOrNull { it.code == if (home.code == DEFAULT_TO) "AKL" else DEFAULT_TO }
                ?: airports.first { it.code != home.code }
            val name = profile?.fullName ?: session?.fullName
            _uiState.update {
                it.copy(
                    travellerName = name,
                    initials = session?.initials ?: "",
                    isGuest = session?.isGuest == true,
                    search = it.search ?: createDefaultSearch(home, destination),
                    homeCity = home.city,
                )
            }
            listenForSearchIntents()
            loadPopular(home.code)
            useNearestAirport()
        }
    }

    /** Explore (and later tabs) ask for a search: fill the card and bring it into view. */
    private fun listenForSearchIntents() {
        if (listeningForIntents) return
        listeningForIntents = true
        viewModelScope.launch {
            observeSearchIntent().filterNotNull().collect { intent ->
                applySearchIntent(intent)
                consumeSearchIntent()
            }
        }
    }

    private fun applySearchIntent(intent: SearchIntent) {
        val to = airports.firstOrNull { it.code == intent.toCode } ?: return
        val from = intent.fromCode?.let { code -> airports.firstOrNull { it.code == code } }
        if (from != null && from.code != to.code) {
            fromChosenByUser = true
            _uiState.update { it.copy(fromIsNearby = false) }
            updateSearch { it.copy(from = from, to = to) }
        } else {
            pickAirportFor(AirportField.To, to)
        }
        send(HomeEffect.ScrollToSearch)
    }

    /** FROM = the airport closest to the traveller (if location is allowed and FROM is untouched). */
    private fun useNearestAirport() {
        if (airports.isEmpty()) return
        viewModelScope.launch {
            val nearest = findNearestAirport(airports) ?: return@launch
            if (fromChosenByUser) return@launch
            updateSearch { q -> if (q.to.code == nearest.code) q.copy(from = nearest, to = q.from) else q.copy(from = nearest) }
            _uiState.update { it.copy(fromIsNearby = true, homeCity = nearest.city) }
            loadPopular(nearest.code)
        }
    }

    private suspend fun loadPopular(code: String) {
        val popular = getPopular(code)
        if (popular is Outcome.Success) _uiState.update { it.copy(popular = popular.value) }
    }

    private fun pickAirport(airport: Airport) {
        val field = _uiState.value.airportPicker ?: return
        if (field == AirportField.From) {
            fromChosenByUser = true
            _uiState.update { it.copy(fromIsNearby = false) }
        }
        pickAirportFor(field, airport)
        _uiState.update { it.copy(airportPicker = null) }
    }

    /** Picking the airport already on the other side swaps them, so the route is never A → A. */
    private fun pickAirportFor(field: AirportField, airport: Airport) = updateSearch { q ->
        when (field) {
            AirportField.From -> if (airport.code == q.to.code) q.copy(from = airport, to = q.from) else q.copy(from = airport)
            AirportField.To -> if (airport.code == q.from.code) q.copy(to = airport, from = q.to) else q.copy(to = airport)
        }
    }

    private fun pickDate(date: LocalDate) {
        when (_uiState.value.datePicker) {
            DateField.Depart -> updateSearch { q ->
                val ret = q.returnDate?.let { if (it.isBefore(date)) defaultReturn(date) else it }
                q.copy(departDate = date, returnDate = ret)
            }
            DateField.Return -> updateSearch { q ->
                q.copy(returnDate = if (date.isBefore(q.departDate)) q.departDate else date, tripType = TripType.RoundTrip)
            }
            null -> Unit
        }
        _uiState.update { it.copy(datePicker = null) }
    }

    private fun defaultReturn(depart: LocalDate): LocalDate =
        depart.plusDays(CreateDefaultSearchUseCase.DEFAULT_STAY_DAYS).coerceAtMost(_uiState.value.lastBookableDate)

    private fun updateSearch(transform: (FlightSearchQuery) -> FlightSearchQuery) {
        _uiState.update { state -> state.search?.let { state.copy(search = transform(it)) } ?: state }
    }

    private fun send(effect: HomeEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private fun LocalDate.coerceAtMost(max: LocalDate) = if (isAfter(max)) max else this

    private companion object {
        const val DEFAULT_HOME = "JFK"
        const val DEFAULT_TO = "LAX"
    }
}
