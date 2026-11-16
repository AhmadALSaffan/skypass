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
import com.skypass.core.domain.usecase.ReserveCarUseCase
import com.skypass.core.domain.usecase.ResolveRentalUseCase
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.domain.usecase.ValidateRentalUseCase
import com.skypass.core.domain.usecase.rentalPlace
import com.skypass.core.model.Airport
import com.skypass.core.model.AppError
import com.skypass.core.model.Car
import com.skypass.core.model.CarReservation
import com.skypass.core.model.CarType
import com.skypass.core.model.Outcome
import com.skypass.core.model.Rental
import com.skypass.core.model.RentalPlace
import com.skypass.core.model.ReservationError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

sealed interface CarReserveState {
    data object Idle : CarReserveState
    data object Reserving : CarReserveState
    data class Reserved(val reservation: CarReservation, val car: Car) : CarReserveState
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
    data object Reserve : CarsEvent
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
    clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CarsUiState(
            type = savedStateHandle.get<String>(KEY_TYPE)?.let(CarType::valueOf) ?: CarType.Compact,
            firstBookableDate = LocalDate.now(clock),
        ),
    )
    val uiState: StateFlow<CarsUiState> = _uiState.asStateFlow()

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
            CarsEvent.Reserve -> reserve()
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

    private fun reserve() {
        val state = _uiState.value
        val car = state.selected ?: return
        val rental = state.rental ?: return
        if (!state.canReserve) return
        _uiState.update { it.copy(reserve = CarReserveState.Reserving, reserveError = null) }
        viewModelScope.launch {
            when (val result = reserveCar(car, rental, attemptId)) {
                is Outcome.Success -> _uiState.update { it.copy(reserve = CarReserveState.Reserved(result.value, car)) }
                is Outcome.Failure -> _uiState.update { it.copy(reserve = CarReserveState.Idle, reserveError = result.error) }
            }
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
        const val KEY_TYPE = "type"
        const val KEY_ATTEMPT = "attempt"
    }
}
