package com.skypass.feature.payment

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.skypass.core.domain.usecase.AddCardUseCase
import com.skypass.core.domain.usecase.ObserveAppSettingsUseCase
import com.skypass.core.testing.FakeAccountSettingsRepository
import com.skypass.core.testing.FakeDeviceSettingsRepository
import com.skypass.core.domain.usecase.CalculateBookingTotalUseCase
import com.skypass.core.domain.usecase.CardField
import com.skypass.core.domain.usecase.CardProblem
import com.skypass.core.domain.usecase.ValidateCardUseCase
import com.skypass.core.model.CardBrand
import com.skypass.core.model.CardDraft
import com.skypass.core.testing.FakePaymentCardRepository
import com.skypass.core.testing.TestCards
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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
    private val cards = FakePaymentCardRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
    private val handle = SavedStateHandle(
        mapOf("outboundInstanceId" to "nz5", "outboundSeats" to "33C", "passengerIds" to "p-1", "cabin" to "economy", "bags" to 1),
    )

    private fun viewModel() = PaymentViewModel(
        savedStateHandle = handle,
        getFlightOffer = GetFlightOfferUseCase(FakeFlightOfferRepository()),
        getSeatMap = GetSeatMapUseCase(FakeSeatMapRepository()),
        getPaymentMethods = GetPaymentMethodsUseCase(cards, clock),
        observeSession = ObserveSessionUseCase(FakeAuthRepository(FakeAuthRepository.TINA)),
        checkPromo = CheckPromoUseCase(FakePromoRepository()),
        calculateTotal = CalculateBookingTotalUseCase(),
        payForTrip = PayForTripUseCase(bookings),
        addCard = AddCardUseCase(cards, ValidateCardUseCase(clock)),
        validateCard = ValidateCardUseCase(clock),
        observeSettings = ObserveAppSettingsUseCase(FakeAccountSettingsRepository(), devices),
    )

    private val devices = FakeDeviceSettingsRepository()

    @Test
    fun `with the biometric lock on, the screen asks before paying`() = runTest {
        val vm = viewModel()
        assertEquals(false, vm.uiState.value.confirmWithBiometric)
        devices.setBiometricLock(true)
        assertEquals(true, vm.uiState.value.confirmWithBiometric)
    }

    @Test
    fun `shows the board's summary, the saved cards (default first) and Google Pay`() {
        cards.stored += TestCards.EXPIRED_AMEX
        val state = viewModel().uiState.value
        assertEquals(269, state.price.totalUsd)
        // The expired Amex isn't offered.
        assertEquals(listOf(PaymentMethodType.Visa, PaymentMethodType.Mastercard, PaymentMethodType.GooglePay), state.methods.map { it.type })
        assertEquals("card-1", state.method!!.cardId)
        assertEquals("Tina Anderson", state.method!!.holder)
        assertEquals(listOf("33C"), state.seats)
    }

    @Test
    fun `paying names the selected card, Google Pay names none`() = runTest {
        val vm = viewModel()
        vm.onEvent(PaymentEvent.SelectMethod(1))
        vm.effects.test {
            vm.onEvent(PaymentEvent.Pay)
            awaitItem()
        }
        assertEquals(PaymentMethodType.Mastercard to "card-2", bookings.paidWith.values.single())
    }

    @Test
    fun `a card added at checkout is saved by its last four digits and selected`() {
        val vm = viewModel()
        vm.onEvent(PaymentEvent.AddCard)
        val form = vm.uiState.value.cardForm!!
        vm.onEvent(PaymentEvent.UpdateCard(form.edit(CardDraft(TestCards.AMEX_NUMBER, "0829", "1234", "Tina Anderson"))))
        vm.onEvent(PaymentEvent.SaveCard)
        assertNull(vm.uiState.value.cardForm)
        val added = cards.stored.last()
        assertEquals(CardBrand.Amex, added.brand)
        assertEquals("0005", added.last4)
        assertEquals("TINA ANDERSON", added.holder)
        assertEquals(added.id, vm.uiState.value.method?.cardId)
        assertEquals(PaymentMethodType.Amex, vm.uiState.value.method?.type)
    }

    @Test
    fun `a bad card number keeps the sheet open with the problem`() {
        val vm = viewModel()
        vm.onEvent(PaymentEvent.AddCard)
        val form = vm.uiState.value.cardForm!!
        vm.onEvent(PaymentEvent.UpdateCard(form.edit(CardDraft("4242424242424241", "0829", "123", "Tina"))))
        vm.onEvent(PaymentEvent.SaveCard)
        assertEquals(CardProblem.Invalid, vm.uiState.value.cardForm!!.problems[CardField.Number])
        assertEquals(2, cards.stored.size)
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
