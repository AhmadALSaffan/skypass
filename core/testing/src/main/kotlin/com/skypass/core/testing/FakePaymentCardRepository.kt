package com.skypass.core.testing

import com.skypass.core.domain.repository.PaymentCardRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.CardBrand
import com.skypass.core.model.Outcome
import com.skypass.core.model.SavedCard

/** Test numbers that pass the Luhn check, one per brand. */
object TestCards {
    const val VISA_NUMBER = "4242424242424242"
    const val MASTERCARD_NUMBER = "5555555555554444"
    const val AMEX_NUMBER = "378282246310005"
    const val DISCOVER_NUMBER = "6011111111111117"

    val VISA = SavedCard("card-1", CardBrand.Visa, "4821", 8, 2028, "Tina Anderson", isDefault = true)
    val MASTERCARD = SavedCard("card-2", CardBrand.Mastercard, "9012", 2, 2027, "Tina Anderson", isDefault = false)
    val EXPIRED_AMEX = SavedCard("card-3", CardBrand.Amex, "0005", 1, 2025, "Tina Anderson", isDefault = false)
}

class FakePaymentCardRepository(
    initial: List<SavedCard> = listOf(TestCards.VISA, TestCards.MASTERCARD),
    var fail: Boolean = false,
) : PaymentCardRepository {
    val stored = initial.toMutableList()
    private var nextId = 10

    override suspend fun cards(): Outcome<List<SavedCard>, AppError> =
        if (fail) Outcome.Failure(AppError.Network) else Outcome.Success(stored.toList())

    override suspend fun add(
        brand: CardBrand,
        last4: String,
        expMonth: Int,
        expYear: Int,
        holder: String,
        makeDefault: Boolean,
    ): Outcome<SavedCard, AppError> {
        if (fail) return Outcome.Failure(AppError.Network)
        val default = makeDefault || stored.isEmpty()
        if (default) stored.replaceAll { it.copy(isDefault = false) }
        val card = SavedCard("card-${nextId++}", brand, last4, expMonth, expYear, holder, default)
        stored += card
        return Outcome.Success(card)
    }

    override suspend fun delete(cardId: String): Outcome<Unit, AppError> {
        if (fail) return Outcome.Failure(AppError.Network)
        stored.removeAll { it.id == cardId }
        return Outcome.Success(Unit)
    }

    override suspend fun setDefault(cardId: String): Outcome<Unit, AppError> {
        if (fail) return Outcome.Failure(AppError.Network)
        stored.replaceAll { it.copy(isDefault = it.id == cardId) }
        return Outcome.Success(Unit)
    }
}
