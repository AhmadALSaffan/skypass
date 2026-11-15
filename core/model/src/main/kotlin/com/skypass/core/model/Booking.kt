package com.skypass.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class SeatType { Standard, Legroom, Premium, Business, First }

/** One seat on the plane. */
data class Seat(
    val code: String,
    val row: Int,
    val letter: String,
    val type: SeatType,
    val extraFeeUsd: Int,
    val taken: Boolean,
    val exitRow: Boolean = false,
)

/**
 * The whole cabin the traveller flies in. [layout] is e.g. "ABC-DEF-HJK": seat letters, with an
 * aisle at each dash. [cabin] is the cabin actually shown (an aircraft without First shows Business).
 */
data class SeatMap(
    val layout: String,
    val cabin: Cabin,
    val seats: List<Seat>,
) {
    /** Seat letters grouped into the blocks between aisles. */
    val blocks: List<List<String>> get() = layout.split('-').map { block -> block.map { it.toString() } }
    val rows: List<Int> get() = seats.map { it.row }.distinct().sorted()
    fun seat(code: String): Seat? = seats.firstOrNull { it.code == code }
}

enum class PassengerType { Adult, Child, Infant }

enum class PassportType(val apiValue: String) {
    Ordinary("ordinary"), Diplomatic("diplomatic"), Service("service"), Official("official");

    companion object {
        fun fromApi(value: String?): PassportType? = entries.firstOrNull { it.apiValue == value }
    }
}

enum class Gender(val apiValue: String) {
    Female("female"), Male("male"), Unspecified("unspecified");

    companion object {
        fun fromApi(value: String?): Gender? = entries.firstOrNull { it.apiValue == value }
    }
}

/** A traveller saved on the account, with everything the airline needs before departure. */
data class Passenger(
    val id: String?,
    val firstName: String,
    val lastName: String = "",
    val type: PassengerType = PassengerType.Adult,
    val passportType: PassportType? = null,
    val passportNumber: String? = null,
    val passportIssueDate: LocalDate? = null,
    val passportExpiryDate: LocalDate? = null,
    val gender: Gender? = null,
    val phone: String? = null,
    val email: String? = null,
    val isPrimary: Boolean = false,
) {
    val fullName: String get() = "$firstName $lastName".trim()
    val passportLast4: String? get() = passportNumber?.takeLast(4)
    val initials: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

    /** All nine details are filled in (validity is checked separately). */
    val isComplete: Boolean
        get() = firstName.isNotBlank() && lastName.isNotBlank() && passportType != null && !passportNumber.isNullOrBlank() &&
            passportIssueDate != null && passportExpiryDate != null && gender != null && !phone.isNullOrBlank() && !email.isNullOrBlank()
}

/** What the traveller is buying on one leg: the departure and a seat per traveller. */
data class LegSelection(
    val flight: FlightOffer,
    val seats: List<Seat>,
)

data class PromoCode(val code: String, val percentOff: Int, val description: String)

/** Everything that goes into the price (per trip, all travellers). */
data class BookingQuote(
    val legs: List<LegSelection>,
    val passengers: Int,
    /** Checked bags per leg. */
    val bags: Int,
    val promo: PromoCode? = null,
)

/** The board's price summary: fare × travellers, promo, 12% taxes, seat upgrades, checked bags. */
data class PriceBreakdown(
    val fareUsd: Int = 0,
    val discountUsd: Int = 0,
    val taxesUsd: Int = 0,
    val seatFeesUsd: Int = 0,
    val bagsUsd: Int = 0,
) {
    val totalUsd: Int get() = fareUsd - discountUsd + taxesUsd + seatFeesUsd + bagsUsd
}

enum class PaymentMethodType(val apiValue: String) { Visa("visa"), Mastercard("mastercard"), GooglePay("google_pay") }

/** A saved way to pay. Card numbers are never stored in full, only the last four digits. */
data class PaymentMethod(
    val type: PaymentMethodType,
    val label: String,
    val detail: String,
    val last4: String?,
    val expiry: String?,
    val holder: String,
)

/** One leg to book: departure, seats in traveller order, bags. */
data class LegOrder(
    val instanceId: String,
    val seats: List<String>,
)

data class TripOrder(
    val legs: List<LegOrder>,
    val cabin: Cabin,
    val passengerIds: List<String>,
    val bags: Int,
    val promoCode: String?,
    val method: PaymentMethodType,
)

/** A confirmed booking for one leg. */
data class ConfirmedBooking(val bookingId: String, val pnr: String, val totalUsd: Int)

enum class BookingError { SeatTaken, InvalidPromo, Network, Unknown }

/** One printed pass per traveller. */
data class BoardingPass(
    val bookingId: String,
    val pnr: String,
    val passengerName: String,
    val seat: String,
    val cabin: Cabin,
    val zone: String,
    val airlineCode: String,
    val airlineName: String,
    val airlineColor: Long,
    val airlineLogoUrl: String?,
    val flightNumber: String,
    val originCode: String,
    val originCity: String,
    val destinationCode: String,
    val destinationCity: String,
    val departure: LocalDateTime,
    val arrival: LocalDateTime,
    val durationMinutes: Int,
    val gate: String,
    val boardingTime: LocalTime,
)
