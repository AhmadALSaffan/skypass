package com.skypass.core.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * The card networks Skypass accepts, recognised from the first digits of the number (IIN ranges).
 * [groups] is how the number is printed: 4-4-4-4, or 4-6-5 for American Express.
 */
enum class CardBrand(
    val label: String,
    val apiValue: String,
    val lengths: Set<Int>,
    val cvcLength: Int,
    val groups: List<Int>,
) {
    Visa("Visa", "visa", setOf(13, 16, 19), 3, listOf(4, 4, 4, 4, 3)),
    Mastercard("Mastercard", "mastercard", setOf(16), 3, listOf(4, 4, 4, 4)),
    Amex("American Express", "amex", setOf(15), 4, listOf(4, 6, 5)),
    Discover("Discover", "discover", setOf(16, 19), 3, listOf(4, 4, 4, 4, 3));

    val maxLength: Int get() = lengths.max()

    val paymentType: PaymentMethodType
        get() = when (this) {
            Visa -> PaymentMethodType.Visa
            Mastercard -> PaymentMethodType.Mastercard
            Amex -> PaymentMethodType.Amex
            Discover -> PaymentMethodType.Discover
        }

    companion object {
        fun fromApi(value: String): CardBrand? = entries.firstOrNull { it.apiValue == value }

        /** The brand of a (partial) number, as soon as its first digits tell; null if unknown yet. */
        fun detect(number: String): CardBrand? {
            val d = number.filter(Char::isDigit)
            fun prefix(n: Int) = d.take(n).takeIf { it.length == n }?.toInt()
            return when {
                d.startsWith("34") || d.startsWith("37") -> Amex
                d.startsWith("4") -> Visa
                prefix(2) in 51..55 -> Mastercard
                prefix(4) in 2221..2720 -> Mastercard
                d.startsWith("6011") || d.startsWith("65") -> Discover
                prefix(3) in 644..649 -> Discover
                prefix(6) in 622126..622925 -> Discover
                else -> null
            }
        }
    }
}

/** A card saved on the account: what's printed on the front, minus all but the last four digits. */
data class SavedCard(
    val id: String,
    val brand: CardBrand,
    val last4: String,
    val expMonth: Int,
    val expYear: Int,
    val holder: String,
    val isDefault: Boolean,
) {
    /** "08/28". */
    val expiry: String get() = "%02d/%02d".format(expMonth, expYear % 100)

    /** Cards work until the end of their expiry month. */
    fun isExpired(today: LocalDate): Boolean = YearMonth.of(expYear, expMonth).isBefore(YearMonth.from(today))
}

/** What the "Add a card" form holds. Only [last4] of the number ever leaves the phone. */
data class CardDraft(
    val number: String = "",
    /** "MMYY" digits as typed; shown as MM/YY. */
    val expiry: String = "",
    val cvc: String = "",
    val holder: String = "",
    val makeDefault: Boolean = false,
) {
    val digits: String get() = number.filter(Char::isDigit)
    val brand: CardBrand? get() = CardBrand.detect(digits)
}
