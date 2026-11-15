package com.skypass.feature.home

import app.cash.turbine.test
import com.skypass.core.domain.usecase.ConsumeSearchIntentUseCase
import com.skypass.core.domain.usecase.CreateDefaultSearchUseCase
import com.skypass.core.domain.usecase.ObserveSearchIntentUseCase
import com.skypass.core.domain.usecase.FindNearestAirportUseCase
import com.skypass.core.domain.usecase.GetAirportsUseCase
import com.skypass.core.domain.usecase.GetPopularDestinationsUseCase
import com.skypass.core.domain.usecase.GetProfileUseCase
import com.skypass.core.domain.usecase.GreetingUseCase
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.domain.usecase.ObserveUpcomingTripUseCase
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.Cabin
import com.skypass.core.model.Destination
import com.skypass.core.model.GeoPoint
import com.skypass.core.model.Outcome
import com.skypass.core.model.SearchIntent
import com.skypass.core.model.TripType
import com.skypass.core.testing.FakeAirportRepository
import com.skypass.core.testing.FakeAuthRepository
import com.skypass.core.testing.FakeDestinationRepository
import com.skypass.core.testing.FakeLocationRepository
import com.skypass.core.testing.FakeProfileRepository
import com.skypass.core.testing.FakeSearchIntentRepository
import com.skypass.core.testing.FakeTripRepository
import com.skypass.core.testing.MainDispatcherExtension
import com.skypass.core.testing.TestAirports
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

class HomeViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T19:00:00Z"), ZoneId.of("UTC"))
    private val airports = FakeAirportRepository()
    private val trips = FakeTripRepository()
    private val location = FakeLocationRepository()
    private val intents = FakeSearchIntentRepository()
    private val lisbon = Destination("1", "Lisbon", "Portugal", "LHR", "Cities", 412, 0xFFCDEBE6)

    private fun viewModel() = HomeViewModel(
        observeSession = ObserveSessionUseCase(FakeAuthRepository(FakeAuthRepository.TINA)),
        getProfile = GetProfileUseCase(FakeProfileRepository()),
        getAirports = GetAirportsUseCase(airports),
        searchAirports = SearchAirportsUseCase(),
        getPopular = GetPopularDestinationsUseCase(FakeDestinationRepository(popular = mapOf("JFK" to listOf(lisbon)))),
        observeUpcomingTrip = ObserveUpcomingTripUseCase(trips),
        createDefaultSearch = CreateDefaultSearchUseCase(clock),
        findNearestAirport = FindNearestAirportUseCase(location),
        observeSearchIntent = ObserveSearchIntentUseCase(intents),
        consumeSearchIntent = ConsumeSearchIntentUseCase(intents),
        greeting = GreetingUseCase(clock),
        clock = clock,
    )

    @Test
    fun `loads the board's defaults from the profile's home airport`() {
        val state = viewModel().uiState.value
        assertEquals("Tina Anderson", state.travellerName)
        assertEquals("TA", state.initials)
        assertEquals("JFK", state.search?.from?.code)
        assertEquals("LAX", state.search?.to?.code)
        assertEquals(LocalDate.of(2026, 11, 20), state.search?.departDate)
        assertEquals("New York", state.homeCity)
        assertEquals(listOf(lisbon), state.popular)
    }

    @Test
    fun `swap exchanges airports and turns the button`() {
        val vm = viewModel()
        vm.onEvent(HomeEvent.Swap)
        assertEquals("LAX", vm.uiState.value.search?.from?.code)
        assertEquals("JFK", vm.uiState.value.search?.to?.code)
        assertEquals(1, vm.uiState.value.swapTurns)
    }

    @Test
    fun `picking the other side's airport swaps instead of making A to A`() {
        val vm = viewModel()
        vm.onEvent(HomeEvent.OpenAirportPicker(AirportField.To))
        vm.onEvent(HomeEvent.AirportPicked(TestAirports.JFK))
        assertEquals("LAX", vm.uiState.value.search?.from?.code)
        assertEquals("JFK", vm.uiState.value.search?.to?.code)
        assertNull(vm.uiState.value.airportPicker)
    }

    @Test
    fun `add return asks for the date instead of guessing it`() {
        val vm = viewModel()
        vm.onEvent(HomeEvent.AddReturn)
        assertEquals(DateField.Return, vm.uiState.value.datePicker)
        assertNull(vm.uiState.value.search?.returnDate)
        assertEquals(TripType.OneWay, vm.uiState.value.search?.tripType)

        vm.onEvent(HomeEvent.DatePicked(LocalDate.of(2026, 11, 29)))
        assertEquals(TripType.RoundTrip, vm.uiState.value.search?.tripType)
        assertEquals(LocalDate.of(2026, 11, 29), vm.uiState.value.search?.returnDate)
    }

    @Test
    fun `round trip without a return opens the calendar and blocks search`() {
        val vm = viewModel()
        vm.onEvent(HomeEvent.TripTypeSelected(TripType.RoundTrip))
        assertEquals(DateField.Return, vm.uiState.value.datePicker)
        assertNull(vm.uiState.value.search?.returnDate)
        assertFalse(vm.uiState.value.search!!.isValid)

        vm.onEvent(HomeEvent.TripTypeSelected(TripType.OneWay))
        assertTrue(vm.uiState.value.search!!.isValid)
    }

    @Test
    fun `cabin comes from the dropdown`() {
        val vm = viewModel()
        vm.onEvent(HomeEvent.OpenCabinMenu)
        assertTrue(vm.uiState.value.cabinMenuOpen)
        vm.onEvent(HomeEvent.CabinSelected(Cabin.Business))
        assertEquals(Cabin.Business, vm.uiState.value.search?.cabin)
        assertFalse(vm.uiState.value.cabinMenuOpen)
    }

    @Test
    fun `from defaults to the nearest airport when location is allowed`() {
        location.location = GeoPoint(-36.85, 174.76) // central Auckland
        val vm = viewModel()
        assertEquals("AKL", vm.uiState.value.search?.from?.code)
        assertTrue(vm.uiState.value.fromIsNearby)
        assertEquals("Auckland", vm.uiState.value.homeCity)
    }

    @Test
    fun `location arriving later never overrides a from the user picked`() {
        val vm = viewModel()
        vm.onEvent(HomeEvent.OpenAirportPicker(AirportField.From))
        vm.onEvent(HomeEvent.AirportPicked(TestAirports.LHR))
        location.location = GeoPoint(-36.85, 174.76)
        vm.onEvent(HomeEvent.LocationPermissionGranted)
        assertEquals("LHR", vm.uiState.value.search?.from?.code)
        assertFalse(vm.uiState.value.fromIsNearby)
    }

    @Test
    fun `moving departure past the return pushes the return`() {
        val vm = viewModel()
        vm.onEvent(HomeEvent.AddReturn)
        vm.onEvent(HomeEvent.DatePicked(LocalDate.of(2026, 11, 27)))
        vm.onEvent(HomeEvent.OpenDatePicker(DateField.Depart))
        vm.onEvent(HomeEvent.DatePicked(LocalDate.of(2026, 12, 1)))
        assertEquals(LocalDate.of(2026, 12, 8), vm.uiState.value.search?.returnDate)
    }

    @Test
    fun `adults stay within 1 to 9 and cabin cycles`() {
        val vm = viewModel()
        vm.onEvent(HomeEvent.RemoveAdult)
        assertEquals(1, vm.uiState.value.search?.adults)
        repeat(12) { vm.onEvent(HomeEvent.AddAdult) }
        assertEquals(9, vm.uiState.value.search?.adults)
        vm.onEvent(HomeEvent.CycleCabin)
        assertEquals(Cabin.Premium, vm.uiState.value.search?.cabin)
    }

    @Test
    fun `search opens results with the query`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(HomeEvent.Search)
            val effect = awaitItem() as HomeEffect.OpenResults
            assertEquals("JFK", effect.query.from.code)
        }
    }

    @Test
    fun `tapping a destination fills TO and scrolls to the search card`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(HomeEvent.DestinationTapped(lisbon))
            assertEquals(HomeEffect.ScrollToSearch, awaitItem())
        }
        assertEquals("LHR", vm.uiState.value.search?.to?.code)
    }

    @Test
    fun `no trip shows the empty state once known`() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.tripLoaded)
        trips.emit(null)
        assertTrue(vm.uiState.value.tripLoaded)
        assertNull(vm.uiState.value.trip)
    }

    @Test
    fun `offline catalog shows retry, and retry recovers`() {
        airports.result = Outcome.Failure(AppError.Network)
        val vm = viewModel()
        assertTrue(vm.uiState.value.loadFailed)
        assertNull(vm.uiState.value.search)

        airports.result = Outcome.Success(TestAirports.all)
        vm.onEvent(HomeEvent.Retry)
        assertFalse(vm.uiState.value.loadFailed)
        assertEquals("JFK", vm.uiState.value.search?.from?.code)
    }

    @Test
    fun `a search from Explore fills TO, and a deal also sets FROM`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            intents.request(SearchIntent(toCode = "ZQN"))
            assertEquals(HomeEffect.ScrollToSearch, awaitItem())
            assertEquals("JFK", vm.uiState.value.search?.from?.code)
            assertEquals("ZQN", vm.uiState.value.search?.to?.code)
            assertNull(intents.pending.value) // applied once

            intents.request(SearchIntent(toCode = "AKL", fromCode = "LAX"))
            assertEquals(HomeEffect.ScrollToSearch, awaitItem())
            assertEquals("LAX", vm.uiState.value.search?.from?.code)
            assertEquals("AKL", vm.uiState.value.search?.to?.code)
        }
    }
}
