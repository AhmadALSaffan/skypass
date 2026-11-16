package com.skypass.core.domain.usecase

import com.skypass.core.model.CardBrand
import com.skypass.core.model.CardDraft
import com.skypass.core.model.Outcome
import com.skypass.core.testing.FakePaymentCardRepository
import com.skypass.core.testing.TestCards
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CardUseCasesTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
    private val validate = ValidateCardUseCase(clock)

    @Test
    fun `brands are recognised from their first digits`() {
        assertEquals(CardBrand.Visa, CardBrand.detect("4"))
        assertEquals(CardBrand.Mastercard, CardBrand.detect("5555"))
        assertEquals(CardBrand.Mastercard, CardBrand.detect("2221 00"))
        assertEquals(CardBrand.Amex, CardBrand.detect("37"))
        assertEquals(CardBrand.Amex, CardBrand.detect("34"))
        assertEquals(CardBrand.Discover, CardBrand.detect("6011"))
        assertEquals(CardBrand.Discover, CardBrand.detect("6445"))
        assertEquals(CardBrand.Discover, CardBrand.detect("622126"))
        assertNull(CardBrand.detect("3530")) // JCB
        assertNull(CardBrand.detect("5"))
    }

    @Test
    fun `every test number passes, a typo fails the Luhn check`() {
        listOf(TestCards.VISA_NUMBER, TestCards.MASTERCARD_NUMBER, TestCards.AMEX_NUMBER, TestCards.DISCOVER_NUMBER).forEach {
            assertTrue(validate(CardDraft(it, "0829", if (it.startsWith("3")) "1234" else "123", "Tina")).isEmpty(), it)
        }
        assertEquals(CardProblem.Invalid, validate(CardDraft("4242424242424241", "0829", "123", "Tina"))[CardField.Number])
        assertFalse(ValidateCardUseCase.luhn("1234567812345678"))
    }

    @Test
    fun `an unsupported network, a past expiry and the wrong code length are each explained`() {
        val problems = validate(CardDraft("3530111333300000", "1025", "1234", ""))
        assertEquals(CardProblem.Unsupported, problems[CardField.Number])
        assertEquals(CardProblem.Expired, problems[CardField.Expiry])
        assertEquals(CardProblem.Required, problems[CardField.Holder])
        assertEquals(CardProblem.Invalid, validate(CardDraft(TestCards.AMEX_NUMBER, "1326", "123", "Tina"))[CardField.Expiry])
        assertEquals(CardProblem.Invalid, validate(CardDraft(TestCards.AMEX_NUMBER, "0829", "123", "Tina"))[CardField.Cvc])
        // This month still works.
        assertNull(validate(CardDraft(TestCards.VISA_NUMBER, "1126", "123", "Tina"))[CardField.Expiry])
    }

    @Test
    fun `adding a card sends only the last four digits, and the first card becomes the default`() = runTest {
        val repo = FakePaymentCardRepository(emptyList())
        val added = AddCardUseCase(repo, validate)(CardDraft(TestCards.DISCOVER_NUMBER, "0230", "123", " tina  anderson ")) as Outcome.Success
        assertEquals(CardBrand.Discover, added.value.brand)
        assertEquals("1117", added.value.last4)
        assertEquals(2, added.value.expMonth)
        assertEquals(2030, added.value.expYear)
        assertEquals("TINA ANDERSON", added.value.holder)
        assertTrue(added.value.isDefault)
    }

    @Test
    fun `checkout offers unexpired cards, default first, then Google Pay`() = runTest {
        val repo = FakePaymentCardRepository(listOf(TestCards.MASTERCARD, TestCards.EXPIRED_AMEX, TestCards.VISA))
        val methods = GetPaymentMethodsUseCase(repo, clock)(null)
        assertEquals(listOf("card-1", "card-2", null), methods.map { it.cardId })
        assertTrue(TestCards.EXPIRED_AMEX.isExpired(LocalDate.now(clock)))
        assertEquals("08/28", TestCards.VISA.expiry)
    }
}
