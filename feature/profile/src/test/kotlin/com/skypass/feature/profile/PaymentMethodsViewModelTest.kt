package com.skypass.feature.profile

import com.skypass.core.domain.usecase.AddCardUseCase
import com.skypass.core.domain.usecase.DeleteCardUseCase
import com.skypass.core.domain.usecase.GetCardsUseCase
import com.skypass.core.domain.usecase.SetDefaultCardUseCase
import com.skypass.core.domain.usecase.ValidateCardUseCase
import com.skypass.core.model.CardBrand
import com.skypass.core.model.CardDraft
import com.skypass.core.testing.FakePaymentCardRepository
import com.skypass.core.testing.MainDispatcherExtension
import com.skypass.core.testing.TestCards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class PaymentMethodsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
    private val repo = FakePaymentCardRepository(listOf(TestCards.MASTERCARD, TestCards.EXPIRED_AMEX, TestCards.VISA))

    private fun viewModel() = PaymentMethodsViewModel(
        getCards = GetCardsUseCase(repo),
        addCard = AddCardUseCase(repo, ValidateCardUseCase(clock)),
        validateCard = ValidateCardUseCase(clock),
        deleteCard = DeleteCardUseCase(repo),
        setDefault = SetDefaultCardUseCase(repo),
        clock = clock,
    )

    @Test
    fun `lists the default card first and expired cards last`() {
        assertEquals(listOf("card-1", "card-2", "card-3"), viewModel().uiState.value.cards.map { it.id })
    }

    @Test
    fun `adding a Mastercard saves it and shows it`() {
        val vm = viewModel()
        vm.onEvent(PaymentMethodsEvent.AddCard)
        vm.onEvent(PaymentMethodsEvent.UpdateCard(vm.uiState.value.cardForm!!.edit(CardDraft(TestCards.MASTERCARD_NUMBER, "0331", "321", "Tina Anderson"))))
        vm.onEvent(PaymentMethodsEvent.SaveCard)
        assertNull(vm.uiState.value.cardForm)
        assertTrue(vm.uiState.value.cards.any { it.brand == CardBrand.Mastercard && it.last4 == "4444" })
    }

    @Test
    fun `make default moves the badge, remove asks first`() {
        val vm = viewModel()
        vm.onEvent(PaymentMethodsEvent.MakeDefault(TestCards.MASTERCARD))
        assertEquals("card-2", vm.uiState.value.cards.first { it.isDefault }.id)
        assertEquals("card-2", repo.stored.first { it.isDefault }.id)

        vm.onEvent(PaymentMethodsEvent.AskDelete("card-1"))
        assertEquals("card-1", vm.uiState.value.confirmingDelete)
        vm.onEvent(PaymentMethodsEvent.ConfirmDelete)
        assertTrue(vm.uiState.value.cards.none { it.id == "card-1" })
        assertTrue(repo.stored.none { it.id == "card-1" })
    }

    @Test
    fun `an expired card can't be made the default`() {
        val vm = viewModel()
        vm.onEvent(PaymentMethodsEvent.MakeDefault(TestCards.EXPIRED_AMEX))
        assertEquals("card-1", vm.uiState.value.cards.first { it.isDefault }.id)
    }
}
