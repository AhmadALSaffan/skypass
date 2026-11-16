package com.skypass.core.domain.usecase

import com.skypass.core.domain.repository.PaymentCardRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.CardDraft
import com.skypass.core.model.Outcome
import com.skypass.core.model.PaymentMethod
import com.skypass.core.model.PaymentMethodType
import com.skypass.core.model.SavedCard
import com.skypass.core.model.UserSession
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class CardField { Number, Expiry, Cvc, Holder }

enum class CardProblem {
    Required,
    /** Not a card number (wrong length or fails the Luhn check), or a malformed date or code. */
    Invalid,
    /** A real number from a network Skypass doesn't take. */
    Unsupported,
    Expired,
}

/**
 * Checks the "Add a card" form the way a payment page does: a supported brand, the right length,
 * the Luhn checksum, an expiry this month or later (up to 20 years out), the brand's CVC length.
 */
class ValidateCardUseCase @Inject constructor(private val clock: Clock) {
    operator fun invoke(draft: CardDraft): Map<CardField, CardProblem> = buildMap {
        val digits = draft.digits
        val brand = draft.brand
        when {
            digits.isEmpty() -> put(CardField.Number, CardProblem.Required)
            brand == null -> put(CardField.Number, if (digits.length >= 13 && luhn(digits)) CardProblem.Unsupported else CardProblem.Invalid)
            digits.length !in brand.lengths || !luhn(digits) -> put(CardField.Number, CardProblem.Invalid)
        }
        val expiry = parseExpiry(draft.expiry)
        val thisMonth = YearMonth.from(LocalDate.now(clock))
        when {
            draft.expiry.isEmpty() -> put(CardField.Expiry, CardProblem.Required)
            expiry == null || expiry.isAfter(thisMonth.plusYears(MAX_YEARS)) -> put(CardField.Expiry, CardProblem.Invalid)
            expiry.isBefore(thisMonth) -> put(CardField.Expiry, CardProblem.Expired)
        }
        val cvcLength = brand?.cvcLength
        when {
            draft.cvc.isEmpty() -> put(CardField.Cvc, CardProblem.Required)
            !draft.cvc.all(Char::isDigit) || (cvcLength != null && draft.cvc.length != cvcLength) || draft.cvc.length !in 3..4 ->
                put(CardField.Cvc, CardProblem.Invalid)
        }
        when {
            draft.holder.isBlank() -> put(CardField.Holder, CardProblem.Required)
            draft.holder.trim().length < 2 -> put(CardField.Holder, CardProblem.Invalid)
        }
    }

    companion object {
        private const val MAX_YEARS = 20L

        /** "0828" → August 2028. */
        fun parseExpiry(mmyy: String): YearMonth? {
            if (mmyy.length != 4 || !mmyy.all(Char::isDigit)) return null
            val month = mmyy.take(2).toInt()
            if (month !in 1..12) return null
            return YearMonth.of(2000 + mmyy.takeLast(2).toInt(), month)
        }

        /** The Luhn checksum every card number carries in its last digit. */
        fun luhn(digits: String): Boolean {
            var sum = 0
            digits.reversed().forEachIndexed { i, c ->
                var d = c - '0'
                if (i % 2 == 1) {
                    d *= 2
                    if (d > 9) d -= 9
                }
                sum += d
            }
            return digits.isNotEmpty() && sum % 10 == 0
        }
    }
}

/** Validates, then saves only the brand, the last four digits, the expiry and the name. */
class AddCardUseCase @Inject constructor(
    private val cards: PaymentCardRepository,
    private val validate: ValidateCardUseCase,
) {
    suspend operator fun invoke(draft: CardDraft): Outcome<SavedCard, AppError> {
        val problems = validate(draft)
        val brand = draft.brand
        val expiry = ValidateCardUseCase.parseExpiry(draft.expiry)
        if (problems.isNotEmpty() || brand == null || expiry == null) return Outcome.Failure(AppError.Unknown("invalid_card"))
        return cards.add(
            brand = brand,
            last4 = draft.digits.takeLast(4),
            expMonth = expiry.monthValue,
            expYear = expiry.year,
            // Cards print the name in capitals; keep it that way.
            holder = draft.holder.trim().replace(Regex("\\s+"), " ").uppercase(),
            makeDefault = draft.makeDefault,
        )
    }
}

class GetCardsUseCase @Inject constructor(private val cards: PaymentCardRepository) {
    /** Default first, then newest; expired cards last. */
    suspend operator fun invoke(today: LocalDate): Outcome<List<SavedCard>, AppError> = when (val r = cards.cards()) {
        is Outcome.Failure -> r
        is Outcome.Success -> Outcome.Success(r.value.sortedWith(compareBy<SavedCard> { it.isExpired(today) }.thenByDescending { it.isDefault }))
    }
}

class DeleteCardUseCase @Inject constructor(private val cards: PaymentCardRepository) {
    suspend operator fun invoke(cardId: String): Outcome<Unit, AppError> = cards.delete(cardId)
}

class SetDefaultCardUseCase @Inject constructor(private val cards: PaymentCardRepository) {
    suspend operator fun invoke(cardId: String): Outcome<Unit, AppError> = cards.setDefault(cardId)
}

/**
 * Ways to pay at checkout: the traveller's unexpired saved cards (default first), then Google Pay.
 * Offline, Google Pay is still offered; new cards can be added from the payment screen.
 */
class GetPaymentMethodsUseCase @Inject constructor(
    private val cards: PaymentCardRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(session: UserSession?): List<PaymentMethod> {
        val today = LocalDate.now(clock)
        val saved = (cards.cards() as? Outcome.Success)?.value.orEmpty()
            .filterNot { it.isExpired(today) }
            .sortedByDescending { it.isDefault }
        return saved.map { it.toPaymentMethod() } + googlePay(session)
    }

    companion object {
        fun googlePay(session: UserSession?) = PaymentMethod(
            type = PaymentMethodType.GooglePay,
            label = "Google Pay",
            detail = session?.email ?: "Cards in your Google account",
            last4 = null,
            expiry = null,
            holder = session?.fullName ?: "Skypass traveller",
        )
    }
}

fun SavedCard.toPaymentMethod() = PaymentMethod(
    type = brand.paymentType,
    label = brand.label,
    detail = "•••• $last4 · Exp $expiry",
    last4 = last4,
    expiry = expiry,
    holder = holder,
    cardId = id,
    isDefault = isDefault,
)
