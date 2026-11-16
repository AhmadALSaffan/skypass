package com.skypass.feature.hotels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.FilterHotelsUseCase
import com.skypass.core.domain.usecase.GetAirportsUseCase
import com.skypass.core.domain.usecase.GetHotelsUseCase
import com.skypass.core.domain.usecase.ObserveSavedHotelsUseCase
import com.skypass.core.domain.usecase.ContactProblem
import com.skypass.core.domain.usecase.PrefillContactUseCase
import com.skypass.core.domain.usecase.PriceStayUseCase
import com.skypass.core.domain.usecase.ReserveHotelUseCase
import com.skypass.core.domain.usecase.ResolveStayUseCase
import com.skypass.core.domain.usecase.RoomPrice
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.domain.usecase.SetHotelSavedUseCase
import com.skypass.core.domain.usecase.StayProblem
import com.skypass.core.domain.usecase.ValidateContactUseCase
import com.skypass.core.domain.usecase.ValidateStayUseCase
import com.skypass.core.model.Airport
import com.skypass.core.model.AppError
import com.skypass.core.model.ArrivalWindow
import com.skypass.core.model.ContactDetails
import com.skypass.core.model.Hotel
import com.skypass.core.model.HotelFilter
import com.skypass.core.model.HotelReservation
import com.skypass.core.model.Outcome
import com.skypass.core.model.ReservationError
import com.skypass.core.model.RoomType
import com.skypass.core.model.Stay
import com.skypass.core.model.StayDetails
import com.skypass.core.model.StayRequest
import com.skypass.feature.hotels.navigation.HotelsDestination
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
import java.util.UUID
import javax.inject.Inject

/** One city in the stay editor's list ("Sydney · Australia"). */
data class CityOption(val city: String, val country: String)

/** The "Your stay" sheet: a draft that only replaces the stay when the traveller taps Show stays. */
data class StayDraft(
    val stay: Stay,
    val pickingCity: Boolean = false,
    val cityQuery: String = "",
    val cities: List<CityOption> = emptyList(),
    val problem: StayProblem? = null,
)

sealed interface ReserveState {
    data object Choosing : ReserveState
    data object Reserving : ReserveState
    data class Reserved(val reservation: HotelReservation) : ReserveState
}

/** The sheet's two steps: pick a room, then who's staying and how. */
enum class RoomsStep { Room, Details }

/** The "View rooms" sheet for one hotel. */
data class RoomsSheet(
    val hotel: Hotel,
    val rooms: List<RoomPrice>,
    val selected: RoomType,
    val step: RoomsStep = RoomsStep.Room,
    val details: StayDetails = StayDetails(ContactDetails()),
    /** Shown only after the guest tries to book, so the form doesn't open full of red. */
    val problems: Set<ContactProblem> = emptySet(),
    val state: ReserveState = ReserveState.Choosing,
    val error: ReservationError? = null,
) {
    val selectedPrice: RoomPrice? get() = rooms.firstOrNull { it.room == selected }
}

data class HotelsUiState(
    val stay: Stay? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val filter: HotelFilter = HotelFilter.All,
    val hotels: List<Hotel> = emptyList(),
    val savedIds: Set<String> = emptySet(),
    val draft: StayDraft? = null,
    val rooms: RoomsSheet? = null,
    val firstBookableDate: LocalDate = LocalDate.now(),
) {
    val isEmpty: Boolean get() = !loading && error == null && hotels.isEmpty()
}

sealed interface HotelsEvent {
    data class SelectFilter(val filter: HotelFilter) : HotelsEvent
    data class ToggleSaved(val hotel: Hotel) : HotelsEvent
    data object OpenMap : HotelsEvent

    data object EditStay : HotelsEvent
    data class UpdateDraft(val stay: Stay) : HotelsEvent
    data object PickCity : HotelsEvent
    data class CityQueryChanged(val text: String) : HotelsEvent
    data class CityPicked(val city: CityOption) : HotelsEvent
    data object ApplyStay : HotelsEvent
    data object DismissStay : HotelsEvent

    data class ViewRooms(val hotel: Hotel) : HotelsEvent
    data class SelectRoom(val room: RoomType) : HotelsEvent
    data object ContinueToDetails : HotelsEvent
    data object BackToRooms : HotelsEvent
    data class GuestChanged(val guest: ContactDetails) : HotelsEvent
    data class SelectArrival(val arrival: ArrivalWindow) : HotelsEvent
    data class ToggleRequest(val request: StayRequest) : HotelsEvent
    data class NoteChanged(val note: String) : HotelsEvent
    data object Reserve : HotelsEvent
    data object DismissRooms : HotelsEvent
    data object OpenMyTrips : HotelsEvent

    data object Retry : HotelsEvent
}

sealed interface HotelsEffect {
    /** Show the city's hotels in a maps app. */
    data class OpenMap(val query: String) : HotelsEffect

    /** The stay was booked: show it in My trips. */
    data object OpenMyTrips : HotelsEffect
}

@HiltViewModel
class HotelsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val resolveStay: ResolveStayUseCase,
    private val getHotels: GetHotelsUseCase,
    private val filterHotels: FilterHotelsUseCase,
    private val priceStay: PriceStayUseCase,
    private val validateStay: ValidateStayUseCase,
    private val reserveHotel: ReserveHotelUseCase,
    private val prefillContact: PrefillContactUseCase,
    private val validateContact: ValidateContactUseCase,
    private val getAirports: GetAirportsUseCase,
    private val searchAirports: SearchAirportsUseCase,
    observeSaved: ObserveSavedHotelsUseCase,
    private val setSaved: SetHotelSavedUseCase,
    clock: Clock,
) : ViewModel() {

    /** [HotelsDestination.city]: navigation puts route arguments in the handle by name. */
    private val requestedCity: String? = savedStateHandle.get<String>("city")

    private val _uiState = MutableStateFlow(
        HotelsUiState(
            filter = savedStateHandle.get<String>(KEY_FILTER)?.let(HotelFilter::valueOf) ?: HotelFilter.All,
            firstBookableDate = LocalDate.now(clock),
        ),
    )
    val uiState: StateFlow<HotelsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HotelsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var all: List<Hotel> = emptyList()

    /** The traveller's saved details, loaded once and copied into each new booking form. */
    private var prefill: ContactDetails? = null
    private var airports: List<Airport> = emptyList()

    /** One per reservation attempt, so a retry after a dropped connection never books twice. */
    private var attemptId: String
        get() = savedStateHandle.get<String>(KEY_ATTEMPT) ?: newAttempt()
        set(value) { savedStateHandle[KEY_ATTEMPT] = value }

    init {
        viewModelScope.launch {
            val stay = resolveStay(requestedCity)
            _uiState.update { it.copy(stay = stay) }
            load(stay.city)
        }
        viewModelScope.launch {
            observeSaved().collect { ids -> _uiState.update { it.copy(savedIds = ids) } }
        }
    }

    fun onEvent(event: HotelsEvent) {
        when (event) {
            is HotelsEvent.SelectFilter -> {
                savedStateHandle[KEY_FILTER] = event.filter.name
                _uiState.update { it.copy(filter = event.filter, hotels = filterHotels(all, event.filter)) }
            }
            is HotelsEvent.ToggleSaved -> viewModelScope.launch {
                setSaved(event.hotel.id, event.hotel.id !in _uiState.value.savedIds)
            }
            HotelsEvent.OpenMap -> _uiState.value.stay?.let { send(HotelsEffect.OpenMap("hotels in ${it.city}")) }

            HotelsEvent.EditStay -> _uiState.value.stay?.let { stay -> _uiState.update { it.copy(draft = StayDraft(stay)) } }
            is HotelsEvent.UpdateDraft -> updateDraft { it.copy(stay = event.stay, problem = null) }
            HotelsEvent.PickCity -> viewModelScope.launch {
                if (airports.isEmpty()) airports = (getAirports() as? Outcome.Success)?.value.orEmpty()
                updateDraft { it.copy(pickingCity = true, cityQuery = "", cities = cities("")) }
            }
            is HotelsEvent.CityQueryChanged -> updateDraft { it.copy(cityQuery = event.text, cities = cities(event.text)) }
            is HotelsEvent.CityPicked -> updateDraft { it.copy(stay = it.stay.copy(city = event.city.city), pickingCity = false) }
            HotelsEvent.ApplyStay -> applyStay()
            HotelsEvent.DismissStay -> _uiState.update { it.copy(draft = null) }

            is HotelsEvent.ViewRooms -> openRooms(event.hotel)
            is HotelsEvent.SelectRoom -> _uiState.update { s -> s.copy(rooms = s.rooms?.copy(selected = event.room, error = null)) }
            HotelsEvent.ContinueToDetails -> updateRooms { it.copy(step = RoomsStep.Details, error = null) }
            HotelsEvent.BackToRooms -> updateRooms { if (it.state == ReserveState.Choosing) it.copy(step = RoomsStep.Room, error = null) else it }
            is HotelsEvent.GuestChanged -> updateRooms { sheet ->
                // Once shown, problems clear as the guest fixes them.
                val problems = if (sheet.problems.isEmpty()) sheet.problems else validateContact(event.guest)
                sheet.copy(details = sheet.details.copy(guest = event.guest), problems = problems)
            }
            is HotelsEvent.SelectArrival -> updateRooms { it.copy(details = it.details.copy(arrival = event.arrival)) }
            is HotelsEvent.ToggleRequest -> updateRooms { sheet ->
                val requests = sheet.details.requests.let { if (event.request in it) it - event.request else it + event.request }
                sheet.copy(details = sheet.details.copy(requests = requests))
            }
            is HotelsEvent.NoteChanged -> updateRooms { it.copy(details = it.details.copy(note = event.note.take(StayDetails.MAX_NOTE))) }
            HotelsEvent.Reserve -> reserve()
            HotelsEvent.DismissRooms -> closeRooms()
            HotelsEvent.OpenMyTrips -> {
                closeRooms()
                send(HotelsEffect.OpenMyTrips)
            }

            HotelsEvent.Retry -> _uiState.value.stay?.let { load(it.city) }
        }
    }

    private fun load(city: String) {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = getHotels(city)) {
                is Outcome.Failure -> _uiState.update { it.copy(loading = false, error = result.error) }
                is Outcome.Success -> {
                    all = result.value
                    _uiState.update { it.copy(loading = false, hotels = filterHotels(all, it.filter)) }
                }
            }
        }
    }

    private fun applyStay() {
        val draft = _uiState.value.draft ?: return
        val problem = validateStay(draft.stay)
        if (problem != null) {
            updateDraft { it.copy(problem = problem) }
            return
        }
        val cityChanged = draft.stay.city != _uiState.value.stay?.city
        _uiState.update { it.copy(stay = draft.stay, draft = null) }
        if (cityChanged) load(draft.stay.city)
    }

    private fun openRooms(hotel: Hotel) {
        val stay = _uiState.value.stay ?: return
        val rooms = priceStay.rooms(hotel, stay.nights, stay.guests)
        val first = rooms.firstOrNull()?.room ?: return
        _uiState.update { it.copy(rooms = RoomsSheet(hotel, rooms, first, details = StayDetails(prefill ?: ContactDetails()))) }
        if (prefill == null) {
            viewModelScope.launch {
                val contact = prefillContact().also { prefill = it }
                // Fill only what the guest hasn't typed yet.
                updateRooms { sheet ->
                    val guest = sheet.details.guest
                    val filled = ContactDetails(
                        name = guest.name.ifBlank { contact.name },
                        email = guest.email.ifBlank { contact.email },
                        phone = guest.phone.ifBlank { contact.phone },
                    )
                    sheet.copy(details = sheet.details.copy(guest = filled))
                }
            }
        }
    }

    private fun closeRooms() {
        if (_uiState.value.rooms?.state is ReserveState.Reserved) attemptId = newAttempt()
        _uiState.update { it.copy(rooms = null) }
    }

    private fun reserve() {
        val sheet = _uiState.value.rooms ?: return
        val stay = _uiState.value.stay ?: return
        if (sheet.state != ReserveState.Choosing) return
        val problems = validateContact(sheet.details.guest)
        if (problems.isNotEmpty()) {
            updateRooms { it.copy(step = RoomsStep.Details, problems = problems) }
            return
        }
        updateRooms { it.copy(state = ReserveState.Reserving, error = null, problems = emptySet()) }
        viewModelScope.launch {
            when (val result = reserveHotel(sheet.hotel, sheet.selected, stay, sheet.details, attemptId)) {
                is Outcome.Success -> updateRooms { it.copy(state = ReserveState.Reserved(result.value)) }
                is Outcome.Failure -> updateRooms { it.copy(state = ReserveState.Choosing, error = result.error) }
            }
        }
    }

    /** Cities Skypass flies to, one row each, matched by city, country or airport code. */
    private fun cities(text: String): List<CityOption> =
        searchAirports(airports, text).map { CityOption(it.city, it.country) }.distinct().take(MAX_CITIES)

    private fun updateDraft(transform: (StayDraft) -> StayDraft) =
        _uiState.update { s -> s.draft?.let { s.copy(draft = transform(it)) } ?: s }

    private fun updateRooms(transform: (RoomsSheet) -> RoomsSheet) =
        _uiState.update { s -> s.rooms?.let { s.copy(rooms = transform(it)) } ?: s }

    private fun newAttempt(): String = UUID.randomUUID().toString().also { savedStateHandle[KEY_ATTEMPT] = it }

    private fun send(effect: HotelsEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val KEY_FILTER = "filter"
        const val KEY_ATTEMPT = "attempt"
        const val MAX_CITIES = 30
    }
}
