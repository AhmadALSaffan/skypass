package com.skypass.feature.booking

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.skypass.core.domain.usecase.CalculateBookingTotalUseCase
import com.skypass.core.domain.usecase.FieldProblem
import com.skypass.core.domain.usecase.GetFlightOfferUseCase
import com.skypass.core.domain.usecase.GetPassengersUseCase
import com.skypass.core.domain.usecase.GetSeatMapUseCase
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.domain.usecase.PassengerField
import com.skypass.core.domain.usecase.SavePassengerUseCase
import com.skypass.core.domain.usecase.SuggestSeatsUseCase
import com.skypass.core.domain.usecase.ValidatePassengerUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.Gender
import com.skypass.core.model.PassportType
import com.skypass.core.testing.FakeAuthRepository
import com.skypass.core.testing.FakeFlightOfferRepository
import com.skypass.core.testing.FakePassengerRepository
import com.skypass.core.testing.FakeSeatMapRepository
import com.skypass.core.testing.MainDispatcherExtension
import com.skypass.core.testing.TestPassengers
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

class BookingViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
    private val offers = FakeFlightOfferRepository()
    private val passengers = FakePassengerRepository()
    private val auth = FakeAuthRepository(FakeAuthRepository.TINA)

    private fun viewModel(returnId: String? = null, adults: Int = 1) = BookingViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf("outboundInstanceId" to "nz5", "returnInstanceId" to returnId, "adults" to adults, "cabin" to "economy"),
        ),
        getFlightOffer = GetFlightOfferUseCase(offers),
        getSeatMap = GetSeatMapUseCase(FakeSeatMapRepository()),
        getPassengers = GetPassengersUseCase(passengers),
        savePassenger = SavePassengerUseCase(passengers),
        validatePassenger = ValidatePassengerUseCase(clock),
        suggestSeats = SuggestSeatsUseCase(),
        calculateTotal = CalculateBookingTotalUseCase(),
        observeSession = ObserveSessionUseCase(auth),
    )

    @Test
    fun `first time, the account holder is added but must finish their details`() {
        val state = viewModel().uiState.value
        val tina = state.travellers.single()
        assertEquals("Tina", tina.firstName)
        assertEquals("Anderson", tina.lastName)
        assertEquals("tina.anderson@gmail.com", tina.email)
        assertEquals(setOf(0), state.incomplete)
        assertFalse(state.canContinue)
    }

    @Test
    fun `the whole cabin loads and the traveller gets a free seat`() {
        passengers.stored += TestPassengers.TINA
        val state = viewModel().uiState.value
        assertEquals(54, state.legs[0].map!!.seats.size)
        assertEquals("31A", state.legs[0].assignments[0])
        assertEquals(269, state.price.totalUsd)
        assertTrue(state.canContinue)
    }

    @Test
    fun `the passenger form needs all nine details`() {
        val vm = viewModel()
        vm.onEvent(BookingEvent.EditPassenger(vm.uiState.value.travellers.first()))
        vm.onEvent(BookingEvent.SavePassenger)
        val problems = vm.uiState.value.editor!!.problems
        listOf(PassengerField.Gender, PassengerField.PassportNumber, PassengerField.IssueDate, PassengerField.ExpiryDate, PassengerField.Phone)
            .forEach { assertEquals(FieldProblem.Required, problems[it], it.name) }

        val editor = vm.uiState.value.editor!!
        vm.onEvent(
            BookingEvent.UpdateEditor(
                editor.copy(
                    gender = Gender.Female,
                    passportType = PassportType.Ordinary,
                    passportNumber = "la1234471",
                    issueDate = LocalDate.of(2022, 3, 1),
                    expiryDate = LocalDate.of(2032, 3, 1),
                    phone = "+64 21 555 0199",
                ),
            ),
        )
        assertTrue(vm.uiState.value.editor!!.problems.isEmpty()) // fixing a field clears its error
        vm.onEvent(BookingEvent.SavePassenger)

        assertNull(vm.uiState.value.editor)
        assertEquals("LA1234471", vm.uiState.value.travellers.single().passportNumber)
        assertTrue(vm.uiState.value.incomplete.isEmpty())
        assertTrue(vm.uiState.value.canContinue)
    }

    @Test
    fun `two travellers are seated together, and tapping seats moves the active one`() {
        passengers.stored += TestPassengers.TINA
        passengers.stored += TestPassengers.TINA.copy(id = "p-2", firstName = "Sam", isPrimary = false)
        val vm = viewModel(adults = 2)
        assertEquals(mapOf(0 to "31D", 1 to "31E"), vm.uiState.value.legs[0].assignments)

        vm.onEvent(BookingEvent.SelectTraveller(1))
        vm.onEvent(BookingEvent.SelectSeat("30B")) // legroom
        assertEquals("30B", vm.uiState.value.legs[0].assignments[1])
        assertEquals(39, vm.uiState.value.price.seatFeesUsd)
        assertEquals(418 + 50 + 39 + 35, vm.uiState.value.price.totalUsd) // 2 fares + 12% + legroom + 1 bag

        // Taking a fellow traveller's seat swaps them.
        vm.onEvent(BookingEvent.SelectTraveller(0))
        vm.onEvent(BookingEvent.SelectSeat("30B"))
        assertEquals(mapOf(0 to "30B", 1 to "31D"), vm.uiState.value.legs[0].assignments)

        vm.onEvent(BookingEvent.SelectSeat("30A")) // sold
        assertEquals("30B", vm.uiState.value.legs[0].assignments[0])
    }

    @Test
    fun `continue sends each leg's seats in traveller order`() = runTest {
        passengers.stored += TestPassengers.TINA
        val vm = viewModel(returnId = "aa")
        vm.effects.test {
            vm.onEvent(BookingEvent.Continue)
            val request = (awaitItem() as BookingEffect.OpenPayment).request
            assertEquals(listOf("nz5", "aa"), request.legs.map { it.instanceId })
            assertEquals(listOf(listOf("31A"), listOf("31A")), request.legs.map { it.seats })
            assertEquals(listOf("p-1"), request.passengerIds)
        }
    }

    @Test
    fun `failure shows retry`() {
        offers.failure = AppError.Network
        val vm = viewModel()
        assertEquals(AppError.Network, vm.uiState.value.error)
        offers.failure = null
        vm.onEvent(BookingEvent.Retry)
        assertNull(vm.uiState.value.error)
    }
}
