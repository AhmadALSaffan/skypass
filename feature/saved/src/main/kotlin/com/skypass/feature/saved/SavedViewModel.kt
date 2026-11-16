package com.skypass.feature.saved

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.GetSavedItemsUseCase
import com.skypass.core.domain.usecase.RequestFlightSearchUseCase
import com.skypass.core.domain.usecase.SavedItems
import com.skypass.core.domain.usecase.SetDestinationSavedUseCase
import com.skypass.core.domain.usecase.SetHotelSavedUseCase
import com.skypass.core.domain.usecase.ToggleSavedFlightUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.Outcome
import com.skypass.core.model.SavedDestination
import com.skypass.core.model.SavedFlight
import com.skypass.core.model.SavedHotel
import com.skypass.core.model.SearchIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

enum class SavedTab { Flights, Stays, Destinations }

/** Something just removed, kept for Undo. */
sealed interface Removed {
    data class Flight(val item: SavedFlight, val index: Int) : Removed
    data class Hotel(val item: SavedHotel, val index: Int) : Removed
    data class Destination(val item: SavedDestination, val index: Int) : Removed
}

data class SavedUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val items: SavedItems? = null,
    val tab: SavedTab = SavedTab.Flights,
    val removed: Removed? = null,
    val now: LocalDateTime = LocalDateTime.now(),
)

sealed interface SavedEvent {
    data class SelectTab(val tab: SavedTab) : SavedEvent
    data class OpenFlight(val flight: SavedFlight) : SavedEvent
    data class OpenHotel(val hotel: SavedHotel) : SavedEvent
    data class OpenDestination(val destination: SavedDestination) : SavedEvent
    data class RemoveFlight(val flight: SavedFlight) : SavedEvent
    data class RemoveHotel(val hotel: SavedHotel) : SavedEvent
    data class RemoveDestination(val destination: SavedDestination) : SavedEvent
    data object Undo : SavedEvent
    data object DismissUndo : SavedEvent
    data object FindMore : SavedEvent
    data object Refresh : SavedEvent
    data object Retry : SavedEvent
}

sealed interface SavedEffect {
    /** Book this departure (one adult, economy; Booking lets them change it). */
    data class BookFlight(val instanceId: String) : SavedEffect
    data class OpenHotels(val city: String?) : SavedEffect
    /** Home has a search waiting (or just open Home). */
    data object SearchFlights : SavedEffect
    data object OpenExplore : SavedEffect
}

/** The Saved tab: bookmarked flights, hearted stays and destinations, from `saved_items`. */
@HiltViewModel
class SavedViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getSaved: GetSavedItemsUseCase,
    private val setFlightSaved: ToggleSavedFlightUseCase,
    private val setHotelSaved: SetHotelSavedUseCase,
    private val setDestinationSaved: SetDestinationSavedUseCase,
    private val requestFlightSearch: RequestFlightSearchUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SavedUiState(
            tab = savedStateHandle.get<String>(KEY_TAB)?.let { name -> SavedTab.entries.firstOrNull { it.name == name } } ?: SavedTab.Flights,
            now = LocalDateTime.now(clock),
        ),
    )
    val uiState: StateFlow<SavedUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SavedEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: SavedEvent) {
        when (event) {
            is SavedEvent.SelectTab -> {
                savedStateHandle[KEY_TAB] = event.tab.name
                _uiState.update { it.copy(tab = event.tab) }
            }
            is SavedEvent.OpenFlight -> if (event.flight.isBookable(_uiState.value.now)) send(SavedEffect.BookFlight(event.flight.instanceId))
            is SavedEvent.OpenHotel -> send(SavedEffect.OpenHotels(event.hotel.hotel.city))
            is SavedEvent.OpenDestination -> {
                requestFlightSearch(SearchIntent(toCode = event.destination.destination.airportCode))
                send(SavedEffect.SearchFlights)
            }
            is SavedEvent.RemoveFlight -> remove(
                find = { it.flights.indexOf(event.flight) },
                drop = { items, _ -> items.copy(flights = items.flights - event.flight) },
                removed = { Removed.Flight(event.flight, it) },
                call = { setFlightSaved(event.flight.instanceId, false) },
            )
            is SavedEvent.RemoveHotel -> remove(
                find = { it.hotels.indexOf(event.hotel) },
                drop = { items, _ -> items.copy(hotels = items.hotels - event.hotel) },
                removed = { Removed.Hotel(event.hotel, it) },
                call = { setHotelSaved(event.hotel.hotel.id, false) },
            )
            is SavedEvent.RemoveDestination -> remove(
                find = { it.destinations.indexOf(event.destination) },
                drop = { items, _ -> items.copy(destinations = items.destinations - event.destination) },
                removed = { Removed.Destination(event.destination, it) },
                call = { setDestinationSaved(event.destination.destination.id, false) },
            )
            SavedEvent.Undo -> undo()
            SavedEvent.DismissUndo -> _uiState.update { it.copy(removed = null) }
            SavedEvent.FindMore -> send(
                when (_uiState.value.tab) {
                    SavedTab.Flights -> SavedEffect.SearchFlights
                    SavedTab.Stays -> SavedEffect.OpenHotels(null)
                    SavedTab.Destinations -> SavedEffect.OpenExplore
                },
            )
            // Back on the tab: something may have been saved elsewhere meanwhile.
            SavedEvent.Refresh -> load(quiet = true)
            SavedEvent.Retry -> load()
        }
    }

    private fun load(quiet: Boolean = false) {
        if (!quiet || _uiState.value.items == null) _uiState.update { it.copy(loading = it.items == null, error = null) }
        _uiState.update { it.copy(now = LocalDateTime.now(clock)) }
        viewModelScope.launch {
            when (val result = getSaved()) {
                is Outcome.Success -> _uiState.update { it.copy(loading = false, error = null, items = result.value) }
                is Outcome.Failure -> _uiState.update { if (quiet && it.items != null) it else it.copy(loading = false, error = result.error) }
            }
        }
    }

    /** Optimistic: the card leaves at once (with Undo) and comes back if the server refuses. */
    private fun remove(
        find: (SavedItems) -> Int,
        drop: (SavedItems, Int) -> SavedItems,
        removed: (Int) -> Removed,
        call: suspend () -> Outcome<Unit, AppError>,
    ) {
        val items = _uiState.value.items ?: return
        val index = find(items)
        if (index < 0) return
        _uiState.update { it.copy(items = drop(items, index), removed = removed(index)) }
        viewModelScope.launch {
            if (call() is Outcome.Failure) _uiState.update { s -> s.copy(items = s.items?.let { restore(it, removed(index)) }, removed = null) }
        }
    }

    private fun undo() {
        val removed = _uiState.value.removed ?: return
        _uiState.update { s -> s.copy(items = s.items?.let { restore(it, removed) }, removed = null) }
        viewModelScope.launch {
            when (removed) {
                is Removed.Flight -> setFlightSaved(removed.item.instanceId, true)
                is Removed.Hotel -> setHotelSaved(removed.item.hotel.id, true)
                is Removed.Destination -> setDestinationSaved(removed.item.destination.id, true)
            }
        }
    }

    private fun restore(items: SavedItems, removed: Removed): SavedItems = when (removed) {
        is Removed.Flight -> items.copy(flights = items.flights.insertAt(removed.index, removed.item))
        is Removed.Hotel -> items.copy(hotels = items.hotels.insertAt(removed.index, removed.item))
        is Removed.Destination -> items.copy(destinations = items.destinations.insertAt(removed.index, removed.item))
    }

    private fun <T> List<T>.insertAt(index: Int, item: T): List<T> =
        if (item in this) this else toMutableList().apply { add(index.coerceIn(0, size), item) }

    private fun send(effect: SavedEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val KEY_TAB = "tab"
    }
}
