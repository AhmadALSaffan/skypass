package com.skypass.feature.payment

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.skypass.core.domain.usecase.CalculateBookingTotalUseCase
import com.skypass.core.domain.usecase.CheckPromoUseCase
import com.skypass.core.domain.usecase.GetFlightOfferUseCase
import com.skypass.core.domain.usecase.GetPaymentMethodsUseCase
import com.skypass.core.domain.usecase.GetSeatMapUseCase
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.domain.usecase.PayForTripUseCase
import com.skypass.core.model.BookingError
import com.skypass.core.model.PaymentMethodType
import com.skypass.core.testing.FakeAuthRepository
import com.skypass.core.testing.FakeBookingRepository
import com.skypass.core.testing.FakeFlightOfferRepository
import com.skypass.core.testing.FakePromoRepository
import com.skypass.core.testing.FakeSeatMapRepository
import com.skypass.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PaymentViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val bookings = FakeBookingRepository()
    private val handle = SavedStateHandle(
        mapOf("outboundInstanceId" to "nz5", "outboundSeats" to "33C", "passengerIds" to "p-1", "cabin" to "economy", "bags" to 1),
    )

    private fun viewModel() = PaymentViewModel(
        savedStateHandle = handle,
        getFlightOffer = GetFlightOfferUseCase(FakeFlightOfferRepository()),
        getSeatMap = GetSeatMapUseCase(FakeSeatMapRepository()),
        getPaymentMethods = GetPaymentMethodsUseCase(),
        observeSession = ObserveSessionUseCase(FakeAuthRepository(FakeAuthRepository.TINA)),
        checkPromo = CheckPromoUseCase(FakePromoRepository()),
        calculateTotal = CalculateBookingTotalUseCase(),
        payForTrip = PayForTripUseCase(bookings),
    )

    @Test
    fun `shows the board's summary and three ways to pay`() {
        val state = viewModel().uiState.value
        assertEquals(269, state.price.totalUsd)
        assertEquals(listOf(PaymentMethodType.Visa, PaymentMethodType.Mastercard, PaymentMethodType.GooglePay), state.methods.map { it.type })
        assertEquals("Tina Anderson", state.method!!.holder)
        assertEquals(listOf("33C"), state.seats)
    }

    @Test
    fun `a valid promo lowers the total, an invalid one is explained`() {
        val vm = viewModel()
        vm.onEvent(PaymentEvent.PromoChanged("nope"))
        vm.onEvent(PaymentEvent.ApplyPromo)
        assertEquals(PromoProblem.Invalid, vm.uiState.value.promoProblem)

        vm.onEvent(PaymentEvent.PromoChanged("kiaora10"))
        vm.onEvent(PaymentEvent.ApplyPromo)
        assertEquals("KIAORA10", vm.uiState.value.promo?.code)
        assertEquals(246, vm.uiState.value.price.totalUsd)

        vm.onEvent(PaymentEvent.RemovePromo)
        assertEquals(269, vm.uiState.value.price.totalUsd)
    }

    @Test
    fun `paying books, pays and hands over the booking ids`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(PaymentEvent.Pay)
            assertEquals(PaymentEffect.Paid(listOf("b-1")), awaitItem())
        }
        assertEquals(PayState.Paid, vm.uiState.value.payState)
        assertEquals(listOf("b-1"), bookings.paid)
    }

    @Test
    fun `a taken seat is explained and nothing else happens`() {
        bookings.createError = BookingError.SeatTaken
        val vm = viewModel()
        vm.onEvent(PaymentEvent.Pay)
        assertEquals(PayState.Idle, vm.uiState.value.payState)
        assertEquals(BookingError.SeatTaken, vm.uiState.value.payError)
        vm.onEvent(PaymentEvent.DismissPayError)
        assertNull(vm.uiState.value.payError)
    }

    @Test
    fun `retrying after a failure reuses the same attempt, so nothing is booked twice`() {
        bookings.payError = BookingError.Network
        val vm = viewModel()
        vm.onEvent(PaymentEvent.Pay)
        bookings.payError = null
        vm.onEvent(PaymentEvent.Pay)
        assertEquals(1, bookings.created.size)
    }
}
