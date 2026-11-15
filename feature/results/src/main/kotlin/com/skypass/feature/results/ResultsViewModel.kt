package com.skypass.feature.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.FilterFlightsUseCase
import com.skypass.core.domain.usecase.GetPriceStripUseCase
import com.skypass.core.domain.usecase.ObserveSavedFlightsUseCase
import com.skypass.core.domain.usecase.SearchFlightsUseCase
import com.skypass.core.domain.usecase.SortFlightsUseCase
import com.skypass.core.domain.usecase.ToggleSavedFlightUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.Cabin
import com.skypass.core.model.DayPrice
import com.skypass.core.model.FlightFilters
import com.skypass.core.model.FlightOffer
import com.skypass.core.model.Outcome
import com.skypass.core.model.SortOrder
import com.skypass.core.model.TripLeg
import com.skypass.feature.results.navigation.FlightSelection
import com.skypass.feature.results.navigation.ResultsArgs
import com.skypass.feature.results.navigation.ResultsDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class AirlineOption(val code: String, val name: String)

data class ResultsUiState(
    val from: String,
    val to: String,
    val leg: TripLeg,
    val isRoundTrip: Boolean,
    val adults: Int,
    val cabin: Cabin,
    val today: LocalDate,
    val selectedDate: LocalDate,
    val strip: List<DayPrice> = emptyList(),
    val loading: Boolean = true,
    val error: AppError? = null,
    /** Everything that flies on the selected date. */
    val all: List<FlightOffer> = emptyList(),
    /** After filters and sort: what the list shows. */
    val visible: List<FlightOffer> = emptyList(),
    val sort: SortOrder = SortOrder.Cheapest,
    val filters: FlightFilters = FlightFilters(),
    val airlines: List<AirlineOption> = emptyList(),
    val expandedId: String? = null,
    val savedIds: Set<String> = emptySet(),
    val filterSheetOpen: Boolean = false,
)

sealed interface ResultsEvent {
    data class SelectDate(val date: LocalDate) : ResultsEvent
    data class SelectSort(val order: SortOrder) : ResultsEvent
    data class ToggleExpanded(val instanceId: String) : ResultsEvent
    data class ToggleSaved(val instanceId: String) : ResultsEvent
    data object OpenFilters : ResultsEvent
    data object CloseFilters : ResultsEvent
    data class UpdateFilters(val filters: FlightFilters) : ResultsEvent
    data object ResetFilters : ResultsEvent
    data object Retry : ResultsEvent
    data class SelectFlight(val instanceId: String) : ResultsEvent
}

sealed interface ResultsEffect {
    /** Round trip: outbound chosen, now choose the way back. */
    data class PickReturn(val destination: ResultsDestination) : ResultsEffect
    data class OpenBooking(val selection: FlightSelection) : ResultsEffect
    data object SaveFailed : ResultsEffect
}

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchFlights: SearchFlightsUseCase,
    private val getPriceStrip: GetPriceStripUseCase,
    private val sortFlights: SortFlightsUseCase,
    private val filterFlights: FilterFlightsUseCase,
    private val observeSaved: ObserveSavedFlightsUseCase,
    private val toggleSaved: ToggleSavedFlightUseCase,
    clock: Clock,
) : ViewModel() {

    private val args = ResultsArgs(savedStateHandle)

    private val _uiState = MutableStateFlow(
        ResultsUiState(
            from = args.from,
            to = args.to,
            leg = args.leg,
            isRoundTrip = args.returnDate != null || args.leg == TripLeg.Return,
            adults = args.adults,
            cabin = args.cabin,
            today = LocalDate.now(clock),
            selectedDate = args.date,
        ),
    )
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ResultsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var searchJob: Job? = null
    /** The board opens the first result; after that the user decides. */
    private var autoExpand = true

    init {
        viewModelScope.launch {
            val strip = getPriceStrip(args.from, args.to, args.date, args.cabin)
            if (strip is Outcome.Success) _uiState.update { it.copy(strip = strip.value) }
        }
        viewModelScope.launch { observeSaved().collect { ids -> _uiState.update { it.copy(savedIds = ids) } } }
        search(args.date)
    }

    fun onEvent(event: ResultsEvent) {
        when (event) {
            is ResultsEvent.SelectDate -> if (event.date != _uiState.value.selectedDate && !event.date.isBefore(_uiState.value.today)) {
                autoExpand = true
                search(event.date)
            }
            is ResultsEvent.SelectSort -> _uiState.update { it.withVisible(sort = event.order) }
            is ResultsEvent.ToggleExpanded -> {
                autoExpand = false
                _uiState.update { it.copy(expandedId = if (it.expandedId == event.instanceId) null else event.instanceId) }
            }
            is ResultsEvent.ToggleSaved -> save(event.instanceId)
            ResultsEvent.OpenFilters -> _uiState.update { it.copy(filterSheetOpen = true) }
            ResultsEvent.CloseFilters -> _uiState.update { it.copy(filterSheetOpen = false) }
            is ResultsEvent.UpdateFilters -> _uiState.update { it.withVisible(filters = event.filters) }
            ResultsEvent.ResetFilters -> _uiState.update { it.withVisible(filters = FlightFilters()) }
            ResultsEvent.Retry -> search(_uiState.value.selectedDate)
            is ResultsEvent.SelectFlight -> select(event.instanceId)
        }
    }

    private fun search(date: LocalDate) {
        searchJob?.cancel()
        _uiState.update { it.copy(selectedDate = date, loading = true, error = null) }
        searchJob = viewModelScope.launch {
            when (val result = searchFlights(args.from, args.to, date, args.cabin, args.adults)) {
                is Outcome.Success -> _uiState.update { state ->
                    val airlines = result.value.distinctBy { it.airlineCode }
                        .map { AirlineOption(it.airlineCode, it.airlineName) }
                        .sortedBy { it.name }
                    // Drop airline filters that no longer apply on this day.
                    val filters = state.filters.copy(airlines = state.filters.airlines.intersect(airlines.map { it.code }.toSet()))
                    state.copy(loading = false, all = result.value, airlines = airlines).withVisible(filters = filters)
                }
                is Outcome.Failure -> _uiState.update { it.copy(loading = false, error = result.error, all = emptyList(), visible = emptyList()) }
            }
        }
    }

    private fun ResultsUiState.withVisible(
        sort: SortOrder = this.sort,
        filters: FlightFilters = this.filters,
    ): ResultsUiState {
        val visible = sortFlights(filterFlights(all, filters), sort)
        val expanded = when {
            autoExpand -> visible.firstOrNull()?.instanceId
            expandedId in visible.map { it.instanceId } -> expandedId
            else -> null
        }
        return copy(sort = sort, filters = filters, visible = visible, expandedId = expanded)
    }

    private fun save(instanceId: String) {
        val nowSaved = instanceId !in _uiState.value.savedIds
        viewModelScope.launch {
            if (toggleSaved(instanceId, nowSaved) is Outcome.Failure) _effects.send(ResultsEffect.SaveFailed)
        }
    }

    private fun select(instanceId: String) {
        val effect = when (args.leg) {
            TripLeg.Outbound -> if (args.returnDate != null) {
                ResultsEffect.PickReturn(
                    ResultsDestination(
                        from = args.to,
                        to = args.from,
                        date = args.returnDate.toString(),
                        returnDate = args.returnDate.toString(),
                        adults = args.adults,
                        cabin = args.cabin.apiValue,
                        leg = TripLeg.Return,
                        outboundInstanceId = instanceId,
                    ),
                )
            } else {
                ResultsEffect.OpenBooking(FlightSelection(instanceId, null, args.adults, args.cabin))
            }
            TripLeg.Return -> ResultsEffect.OpenBooking(
                FlightSelection(checkNotNull(args.outboundInstanceId), instanceId, args.adults, args.cabin),
            )
        }
        viewModelScope.launch { _effects.send(effect) }
    }
}
