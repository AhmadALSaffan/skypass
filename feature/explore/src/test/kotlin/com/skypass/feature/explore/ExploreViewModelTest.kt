package com.skypass.feature.explore

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.skypass.core.domain.usecase.FilterDestinationsUseCase
import com.skypass.core.domain.usecase.FindNearestAirportUseCase
import com.skypass.core.domain.usecase.GetAirportsUseCase
import com.skypass.core.domain.usecase.GetExploreCatalogUseCase
import com.skypass.core.domain.usecase.ObserveSavedDestinationsUseCase
import com.skypass.core.domain.usecase.RequestFlightSearchUseCase
import com.skypass.core.domain.usecase.SetDestinationSavedUseCase
import com.skypass.core.domain.usecase.ResolveOriginAirportUseCase
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.ExploreCategory
import com.skypass.core.model.GeoPoint
import com.skypass.core.model.SearchIntent
import com.skypass.core.testing.FakeAirportRepository
import com.skypass.core.testing.FakeExploreRepository
import com.skypass.core.testing.FakeLocationRepository
import com.skypass.core.testing.FakeProfileRepository
import com.skypass.core.testing.FakeSavedDestinationsRepository
import com.skypass.core.testing.FakeSearchIntentRepository
import com.skypass.core.testing.MainDispatcherExtension
import com.skypass.core.testing.TestExplore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ExploreViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val explore = FakeExploreRepository()
    private val intents = FakeSearchIntentRepository()
    private val location = FakeLocationRepository()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = ExploreViewModel(
        savedStateHandle = handle,
        getAirports = GetAirportsUseCase(FakeAirportRepository()),
        resolveOrigin = ResolveOriginAirportUseCase(FindNearestAirportUseCase(location), FakeProfileRepository()),
        getCatalog = GetExploreCatalogUseCase(explore),
        filterDestinations = FilterDestinationsUseCase(),
        searchAirports = SearchAirportsUseCase(),
        requestFlightSearch = RequestFlightSearchUseCase(intents),
        observeSaved = ObserveSavedDestinationsUseCase(savedDestinations),
        setSaved = SetDestinationSavedUseCase(savedDestinations),
    )

    private val savedDestinations = FakeSavedDestinationsRepository()

    @Test
    fun `the heart saves a destination for the Saved tab, and a second tap removes it`() {
        val vm = viewModel()
        vm.onEvent(ExploreEvent.ToggleSaved(TestExplore.LISBON))
        assertEquals(setOf("d-2"), vm.uiState.value.savedIds)
        vm.onEvent(ExploreEvent.ToggleSaved(TestExplore.LISBON))
        assertTrue(vm.uiState.value.savedIds.isEmpty())
    }

    @Test
    fun `loads the deal and the grid priced from the profile's airport`() {
        val state = viewModel().uiState.value
        assertFalse(state.loading)
        assertEquals("JFK", state.origin?.code)
        assertEquals(listOf("JFK"), explore.requestedOrigins)
        assertEquals("Fly to Aotearoa this summer", state.deal?.title)
        assertEquals(TestExplore.all, state.destinations)
    }

    @Test
    fun `prices come from the nearest airport when location is on`() {
        location.location = GeoPoint(-36.85, 174.76) // Auckland CBD
        assertEquals("AKL", viewModel().uiState.value.origin?.code)
        assertEquals(listOf("AKL"), explore.requestedOrigins)
    }

    @Test
    fun `chips and search text filter the grid together`() {
        val vm = viewModel()
        vm.onEvent(ExploreEvent.CategorySelected(ExploreCategory.Cities))
        assertEquals(listOf("Lisbon", "London"), vm.uiState.value.destinations.map { it.city })

        vm.onEvent(ExploreEvent.QueryChanged("portugal"))
        assertEquals(listOf("Lisbon"), vm.uiState.value.destinations.map { it.city })

        vm.onEvent(ExploreEvent.ClearQuery)
        vm.onEvent(ExploreEvent.CategorySelected(ExploreCategory.All))
        assertEquals(5, vm.uiState.value.destinations.size)
    }

    @Test
    fun `searching also offers airports that aren't in the grid`() {
        val vm = viewModel()
        vm.onEvent(ExploreEvent.QueryChanged("auck"))
        val state = vm.uiState.value
        assertTrue(state.destinations.isEmpty())
        assertEquals(listOf("AKL"), state.airportMatches.map { it.code })
        assertFalse(state.isEmpty)

        vm.onEvent(ExploreEvent.QueryChanged("zzzz"))
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test
    fun `the search and chip survive process death`() {
        val handle = SavedStateHandle(mapOf("query" to "lis", "category" to "Cities"))
        val state = viewModel(handle).uiState.value
        assertEquals(ExploreCategory.Cities, state.category)
        assertEquals(listOf("Lisbon"), state.destinations.map { it.city })
    }

    @Test
    fun `a destination asks Home for a search and opens Flights`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(ExploreEvent.DestinationTapped(TestExplore.QUEENSTOWN))
            assertEquals(ExploreEffect.OpenFlights, awaitItem())
            assertEquals(SearchIntent(toCode = "ZQN"), intents.pending.value)

            vm.onEvent(ExploreEvent.DealTapped)
            assertEquals(ExploreEffect.OpenFlights, awaitItem())
            assertEquals(SearchIntent(toCode = "AKL", fromCode = "JFK"), intents.pending.value)

            vm.onEvent(ExploreEvent.HotelsTapped)
            assertEquals(ExploreEffect.OpenHotels, awaitItem())
            vm.onEvent(ExploreEvent.CarsTapped)
            assertEquals(ExploreEffect.OpenCars, awaitItem())
        }
    }

    @Test
    fun `failure shows retry, and retry recovers`() {
        explore.failure = AppError.Network
        val vm = viewModel()
        assertEquals(AppError.Network, vm.uiState.value.error)
        explore.failure = null
        vm.onEvent(ExploreEvent.Retry)
        assertNull(vm.uiState.value.error)
        assertEquals(5, vm.uiState.value.destinations.size)
    }
}
