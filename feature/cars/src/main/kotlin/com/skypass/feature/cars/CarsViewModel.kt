package com.skypass.feature.cars

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.DropOffPlacesUseCase
import com.skypass.core.domain.usecase.FilterCarsUseCase
import com.skypass.core.domain.usecase.GetAirportsUseCase
import com.skypass.core.domain.usecase.GetCarsUseCase
import com.skypass.core.domain.usecase.PriceRentalUseCase
import com.skypass.core.domain.usecase.RentalPrice
import com.skypass.core.domain.usecase.RentalProblem
import com.skypass.core.domain.usecase.ContactProblem
import com.skypass.core.domain.usecase.PrefillDriverUseCase
import com.skypass.core.domain.usecase.ReserveCarUseCase
import com.skypass.core.domain.usecase.ValidateContactUseCase
import com.skypass.core.domain.usecase.ResolveRentalUseCase
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.domain.usecase.ValidateRentalUseCase
import com.skypass.core.domain.usecase.rentalPlace
import com.skypass.core.model.Airport
import com.skypass.core.model.AppError
import com.skypass.core.model.Car
import com.skypass.core.model.CarReservation
import com.skypass.core.model.CarExtra
import com.skypass.core.model.CarType
import com.skypass.core.model.ContactDetails
import com.skypass.core.model.DriverAge
import com.skypass.core.model.DriverDetails
import com.skypass.core.model.Outcome
import com.skypass.core.model.Rental
import com.skypass.core.model.RentalPlace
import com.skypass.core.model.ReservationError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Which sheet is open over the trip card. */
sealed interface PlacePicker {
    /** Search every airport Skypass flies to. */
    data class Pickup(val query: String = "", val results: List<Airport> = emptyList()) : PlacePicker

    /** The pick-up airport, the city centre or another airport in the country. */
    data class Dropoff(val places: List<RentalPlace>) : PlacePicker
}

/** "Driver & extras": the step between Reserve car and the booking. */
data class CheckoutSheet(
    val details: DriverDetails,
    val price: RentalPrice,
    /** Shown only after the traveller tries to book, so the form doesn't open full of red. */
    val problems: Set<ContactProblem> = emptySet(),
    val flightInvalid: Boolean = false,
)

sealed interface CarsEffect {
    /** The car was booked: show it in My trips. */
    data object OpenMyTrips : CarsEffect
}

sealed interface CarReserveState {
    data object Idle : CarReserveState
    data object Reserving : CarReserveState
    data class Reserved(val reservation: CarReservation, val car: Car, val details: DriverDetails? = null) : CarReserveState
}

data class CarsUiState(
    val rental: Rental? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val type: CarType = CarType.Compact,
    val cars: List<Car> = emptyList(),
    val selected: Car? = null,
    val price: RentalPrice? = null,
    val picker: PlacePicker? = null,
    val pickingDates: Boolean = false,
    val problem: RentalProblem? = null,
    val reserve: CarReserveState = CarReserveState.Idle,
    val reserveError: ReservationError? = null,
    val checkout: CheckoutSheet? = null,
    val firstBookableDate: LocalDate = LocalDate.now(),
) {
    val isEmpty: Boolean get() = !loading && error == null && cars.isEmpty()
    val canReserve: Boolean get() = selected != null && problem == null && reserve == CarReserveState.Idle
}

sealed interface CarsEvent {
    data class SelectType(val type: CarType) : CarsEvent
    data class SelectCar(val car: Car) : CarsEvent
    data class SetSameReturn(val same: Boolean) : CarsEvent
    data object PickPickup : CarsEvent
    data class PickupQueryChanged(val text: String) : CarsEvent
    data class PickupPicked(val airport: Airport) : CarsEvent
    data object PickDropoff : CarsEvent
    data class DropoffPicked(val place: RentalPlace) : CarsEvent
    data object DismissPicker : CarsEvent
    data object PickDates : CarsEvent
    data class DatesPicked(val pickup: LocalDate, val dropoff: LocalDate) : CarsEvent
    data object DismissDates : CarsEvent
    /** "Reserve car": opens Driver & extras. */
    data object Reserve : CarsEvent
    data class DriverChanged(val driver: ContactDetails) : CarsEvent
    data class SelectAge(val age: DriverAge) : CarsEvent
    data class FlightChanged(val flightNumber: String) : CarsEvent
    data class ToggleExtra(val extra: CarExtra) : CarsEvent
    data object DismissCheckout : CarsEvent
    /** "Confirm booking" on Driver & extras. */
    data object Confirm : CarsEvent
    data object OpenMyTrips : CarsEvent
    data object DismissReserved : CarsEvent
    data object DismissReserveError : CarsEvent
    data object Retry : CarsEvent
}

@HiltViewModel
class CarsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getAirports: GetAirportsUseCase,
    private val searchAirports: SearchAirportsUseCase,
    private val resolveRental: ResolveRentalUseCase,
    private val dropOffPlaces: DropOffPlacesUseCase,
    private val getCars: GetCarsUseCase,
    private val filterCars: FilterCarsUseCase,
    private val priceRental: PriceRentalUseCase,
    private val validateRental: ValidateRentalUseCase,
    private val reserveCar: ReserveCarUseCase,
    private val prefillDriver: PrefillDriverUseCase,
    private val validateContact: ValidateContactUseCase,
    clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CarsUiState(
            type = savedStateHandle.get<String>(KEY_TYPE)?.let(CarType::valueOf) ?: CarType.Compact,
            firstBookableDate = LocalDate.now(clock),
        ),
    )
    val uiState: StateFlow<CarsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CarsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** The driver form as last filled in, so reopening it keeps what was typed. */
    private var lastDetails: DriverDetails? = null

    private var airports: List<Airport> = emptyList()
    private var all: List<Car> = emptyList()

    /** One per reservation, so a retry after a dropped connection never books twice. */
    private val attemptId: String
        get() = savedStateHandle.get<String>(KEY_ATTEMPT) ?: newAttempt()

    init {
        load()
    }

    fun onEvent(event: CarsEvent) {
        when (event) {
            is CarsEvent.SelectType -> {
                savedStateHandle[KEY_TYPE] = event.type.name
                val cars = filterCars(all, event.type)
                // The board selects the first car of the new type.
                setSelection(type = event.type, cars = cars, selected = cars.firstOrNull())
            }
            is CarsEvent.SelectCar -> setSelection(selected = event.car)
            is CarsEvent.SetSameReturn -> updateRental { r ->
                if (event.same) r.copy(dropoff = r.pickup) else r.copy(dropoff = dropOffPlaces(r.pickup, airports).getOrElse(1) { r.pickup })
            }
            CarsEvent.PickPickup -> _uiState.update { it.copy(picker = PlacePicker.Pickup(results = searchAirports(airports, ""))) }
            is CarsEvent.PickupQueryChanged -> _uiState.update { it.copy(picker = PlacePicker.Pickup(event.text, searchAirports(airports, event.text))) }
            is CarsEvent.PickupPicked -> {
                val place = event.airport.rentalPlace()
                _uiState.update { it.copy(picker = null) }
                updateRental { it.copy(pickup = place, dropoff = place) }
                loadCars(event.airport.code)
            }
            CarsEvent.PickDropoff -> _uiState.value.rental?.let { r ->
                _uiState.update { it.copy(picker = PlacePicker.Dropoff(dropOffPlaces(r.pickup, airports))) }
            }
            is CarsEvent.DropoffPicked -> {
                _uiState.update { it.copy(picker = null) }
                updateRental { it.copy(dropoff = event.place) }
            }
            CarsEvent.DismissPicker -> _uiState.update { it.copy(picker = null) }
            CarsEvent.PickDates -> _uiState.update { it.copy(pickingDates = true) }
            is CarsEvent.DatesPicked -> {
                _uiState.update { it.copy(pickingDates = false) }
                updateRental { r ->
                    r.copy(
                        pickupAt = event.pickup.atTime(r.pickupAt.toLocalTime()),
                        dropoffAt = event.dropoff.atTime(r.dropoffAt.toLocalTime()),
                    )
                }
            }
            CarsEvent.DismissDates -> _uiState.update { it.copy(pickingDates = false) }
            CarsEvent.Reserve -> openCheckout()
            is CarsEvent.DriverChanged -> updateCheckout { sheet ->
                val problems = if (sheet.problems.isEmpty()) sheet.problems else validateContact(event.driver)
                sheet.copy(details = sheet.details.copy(driver = event.driver), problems = problems)
            }
            is CarsEvent.SelectAge -> updateCheckout { it.copy(details = it.details.copy(age = event.age)) }
            is CarsEvent.FlightChanged -> updateCheckout { sheet ->
                val flight = event.flightNumber.uppercase().take(MAX_FLIGHT)
                sheet.copy(
                    details = sheet.details.copy(flightNumber = flight),
                    flightInvalid = sheet.flightInvalid && !ReserveCarUseCase.FLIGHT_NUMBER.matches(flight.trim()),
                )
            }
            is CarsEvent.ToggleExtra -> updateCheckout { sheet ->
                val extras = sheet.details.extras.let { if (event.extra in it) it - event.extra else it + event.extra }
                sheet.copy(details = sheet.details.copy(extras = extras))
            }
            CarsEvent.DismissCheckout -> {
                if (_uiState.value.reserve != CarReserveState.Reserving) _uiState.update { it.copy(checkout = null, reserveError = null) }
            }
            CarsEvent.Confirm -> reserve()
            CarsEvent.OpenMyTrips -> {
                newAttempt()
                _uiState.update { it.copy(reserve = CarReserveState.Idle) }
                viewModelScope.launch { _effects.send(CarsEffect.OpenMyTrips) }
            }
            CarsEvent.DismissReserved -> {
                newAttempt()
                _uiState.update { it.copy(reserve = CarReserveState.Idle) }
            }
            CarsEvent.DismissReserveError -> _uiState.update { it.copy(reserveError = null) }
            CarsEvent.Retry -> load()
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
            val rental = _uiState.value.rental ?: resolveRental(airports)
            _uiState.update { it.copy(rental = rental, problem = validateRental(rental)) }
            loadCars(rental.pickup.airportCode ?: ResolveRentalUseCase.DEFAULT_AIRPORT)
        }
    }

    private fun loadCars(code: String) {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = getCars(code)) {
                is Outcome.Failure -> _uiState.update { it.copy(loading = false, error = result.error) }
                is Outcome.Success -> {
                    all = result.value
                    val type = _uiState.value.type
                    val cars = filterCars(all, type)
                    val keep = _uiState.value.selected?.let { s -> cars.firstOrNull { it.name == s.name } }
                    _uiState.update { it.copy(loading = false) }
                    setSelection(cars = cars, selected = keep ?: cars.firstOrNull())
                }
            }
        }
    }

    private fun openCheckout() {
        val state = _uiState.value
        val car = state.selected ?: return
        val rental = state.rental ?: return
        if (!state.canReserve) return
        val details = lastDetails ?: DriverDetails(ContactDetails())
        _uiState.update { it.copy(checkout = CheckoutSheet(details, priceRental(car, rental, details.extras, details.age)), reserveError = null) }
        if (lastDetails == null) {
            viewModelScope.launch {
                val prefilled = prefillDriver(rental.pickup.airportCode)
                // Fill only what the traveller hasn't typed yet.
                updateCheckout { sheet ->
                    val d = sheet.details
                    sheet.copy(
                        details = d.copy(
                            driver = ContactDetails(
                                name = d.driver.name.ifBlank { prefilled.driver.name },
                                email = d.driver.email.ifBlank { prefilled.driver.email },
                                phone = d.driver.phone.ifBlank { prefilled.driver.phone },
                            ),
                            flightNumber = d.flightNumber.ifBlank { prefilled.flightNumber },
                        ),
                    )
                }
            }
        }
    }

    private fun reserve() {
        val state = _uiState.value
        val car = state.selected ?: return
        val rental = state.rental ?: return
        val sheet = state.checkout ?: return
        if (!state.canReserve) return
        val problems = validateContact(sheet.details.driver)
        val flightInvalid = !ReserveCarUseCase.FLIGHT_NUMBER.matches(sheet.details.flightNumber.trim())
        if (problems.isNotEmpty() || flightInvalid) {
            updateCheckout { it.copy(problems = problems, flightInvalid = flightInvalid) }
            return
        }
        _uiState.update { it.copy(reserve = CarReserveState.Reserving, reserveError = null) }
        viewModelScope.launch {
            when (val result = reserveCar(car, rental, sheet.details, attemptId)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(reserve = CarReserveState.Reserved(result.value, car, sheet.details), checkout = null)
                }
                is Outcome.Failure -> _uiState.update { it.copy(reserve = CarReserveState.Idle, reserveError = result.error) }
            }
        }
    }

    /** Edits the open Driver & extras sheet and reprices it. */
    private fun updateCheckout(transform: (CheckoutSheet) -> CheckoutSheet) {
        _uiState.update { s ->
            val sheet = s.checkout?.let(transform) ?: return@update s
            lastDetails = sheet.details
            val price = s.selected?.let { car -> s.rental?.let { priceRental(car, it, sheet.details.extras, sheet.details.age) } } ?: sheet.price
            s.copy(checkout = sheet.copy(price = price))
        }
    }

    private fun setSelection(
        type: CarType = _uiState.value.type,
        cars: List<Car> = _uiState.value.cars,
        selected: Car? = _uiState.value.selected,
    ) {
        _uiState.update { s ->
            s.copy(
                type = type,
                cars = cars,
                selected = selected,
                price = selected?.let { car -> s.rental?.let { priceRental(car, it) } },
            )
        }
    }

    private fun updateRental(transform: (Rental) -> Rental) {
        _uiState.update { s ->
            val rental = s.rental?.let(transform) ?: return@update s
            s.copy(
                rental = rental,
                problem = validateRental(rental),
                price = s.selected?.let { priceRental(it, rental) },
            )
        }
    }

    private fun newAttempt(): String = UUID.randomUUID().toString().also { savedStateHandle[KEY_ATTEMPT] = it }

    private companion object {
        const val MAX_FLIGHT = 8
        const val KEY_TYPE = "type"
        const val KEY_ATTEMPT = "attempt"
    }
}
