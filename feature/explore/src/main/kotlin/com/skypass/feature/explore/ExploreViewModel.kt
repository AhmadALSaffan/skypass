package com.skypass.feature.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.FilterDestinationsUseCase
import com.skypass.core.domain.usecase.GetAirportsUseCase
import com.skypass.core.domain.usecase.GetExploreCatalogUseCase
import com.skypass.core.domain.usecase.ObserveSavedDestinationsUseCase
import com.skypass.core.domain.usecase.RequestFlightSearchUseCase
import com.skypass.core.domain.usecase.SetDestinationSavedUseCase
import com.skypass.core.domain.usecase.ResolveOriginAirportUseCase
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.model.Airport
import com.skypass.core.model.AppError
import com.skypass.core.model.Deal
import com.skypass.core.model.Destination
import com.skypass.core.model.ExploreCategory
import com.skypass.core.model.Outcome
import com.skypass.core.model.SearchIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val origin: Airport? = null,
    val deal: Deal? = null,
    val query: String = "",
    val category: ExploreCategory = ExploreCategory.All,
    /** The grid after the chip and the search text are applied. */
    val destinations: List<Destination> = emptyList(),
    /** While searching: airports that match but aren't in the grid ("Fly to Hamilton"). */
    val airportMatches: List<Airport> = emptyList(),
    /** Hearted destinations (they show on the Saved tab). */
    val savedIds: Set<String> = emptySet(),
) {
    val isSearching: Boolean get() = query.isNotBlank()
    val isEmpty: Boolean get() = !loading && error == null && destinations.isEmpty() && airportMatches.isEmpty()
}

sealed interface ExploreEvent {
    data class QueryChanged(val text: String) : ExploreEvent
    data object ClearQuery : ExploreEvent
    data class CategorySelected(val category: ExploreCategory) : ExploreEvent
    data class DestinationTapped(val destination: Destination) : ExploreEvent
    data class ToggleSaved(val destination: Destination) : ExploreEvent
    data class AirportTapped(val airport: Airport) : ExploreEvent
    data object DealTapped : ExploreEvent
    data object FlightsTapped : ExploreEvent
    data object HotelsTapped : ExploreEvent
    data object CarsTapped : ExploreEvent
    data object Retry : ExploreEvent
}

sealed interface ExploreEffect {
    /** Switch to the Home tab (its search card is already filled when a search was requested). */
    data object OpenFlights : ExploreEffect
    data object OpenHotels : ExploreEffect
    data object OpenCars : ExploreEffect
}

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getAirports: GetAirportsUseCase,
    private val resolveOrigin: ResolveOriginAirportUseCase,
    private val getCatalog: GetExploreCatalogUseCase,
    private val filterDestinations: FilterDestinationsUseCase,
    private val searchAirports: SearchAirportsUseCase,
    private val requestFlightSearch: RequestFlightSearchUseCase,
    observeSaved: ObserveSavedDestinationsUseCase,
    private val setSaved: SetDestinationSavedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExploreUiState(
            query = savedStateHandle[KEY_QUERY] ?: "",
            category = savedStateHandle.get<String>(KEY_CATEGORY)?.let(ExploreCategory::valueOf) ?: ExploreCategory.All,
        ),
    )
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ExploreEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var airports: List<Airport> = emptyList()
    private var catalog: List<Destination> = emptyList()

    init {
        load()
        viewModelScope.launch { observeSaved().collect { ids -> _uiState.update { it.copy(savedIds = ids) } } }
    }

    fun onEvent(event: ExploreEvent) {
        when (event) {
            is ExploreEvent.QueryChanged -> {
                savedStateHandle[KEY_QUERY] = event.text
                _uiState.update { it.copy(query = event.text) }
                refilter()
            }
            ExploreEvent.ClearQuery -> onEvent(ExploreEvent.QueryChanged(""))
            is ExploreEvent.ToggleSaved -> viewModelScope.launch {
                setSaved(event.destination.id, event.destination.id !in _uiState.value.savedIds)
            }
            is ExploreEvent.CategorySelected -> {
                savedStateHandle[KEY_CATEGORY] = event.category.name
                _uiState.update { it.copy(category = event.category) }
                refilter()
            }
            is ExploreEvent.DestinationTapped -> searchFlightsTo(event.destination.airportCode)
            is ExploreEvent.AirportTapped -> searchFlightsTo(event.airport.code)
            ExploreEvent.DealTapped -> _uiState.value.deal?.let { deal ->
                // A deal is a specific route, so it sets FROM as well.
                requestFlightSearch(SearchIntent(toCode = deal.destinationCode, fromCode = deal.originCode))
                send(ExploreEffect.OpenFlights)
            }
            ExploreEvent.FlightsTapped -> send(ExploreEffect.OpenFlights)
            ExploreEvent.HotelsTapped -> send(ExploreEffect.OpenHotels)
            ExploreEvent.CarsTapped -> send(ExploreEffect.OpenCars)
            ExploreEvent.Retry -> load()
        }
    }

    private fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val loaded = getAirports()
            if (loaded is Outcome.Failure) {
                _uiState.update { it.copy(loading = false, error = loaded.error) }
                return@launch
            }
            airports = (loaded as Outcome.Success).value
            val origin = resolveOrigin(airports)
            when (val result = getCatalog(origin?.code ?: ResolveOriginAirportUseCase.DEFAULT_ORIGIN)) {
                is Outcome.Failure -> _uiState.update { it.copy(loading = false, error = result.error) }
                is Outcome.Success -> {
                    catalog = result.value.destinations
                    _uiState.update { it.copy(loading = false, origin = origin, deal = result.value.deal) }
                    refilter()
                }
            }
        }
    }

    private fun refilter() {
        val state = _uiState.value
        val grid = filterDestinations(catalog, state.category, state.query)
        val inGrid = grid.mapTo(HashSet()) { it.airportCode }
        val matches = if (state.isSearching) {
            searchAirports(airports, state.query)
                .filter { it.code !in inGrid && it.code != state.origin?.code }
                .take(MAX_AIRPORT_MATCHES)
        } else {
            emptyList()
        }
        _uiState.update { it.copy(destinations = grid, airportMatches = matches) }
    }

    private fun searchFlightsTo(code: String) {
        requestFlightSearch(SearchIntent(toCode = code))
        send(ExploreEffect.OpenFlights)
    }

    private fun send(effect: ExploreEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_CATEGORY = "category"
        const val MAX_AIRPORT_MATCHES = 4
    }
}
