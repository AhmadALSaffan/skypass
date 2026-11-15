package com.skypass.feature.results

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.skypass.core.domain.usecase.FilterFlightsUseCase
import com.skypass.core.domain.usecase.GetPriceStripUseCase
import com.skypass.core.domain.usecase.ObserveSavedFlightsUseCase
import com.skypass.core.domain.usecase.SearchFlightsUseCase
import com.skypass.core.domain.usecase.SortFlightsUseCase
import com.skypass.core.domain.usecase.ToggleSavedFlightUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.Cabin
import com.skypass.core.model.FlightFilters
import com.skypass.core.model.SortOrder
import com.skypass.core.model.TripLeg
import com.skypass.core.testing.FakeFlightSearchRepository
import com.skypass.core.testing.FakeSavedFlightsRepository
import com.skypass.core.testing.MainDispatcherExtension
import com.skypass.core.testing.TestFlights
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ResultsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val flights = FakeFlightSearchRepository()
    private val saved = FakeSavedFlightsRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))

    private fun viewModel(
        returnDate: String? = null,
        leg: TripLeg = TripLeg.Outbound,
        outbound: String? = null,
        adults: Int = 1,
    ) = ResultsViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                "from" to "JFK", "to" to "LAX", "date" to "2026-11-20", "returnDate" to returnDate,
                "adults" to adults, "cabin" to "economy", "leg" to leg, "outboundInstanceId" to outbound,
            ),
        ),
        searchFlights = SearchFlightsUseCase(flights),
        getPriceStrip = GetPriceStripUseCase(flights),
        sortFlights = SortFlightsUseCase(),
        filterFlights = FilterFlightsUseCase(),
        observeSaved = ObserveSavedFlightsUseCase(saved),
        toggleSaved = ToggleSavedFlightUseCase(saved),
        clock = clock,
    )

    @Test
    fun `loads the day cheapest first with the first card open`() {
        val state = viewModel().uiState.value
        assertEquals(5, state.visible.size)
        assertEquals("nz5", state.visible.first().instanceId)
        assertEquals("nz5", state.expandedId)
        assertEquals(5, state.strip.size)
        assertEquals(Cabin.Economy, state.cabin)
    }

    @Test
    fun `sorting by fastest reorders`() {
        val vm = viewModel()
        vm.onEvent(ResultsEvent.SelectSort(SortOrder.Fastest))
        assertEquals("b6", vm.uiState.value.visible.first().instanceId)
    }

    @Test
    fun `filters narrow the list and reset restores it`() {
        val vm = viewModel()
        vm.onEvent(ResultsEvent.UpdateFilters(FlightFilters(nonstopOnly = true)))
        assertEquals(3, vm.uiState.value.visible.size)
        assertEquals(1, vm.uiState.value.filters.activeCount)
        vm.onEvent(ResultsEvent.ResetFilters)
        assertEquals(5, vm.uiState.value.visible.size)
    }

    @Test
    fun `tapping an open card closes it`() {
        val vm = viewModel()
        vm.onEvent(ResultsEvent.ToggleExpanded("nz5"))
        assertNull(vm.uiState.value.expandedId)
        vm.onEvent(ResultsEvent.ToggleExpanded("aa"))
        assertEquals("aa", vm.uiState.value.expandedId)
    }

    @Test
    fun `another day searches again, past days are ignored`() {
        val vm = viewModel()
        vm.onEvent(ResultsEvent.SelectDate(LocalDate.of(2026, 11, 21)))
        assertEquals(LocalDate.of(2026, 11, 21), vm.uiState.value.selectedDate)
        assertTrue(vm.uiState.value.visible.isEmpty())
        vm.onEvent(ResultsEvent.SelectDate(LocalDate.of(2026, 11, 10)))
        assertEquals(LocalDate.of(2026, 11, 21), vm.uiState.value.selectedDate)
        assertEquals(listOf(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21)), flights.searches)
    }

    @Test
    fun `failure shows the error, retry recovers`() {
        flights.failure = AppError.Network
        val vm = viewModel()
        assertEquals(AppError.Network, vm.uiState.value.error)
        flights.failure = null
        vm.onEvent(ResultsEvent.Retry)
        assertNull(vm.uiState.value.error)
        assertEquals(5, vm.uiState.value.visible.size)
    }

    @Test
    fun `bookmark toggles saved`() {
        val vm = viewModel()
        vm.onEvent(ResultsEvent.ToggleSaved("nz5"))
        assertTrue("nz5" in vm.uiState.value.savedIds)
        vm.onEvent(ResultsEvent.ToggleSaved("nz5"))
        assertTrue(vm.uiState.value.savedIds.isEmpty())
    }

    @Test
    fun `one way selection opens booking`() = runTest {
        val vm = viewModel(adults = 2)
        vm.effects.test {
            vm.onEvent(ResultsEvent.SelectFlight("nz5"))
            val effect = awaitItem() as ResultsEffect.OpenBooking
            assertEquals("nz5", effect.selection.outboundInstanceId)
            assertNull(effect.selection.returnInstanceId)
            assertEquals(2, effect.selection.adults)
        }
    }

    @Test
    fun `round trip goes outbound, then return, then booking with both`() = runTest {
        val outbound = viewModel(returnDate = "2026-11-27")
        outbound.effects.test {
            outbound.onEvent(ResultsEvent.SelectFlight("nz5"))
            val next = (awaitItem() as ResultsEffect.PickReturn).destination
            assertEquals("LAX", next.from)
            assertEquals("JFK", next.to)
            assertEquals("2026-11-27", next.date)
            assertEquals(TripLeg.Return, next.leg)
            assertEquals("nz5", next.outboundInstanceId)
        }

        val back = viewModel(returnDate = "2026-11-27", leg = TripLeg.Return, outbound = "nz5")
        back.effects.test {
            back.onEvent(ResultsEvent.SelectFlight("aa"))
            val effect = awaitItem() as ResultsEffect.OpenBooking
            assertEquals("nz5", effect.selection.outboundInstanceId)
            assertEquals("aa", effect.selection.returnInstanceId)
        }
    }
}
