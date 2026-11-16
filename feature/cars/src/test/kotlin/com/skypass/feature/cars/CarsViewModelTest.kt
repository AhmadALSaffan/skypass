package com.skypass.feature.cars

import androidx.lifecycle.SavedStateHandle
import com.skypass.core.domain.usecase.DropOffPlacesUseCase
import com.skypass.core.domain.usecase.FilterCarsUseCase
import com.skypass.core.domain.usecase.GetAirportsUseCase
import com.skypass.core.domain.usecase.GetCarsUseCase
import com.skypass.core.domain.usecase.PriceRentalUseCase
import com.skypass.core.domain.usecase.RentalProblem
import com.skypass.core.domain.usecase.ReserveCarUseCase
import com.skypass.core.domain.usecase.ResolveRentalUseCase
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.domain.usecase.ValidateRentalUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.CarType
import com.skypass.core.model.ReservationError
import com.skypass.core.testing.FakeAirportRepository
import com.skypass.core.testing.FakeCarRepository
import com.skypass.core.testing.FakeTripRepository
import com.skypass.core.testing.MainDispatcherExtension
import com.skypass.core.testing.TestAirports
import com.skypass.core.testing.TestCars
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CarsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
    private val cars = FakeCarRepository()
    private val trips = FakeTripRepository().apply { emit(null) }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = CarsViewModel(
        savedStateHandle = handle,
        getAirports = GetAirportsUseCase(FakeAirportRepository()),
        searchAirports = SearchAirportsUseCase(),
        resolveRental = ResolveRentalUseCase(trips, clock),
        dropOffPlaces = DropOffPlacesUseCase(),
        getCars = GetCarsUseCase(cars),
        filterCars = FilterCarsUseCase(),
        priceRental = PriceRentalUseCase(),
        validateRental = ValidateRentalUseCase(clock),
        reserveCar = ReserveCarUseCase(cars),
        clock = clock,
    )

    @Test
    fun `opens on the board with Compact, Kia Cerato selected, 287 dollars for 7 days`() {
        val state = viewModel().uiState.value
        assertEquals("Auckland Airport (AKL)", state.rental?.pickup?.label)
        assertTrue(state.rental!!.sameReturn)
        assertEquals(listOf("Kia Cerato", "Nissan Note"), state.cars.map { it.name })
        assertEquals(TestCars.CERATO, state.selected)
        assertEquals(287, state.price?.totalUsd)
        assertEquals(7, state.price?.days)
    }

    @Test
    fun `a type selects its first car, and the type survives process death`() {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.onEvent(CarsEvent.SelectType(CarType.Electric))
        assertEquals(TestCars.MODEL_3, vm.uiState.value.selected)
        vm.onEvent(CarsEvent.SelectCar(TestCars.MODEL_S))
        assertEquals(784, vm.uiState.value.price?.totalUsd) // $112 × 7
        assertEquals(CarType.Electric, viewModel(handle).uiState.value.type)
    }

    @Test
    fun `turning off same location picks the city centre and adds the fee`() {
        val vm = viewModel()
        vm.onEvent(CarsEvent.SetSameReturn(false))
        assertEquals("Auckland CBD, Queen St", vm.uiState.value.rental?.dropoff?.label)
        assertEquals(336, vm.uiState.value.price?.totalUsd)

        vm.onEvent(CarsEvent.PickDropoff)
        val places = (vm.uiState.value.picker as PlacePicker.Dropoff).places
        vm.onEvent(CarsEvent.DropoffPicked(places.first { it.airportCode == "ZQN" }))
        assertEquals("Queenstown Airport (ZQN)", vm.uiState.value.rental?.dropoff?.label)

        vm.onEvent(CarsEvent.SetSameReturn(true))
        assertTrue(vm.uiState.value.rental!!.sameReturn)
        assertEquals(287, vm.uiState.value.price?.totalUsd)
    }

    @Test
    fun `a new pick-up airport reloads its cars and keeps the same model`() {
        cars.cars = cars.cars + ("LHR" to TestCars.auckland.map { it.copy(id = "l-${it.id}", locationCode = "LHR", pricePerDayUsd = it.pricePerDayUsd + 10) })
        val vm = viewModel()
        vm.onEvent(CarsEvent.PickPickup)
        vm.onEvent(CarsEvent.PickupQueryChanged("heath"))
        assertEquals(listOf("LHR"), (vm.uiState.value.picker as PlacePicker.Pickup).results.map { it.code })
        vm.onEvent(CarsEvent.PickupPicked(TestAirports.LHR))
        assertEquals("London Airport (LHR)", vm.uiState.value.rental?.pickup?.label)
        assertEquals("Kia Cerato", vm.uiState.value.selected?.name)
        assertEquals(357, vm.uiState.value.price?.totalUsd) // $51 × 7
    }

    @Test
    fun `dates update the days, and a return before pick-up blocks reserving`() {
        val vm = viewModel()
        vm.onEvent(CarsEvent.DatesPicked(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23)))
        assertEquals(3, vm.uiState.value.price?.days)
        vm.onEvent(CarsEvent.DatesPicked(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20)))
        assertEquals(RentalProblem.DropoffBeforePickup, vm.uiState.value.problem)
        assertFalse(vm.uiState.value.canReserve)
    }

    @Test
    fun `reserving books once, a retry after a failure reuses the attempt`() {
        cars.reserveError = ReservationError.Network
        val vm = viewModel()
        vm.onEvent(CarsEvent.Reserve)
        assertEquals(ReservationError.Network, vm.uiState.value.reserveError)
        assertEquals(CarReserveState.Idle, vm.uiState.value.reserve)

        cars.reserveError = null
        vm.onEvent(CarsEvent.Reserve)
        val reserved = vm.uiState.value.reserve as CarReserveState.Reserved
        assertEquals(287, reserved.reservation.totalUsd)
        assertEquals(1, cars.reservations.size)

        vm.onEvent(CarsEvent.DismissReserved)
        vm.onEvent(CarsEvent.Reserve)
        assertEquals(2, cars.reservations.size)
    }

    @Test
    fun `failure shows retry`() {
        cars.failure = AppError.Network
        val vm = viewModel()
        assertEquals(AppError.Network, vm.uiState.value.error)
        cars.failure = null
        vm.onEvent(CarsEvent.Retry)
        assertEquals(2, vm.uiState.value.cars.size)
    }
}
