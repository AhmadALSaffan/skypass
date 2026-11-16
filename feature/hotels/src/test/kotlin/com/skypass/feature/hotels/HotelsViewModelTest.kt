package com.skypass.feature.hotels

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.skypass.core.domain.usecase.FilterHotelsUseCase
import com.skypass.core.domain.usecase.GetAirportsUseCase
import com.skypass.core.domain.usecase.GetHotelsUseCase
import com.skypass.core.domain.usecase.ObserveSavedHotelsUseCase
import com.skypass.core.domain.usecase.PriceStayUseCase
import com.skypass.core.domain.usecase.ReserveHotelUseCase
import com.skypass.core.domain.usecase.ResolveStayUseCase
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.domain.usecase.SetHotelSavedUseCase
import com.skypass.core.domain.usecase.StayProblem
import com.skypass.core.domain.usecase.ValidateStayUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.HotelFilter
import com.skypass.core.model.ReservationError
import com.skypass.core.model.RoomType
import com.skypass.core.testing.FakeAirportRepository
import com.skypass.core.testing.FakeHotelRepository
import com.skypass.core.testing.FakeSavedHotelsRepository
import com.skypass.core.testing.FakeTripRepository
import com.skypass.core.testing.MainDispatcherExtension
import com.skypass.core.testing.TestHotels
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HotelsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
    private val hotels = FakeHotelRepository()
    private val saved = FakeSavedHotelsRepository()
    private val trips = FakeTripRepository().apply { emit(null) }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = HotelsViewModel(
        savedStateHandle = handle,
        resolveStay = ResolveStayUseCase(trips, clock),
        getHotels = GetHotelsUseCase(hotels),
        filterHotels = FilterHotelsUseCase(),
        priceStay = PriceStayUseCase(),
        validateStay = ValidateStayUseCase(clock),
        reserveHotel = ReserveHotelUseCase(hotels),
        getAirports = GetAirportsUseCase(FakeAirportRepository()),
        searchAirports = SearchAirportsUseCase(),
        observeSaved = ObserveSavedHotelsUseCase(saved),
        setSaved = SetHotelSavedUseCase(saved),
        clock = clock,
    )

    @Test
    fun `shows the board's Auckland stays for 20 to 27 Nov`() {
        val state = viewModel().uiState.value
        assertEquals("Auckland", state.stay?.city)
        assertEquals("20–27 Nov", state.stay?.dateRange())
        assertEquals(4, state.hotels.size)
        assertFalse(state.loading)
    }

    @Test
    fun `chips filter, and the chip survives process death`() {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.onEvent(HotelsEvent.SelectFilter(HotelFilter.FiveStar))
        assertEquals(listOf("Pōhutukawa Lodge", "Waitematā House"), vm.uiState.value.hotels.map { it.name })
        assertEquals(HotelFilter.FiveStar, viewModel(handle).uiState.value.filter)
    }

    @Test
    fun `hearts toggle`() {
        val vm = viewModel()
        vm.onEvent(HotelsEvent.ToggleSaved(TestHotels.KOWHAI))
        assertTrue(TestHotels.KOWHAI.id in vm.uiState.value.savedIds)
        vm.onEvent(HotelsEvent.ToggleSaved(TestHotels.KOWHAI))
        assertFalse(TestHotels.KOWHAI.id in vm.uiState.value.savedIds)
    }

    @Test
    fun `changing the city loads its stays, and bad dates are explained`() {
        hotels.hotels = hotels.hotels + ("London" to listOf(TestHotels.KOWHAI.copy(id = "l1", city = "London", name = "The Grand London")))
        val vm = viewModel()
        vm.onEvent(HotelsEvent.EditStay)
        vm.onEvent(HotelsEvent.PickCity)
        vm.onEvent(HotelsEvent.CityQueryChanged("lond"))
        assertEquals(listOf("London"), vm.uiState.value.draft!!.cities.map { it.city })
        vm.onEvent(HotelsEvent.CityPicked(vm.uiState.value.draft!!.cities.single()))

        val draft = vm.uiState.value.draft!!.stay
        vm.onEvent(HotelsEvent.UpdateDraft(draft.copy(checkOut = draft.checkIn)))
        vm.onEvent(HotelsEvent.ApplyStay)
        assertEquals(StayProblem.NoNights, vm.uiState.value.draft!!.problem)

        vm.onEvent(HotelsEvent.UpdateDraft(draft.copy(checkOut = draft.checkIn.plusDays(3), guests = 2)))
        vm.onEvent(HotelsEvent.ApplyStay)
        assertNull(vm.uiState.value.draft)
        assertEquals("London", vm.uiState.value.stay?.city)
        assertEquals(3, vm.uiState.value.stay?.nights)
        assertEquals(listOf("The Grand London"), vm.uiState.value.hotels.map { it.name })
    }

    @Test
    fun `reserving books once and shows the reference`() {
        val vm = viewModel()
        vm.onEvent(HotelsEvent.ViewRooms(TestHotels.KOWHAI))
        val sheet = vm.uiState.value.rooms!!
        assertEquals(listOf(RoomType.Standard, RoomType.Deluxe, RoomType.Suite), sheet.rooms.map { it.room })
        assertEquals(1323, sheet.selectedPrice?.totalUsd)

        vm.onEvent(HotelsEvent.SelectRoom(RoomType.Deluxe))
        vm.onEvent(HotelsEvent.Reserve)
        val reserved = vm.uiState.value.rooms!!.state as ReserveState.Reserved
        assertEquals(1785, reserved.reservation.totalUsd)
        vm.onEvent(HotelsEvent.Reserve) // a second tap does nothing
        assertEquals(1, hotels.reservations.size)

        // A new reservation after Done is a new attempt.
        vm.onEvent(HotelsEvent.DismissRooms)
        vm.onEvent(HotelsEvent.ViewRooms(TestHotels.WAITEMATA))
        vm.onEvent(HotelsEvent.Reserve)
        assertEquals(2, hotels.reservations.size)
    }

    @Test
    fun `a failed reservation is explained and can be retried with the same attempt`() {
        hotels.reserveError = ReservationError.Network
        val vm = viewModel()
        vm.onEvent(HotelsEvent.ViewRooms(TestHotels.KOWHAI))
        vm.onEvent(HotelsEvent.Reserve)
        assertEquals(ReservationError.Network, vm.uiState.value.rooms!!.error)
        assertEquals(ReserveState.Choosing, vm.uiState.value.rooms!!.state)

        hotels.reserveError = null
        vm.onEvent(HotelsEvent.Reserve)
        assertTrue(vm.uiState.value.rooms!!.state is ReserveState.Reserved)
        assertEquals(1, hotels.reservations.size)
    }

    @Test
    fun `the map opens a hotels search for the city`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(HotelsEvent.OpenMap)
            assertEquals(HotelsEffect.OpenMap("hotels in Auckland"), awaitItem())
        }
    }

    @Test
    fun `failure shows retry`() {
        hotels.failure = AppError.Network
        val vm = viewModel()
        assertEquals(AppError.Network, vm.uiState.value.error)
        hotels.failure = null
        vm.onEvent(HotelsEvent.Retry)
        assertEquals(4, vm.uiState.value.hotels.size)
    }
}
