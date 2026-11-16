package com.skypass.feature.profile

import app.cash.turbine.test
import com.skypass.core.domain.usecase.GetAirportsUseCase
import com.skypass.core.domain.usecase.GetProfileOverviewUseCase
import com.skypass.core.domain.usecase.ObserveSavedCountUseCase
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.domain.usecase.ProfileProblem
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.domain.usecase.UpdateProfileUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.Outcome
import com.skypass.core.model.Tier
import com.skypass.core.testing.FakeAirportRepository
import com.skypass.core.testing.FakeAuthRepository
import com.skypass.core.testing.FakeEditProfileRepository
import com.skypass.core.testing.FakePassengerRepository
import com.skypass.core.testing.FakeProfileRepository
import com.skypass.core.testing.FakeProfileStatsRepository
import com.skypass.core.testing.FakeSavedDestinationsRepository
import com.skypass.core.testing.FakeSavedFlightsRepository
import com.skypass.core.testing.FakeSavedHotelsRepository
import com.skypass.core.testing.MainDispatcherExtension
import com.skypass.core.testing.TestAirports
import com.skypass.core.testing.TestPassengers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ProfileViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val profiles = FakeProfileRepository()
    private val stats = FakeProfileStatsRepository()
    private val edits = FakeEditProfileRepository()
    private val savedFlights = FakeSavedFlightsRepository()
    private val savedHotels = FakeSavedHotelsRepository()
    private val savedDestinations = FakeSavedDestinationsRepository()

    private fun viewModel() = ProfileViewModel(
        getOverview = GetProfileOverviewUseCase(profiles, stats, FakePassengerRepository(listOf(TestPassengers.TINA))),
        observeSession = ObserveSessionUseCase(FakeAuthRepository(FakeAuthRepository.TINA)),
        observeSavedCount = ObserveSavedCountUseCase(savedFlights, savedHotels, savedDestinations),
        updateProfile = UpdateProfileUseCase(edits),
        getAirports = GetAirportsUseCase(FakeAirportRepository()),
        searchAirports = SearchAirportsUseCase(),
    )

    @Test
    fun `shows the board's card - Tina, Gold, 14 trips, 9 countries, 48,200 miles`() {
        val state = viewModel().uiState.value
        assertEquals("Tina Anderson", state.displayName)
        assertEquals("TA", state.initials)
        val tiles = state.overview!!.stats
        assertEquals(Tier.Gold, tiles.tier)
        assertEquals(listOf(14, 9, 48_200), listOf(tiles.trips, tiles.countries, tiles.miles))
        assertEquals(1, state.overview!!.passengers.size)
        assertEquals("48.2k", compactMiles(48_200))
    }

    @Test
    fun `the saved badge counts flights, stays and places live`() = runTest {
        val vm = viewModel()
        savedFlights.setSaved("i-1", true)
        savedHotels.setSaved("h-1", true)
        savedDestinations.setSaved("d-1", true)
        assertEquals(3, vm.uiState.value.savedCount)
        savedHotels.setSaved("h-1", false)
        assertEquals(2, vm.uiState.value.savedCount)
    }

    @Test
    fun `menu rows go to their screens`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(ProfileEvent.OpenPassengers)
            assertEquals(ProfileEffect.Passengers, awaitItem())
            vm.onEvent(ProfileEvent.OpenSaved)
            assertEquals(ProfileEffect.Saved, awaitItem())
            vm.onEvent(ProfileEvent.OpenTrips)
            assertEquals(ProfileEffect.Trips, awaitItem())
            vm.onEvent(ProfileEvent.OpenSettings)
            assertEquals(ProfileEffect.Settings, awaitItem())
        }
    }

    @Test
    fun `personal information saves a new name and home airport`() {
        val vm = viewModel()
        vm.onEvent(ProfileEvent.OpenPersonalInfo)
        val draft = (vm.uiState.value.sheet as ProfileSheet.PersonalInfo).draft
        assertEquals("Tina Anderson", draft.fullName)
        assertEquals("JFK", draft.homeCode)

        vm.onEvent(ProfileEvent.PickHomeAirport)
        vm.onEvent(ProfileEvent.AirportQueryChanged("auck"))
        val picked = (vm.uiState.value.sheet as ProfileSheet.PersonalInfo).draft.airports.single()
        vm.onEvent(ProfileEvent.AirportPicked(picked))
        vm.onEvent(ProfileEvent.NameChanged("Tina  M Anderson"))
        vm.onEvent(ProfileEvent.SavePersonalInfo)

        assertEquals(listOf("Tina M Anderson" to TestAirports.AKL.code), edits.updates)
        assertNull(vm.uiState.value.sheet)
        assertEquals("Tina M Anderson", vm.uiState.value.displayName)
        assertEquals("AKL", vm.uiState.value.overview?.profile?.homeAirport)
    }

    @Test
    fun `a one-letter name is refused, a failed save keeps the sheet open`() {
        val vm = viewModel()
        vm.onEvent(ProfileEvent.OpenPersonalInfo)
        vm.onEvent(ProfileEvent.NameChanged("T"))
        vm.onEvent(ProfileEvent.SavePersonalInfo)
        assertEquals(ProfileProblem.NameTooShort, (vm.uiState.value.sheet as ProfileSheet.PersonalInfo).draft.problem)
        assertTrue(edits.updates.isEmpty())

        edits.fail = true
        vm.onEvent(ProfileEvent.NameChanged("Tina"))
        vm.onEvent(ProfileEvent.SavePersonalInfo)
        assertTrue((vm.uiState.value.sheet as ProfileSheet.PersonalInfo).draft.failed)
    }

    @Test
    fun `offline shows retry, and a quiet refresh never replaces what's shown with an error`() {
        profiles.result = Outcome.Failure(AppError.Network)
        val vm = viewModel()
        assertEquals(AppError.Network, vm.uiState.value.error)
        profiles.result = FakeProfileRepository().result
        vm.onEvent(ProfileEvent.Retry)
        assertNull(vm.uiState.value.error)

        profiles.result = Outcome.Failure(AppError.Network)
        vm.onEvent(ProfileEvent.Refresh)
        assertNull(vm.uiState.value.error)
        assertEquals("Tina Anderson", vm.uiState.value.displayName)
    }
}
