package com.skypass.feature.saved

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.skypass.core.domain.usecase.GetSavedItemsUseCase
import com.skypass.core.domain.usecase.RequestFlightSearchUseCase
import com.skypass.core.domain.usecase.SetDestinationSavedUseCase
import com.skypass.core.domain.usecase.SetHotelSavedUseCase
import com.skypass.core.domain.usecase.ToggleSavedFlightUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.SearchIntent
import com.skypass.core.testing.FakeProfileRepository
import com.skypass.core.testing.FakeSavedDestinationsRepository
import com.skypass.core.testing.FakeSavedFlightsRepository
import com.skypass.core.testing.FakeSavedHotelsRepository
import com.skypass.core.testing.FakeSavedListRepository
import com.skypass.core.testing.FakeSearchIntentRepository
import com.skypass.core.testing.MainDispatcherExtension
import com.skypass.core.testing.TestSaved
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class SavedViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
    private val list = FakeSavedListRepository()
    private val flights = FakeSavedFlightsRepository()
    private val hotels = FakeSavedHotelsRepository()
    private val destinations = FakeSavedDestinationsRepository()
    private val intents = FakeSearchIntentRepository()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = SavedViewModel(
        savedStateHandle = handle,
        getSaved = GetSavedItemsUseCase(list, FakeProfileRepository()),
        setFlightSaved = ToggleSavedFlightUseCase(flights),
        setHotelSaved = SetHotelSavedUseCase(hotels),
        setDestinationSaved = SetDestinationSavedUseCase(destinations),
        requestFlightSearch = RequestFlightSearchUseCase(intents),
        clock = clock,
    )

    @Test
    fun `loads all three lists, places priced from the home airport`() {
        val items = viewModel().uiState.value.items!!
        assertEquals(2, items.flights.size)
        assertEquals(1, items.hotels.size)
        assertEquals(1, items.destinations.size)
        assertEquals(listOf("JFK"), list.origins)
    }

    @Test
    fun `a saved flight opens booking, a departed one can't be booked`() = runTest {
        val vm = viewModel()
        assertTrue(TestSaved.FLIGHT.isBookable(vm.uiState.value.now))
        assertFalse(TestSaved.DEPARTED.isBookable(vm.uiState.value.now))
        vm.effects.test {
            vm.onEvent(SavedEvent.OpenFlight(TestSaved.DEPARTED))
            vm.onEvent(SavedEvent.OpenFlight(TestSaved.FLIGHT))
            assertEquals(SavedEffect.BookFlight("i-9"), awaitItem())
        }
    }

    @Test
    fun `a stay opens its city, a place asks Home to search flights there`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(SavedEvent.OpenHotel(TestSaved.HOTEL))
            assertEquals(SavedEffect.OpenHotels("Auckland"), awaitItem())
            vm.onEvent(SavedEvent.OpenDestination(TestSaved.DESTINATION))
            assertEquals(SavedEffect.SearchFlights, awaitItem())
        }
        assertEquals(SearchIntent(toCode = "ZQN"), intents.pending.value)
    }

    @Test
    fun `removing un-saves on the server, Undo puts it back in place`() = runTest {
        hotels.setSaved(TestSaved.HOTEL.hotel.id, true)
        val vm = viewModel()
        vm.onEvent(SavedEvent.RemoveHotel(TestSaved.HOTEL))
        assertTrue(vm.uiState.value.items!!.hotels.isEmpty())
        assertTrue(hotels.savedHotelIds().first().isEmpty())

        vm.onEvent(SavedEvent.Undo)
        assertEquals(listOf(TestSaved.HOTEL), vm.uiState.value.items!!.hotels)
        assertEquals(setOf(TestSaved.HOTEL.hotel.id), hotels.savedHotelIds().first())
        assertNull(vm.uiState.value.removed)
    }

    @Test
    fun `a refused removal puts the card back`() {
        flights.fail = true
        val vm = viewModel()
        vm.onEvent(SavedEvent.RemoveFlight(TestSaved.FLIGHT))
        assertEquals(2, vm.uiState.value.items!!.flights.size)
        assertEquals("i-9", vm.uiState.value.items!!.flights.first().instanceId)
    }

    @Test
    fun `the tab survives process death, and empty-tab buttons go to the right place`() = runTest {
        val handle = SavedStateHandle()
        viewModel(handle).onEvent(SavedEvent.SelectTab(SavedTab.Destinations))
        val vm = viewModel(handle)
        assertEquals(SavedTab.Destinations, vm.uiState.value.tab)
        vm.effects.test {
            vm.onEvent(SavedEvent.FindMore)
            assertEquals(SavedEffect.OpenExplore, awaitItem())
        }
    }

    @Test
    fun `offline shows retry`() {
        list.failure = AppError.Network
        val vm = viewModel()
        assertEquals(AppError.Network, vm.uiState.value.error)
        list.failure = null
        vm.onEvent(SavedEvent.Retry)
        assertNull(vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.items!!.hotels.size)
    }
}
