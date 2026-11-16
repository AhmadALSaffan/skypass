package com.skypass.feature.trips

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.skypass.core.domain.repository.CancelError
import com.skypass.core.domain.usecase.CancelReservationUseCase
import com.skypass.core.domain.usecase.GetMyTripsUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.ReservationStatus
import com.skypass.core.testing.FakeMyTripsRepository
import com.skypass.core.testing.MainDispatcherExtension
import com.skypass.core.testing.TestTrips
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TripsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
    private val repo = FakeMyTripsRepository()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        TripsViewModel(handle, GetMyTripsUseCase(repo, clock), CancelReservationUseCase(repo), clock)

    @Test
    fun `opens on the tab it was asked for, and remembers the one picked`() {
        assertEquals(TripsTab.Stays, viewModel(SavedStateHandle(mapOf("tab" to "Stays"))).uiState.value.tab)
        val handle = SavedStateHandle()
        viewModel(handle).onEvent(TripsEvent.SelectTab(TripsTab.Cars))
        assertEquals(TripsTab.Cars, viewModel(handle).uiState.value.tab)
    }

    @Test
    fun `loads every kind of booking`() {
        val trips = viewModel().uiState.value.trips!!
        assertEquals(listOf("SKP7Q2"), trips.flights.upcoming.map { it.trip.pnr })
        assertEquals(listOf("SKHK7M"), trips.stays.upcoming.map { it.reference })
        assertEquals(listOf("SKHP2A"), trips.stays.past.map { it.reference })
        assertEquals(listOf("SKC4XR"), trips.cars.upcoming.map { it.reference })
    }

    @Test
    fun `a flight opens its boarding pass`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(TripsEvent.OpenFlight(TestTrips.FLIGHT))
            assertEquals(TripsEffect.BoardingPass("b-1"), awaitItem())
        }
    }

    @Test
    fun `cancelling asks first, then shows the booking as cancelled`() {
        val vm = viewModel()
        vm.onEvent(TripsEvent.OpenStay(TestTrips.STAY))
        vm.onEvent(TripsEvent.AskCancel)
        assertEquals(CancelStep.Confirming, vm.uiState.value.cancel)
        vm.onEvent(TripsEvent.KeepBooking)
        assertEquals(CancelStep.None, vm.uiState.value.cancel)

        vm.onEvent(TripsEvent.AskCancel)
        vm.onEvent(TripsEvent.ConfirmCancel)
        assertEquals(listOf("SKHK7M"), repo.cancelled)
        val open = vm.uiState.value.open as OpenBooking.Stay
        assertEquals(ReservationStatus.Cancelled, open.booking.status)
        assertEquals(listOf("SKHK7M", "SKHP2A"), vm.uiState.value.trips!!.stays.past.map { it.reference })
    }

    @Test
    fun `a refused cancellation is explained and the booking stays`() {
        repo.cancelError = CancelError.NotCancellable
        val vm = viewModel()
        vm.onEvent(TripsEvent.OpenCar(TestTrips.CAR))
        vm.onEvent(TripsEvent.AskCancel)
        vm.onEvent(TripsEvent.ConfirmCancel)
        assertEquals(CancelError.NotCancellable, vm.uiState.value.cancelError)
        assertEquals(CancelStep.Confirming, vm.uiState.value.cancel)
        assertEquals(ReservationStatus.Confirmed, (vm.uiState.value.open as OpenBooking.Car).booking.status)
    }

    @Test
    fun `directions and the empty-state button go to the right place`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(TripsEvent.OpenStay(TestTrips.STAY))
            vm.onEvent(TripsEvent.Directions)
            assertEquals(TripsEffect.Directions("Kōwhai Airport Inn, Auckland"), awaitItem())
            vm.onEvent(TripsEvent.CloseDetail)
            vm.onEvent(TripsEvent.SelectTab(TripsTab.Cars))
            vm.onEvent(TripsEvent.FindMore)
            assertEquals(TripsEffect.FindCars, awaitItem())
        }
        assertNull(vm.uiState.value.open)
    }

    @Test
    fun `offline shows retry`() {
        repo.failure = AppError.Network
        val vm = viewModel()
        assertEquals(AppError.Network, vm.uiState.value.error)
        repo.failure = null
        vm.onEvent(TripsEvent.Retry)
        assertNull(vm.uiState.value.error)
    }
}
