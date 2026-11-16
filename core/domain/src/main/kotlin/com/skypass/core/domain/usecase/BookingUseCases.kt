package com.skypass.core.domain.usecase

import com.skypass.core.domain.repository.BookingRepository
import com.skypass.core.domain.repository.FlightOfferRepository
import com.skypass.core.domain.repository.PassengerRepository
import com.skypass.core.domain.repository.PromoRepository
import com.skypass.core.domain.repository.SeatMapRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.BoardingPass
import com.skypass.core.model.BookingError
import com.skypass.core.model.BookingQuote
import com.skypass.core.model.Cabin
import com.skypass.core.model.ConfirmedBooking
import com.skypass.core.model.FlightOffer
import com.skypass.core.model.Outcome
import com.skypass.core.model.Passenger
import com.skypass.core.model.PaymentMethod
import com.skypass.core.model.PaymentMethodType
import com.skypass.core.model.PriceBreakdown
import com.skypass.core.model.PromoCode
import com.skypass.core.model.Seat
import com.skypass.core.model.SeatMap
import com.skypass.core.model.SeatType
import com.skypass.core.model.TripOrder
import com.skypass.core.model.UserSession
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

class GetFlightOfferUseCase @Inject constructor(private val offers: FlightOfferRepository) {
    suspend operator fun invoke(instanceId: String, cabin: Cabin, passengers: Int): Outcome<FlightOffer, AppError> =
        offers.offer(instanceId, cabin, passengers)
}

class GetSeatMapUseCase @Inject constructor(private val seats: SeatMapRepository) {
    suspend operator fun invoke(instanceId: String, cabin: Cabin): Outcome<SeatMap, AppError> = seats.seatMap(instanceId, cabin)
}

class GetPassengersUseCase @Inject constructor(private val passengers: PassengerRepository) {
    suspend operator fun invoke(): Outcome<List<Passenger>, AppError> = passengers.passengers()
}

// ---------------------------------------------------------------------------------------------
// Passenger details
// ---------------------------------------------------------------------------------------------

enum class PassengerField { FirstName, LastName, PassportType, PassportNumber, IssueDate, ExpiryDate, Gender, Phone, Email }

enum class FieldProblem {
    Required,
    Invalid,
    /** Issue date in the future. */
    NotYetIssued,
    /** Expiry before (or not after) the issue date. */
    BeforeIssue,
    /** Expires before the trip ends. */
    ExpiresBeforeTrip,
    /** Many countries require 6 months' validity after the trip. */
    ExpiresWithinSixMonths,
}

/** Checks all nine passenger details the airline needs. Empty map = good to fly. */
class ValidatePassengerUseCase @Inject constructor(private val clock: Clock) {
    operator fun invoke(p: Passenger, tripEnds: LocalDate?): Map<PassengerField, FieldProblem> {
        val today = LocalDate.now(clock)
        val problems = mutableMapOf<PassengerField, FieldProblem>()
        fun name(field: PassengerField, value: String) {
            when {
                value.isBlank() -> problems[field] = FieldProblem.Required
                !NAME.matches(value.trim()) -> problems[field] = FieldProblem.Invalid
            }
        }
        name(PassengerField.FirstName, p.firstName)
        name(PassengerField.LastName, p.lastName)
        if (p.passportType == null) problems[PassengerField.PassportType] = FieldProblem.Required
        val passport = p.passportNumber
        when {
            passport.isNullOrBlank() -> problems[PassengerField.PassportNumber] = FieldProblem.Required
            !PASSPORT.matches(passport) -> problems[PassengerField.PassportNumber] = FieldProblem.Invalid
        }
        val issued = p.passportIssueDate
        val expires = p.passportExpiryDate
        when {
            issued == null -> problems[PassengerField.IssueDate] = FieldProblem.Required
            issued.isAfter(today) -> problems[PassengerField.IssueDate] = FieldProblem.NotYetIssued
        }
        val tripEnd = tripEnds ?: today
        when {
            expires == null -> problems[PassengerField.ExpiryDate] = FieldProblem.Required
            issued != null && !expires.isAfter(issued) -> problems[PassengerField.ExpiryDate] = FieldProblem.BeforeIssue
            expires.isBefore(tripEnd) -> problems[PassengerField.ExpiryDate] = FieldProblem.ExpiresBeforeTrip
            expires.isBefore(tripEnd.plusMonths(6)) -> problems[PassengerField.ExpiryDate] = FieldProblem.ExpiresWithinSixMonths
        }
        if (p.gender == null) problems[PassengerField.Gender] = FieldProblem.Required
        val phone = p.phone
        when {
            phone.isNullOrBlank() -> problems[PassengerField.Phone] = FieldProblem.Required
            !PHONE.matches(phone.trim()) || phone.count(Char::isDigit) !in 7..15 -> problems[PassengerField.Phone] = FieldProblem.Invalid
        }
        val email = p.email
        when {
            email.isNullOrBlank() -> problems[PassengerField.Email] = FieldProblem.Required
            !EMAIL.matches(email.trim()) -> problems[PassengerField.Email] = FieldProblem.Invalid
        }
        return problems
    }

    companion object {
        private val NAME = Regex("^[\\p{L}][\\p{L} '.-]{0,48}$")
        private val PASSPORT = Regex("^[A-Z0-9]{6,9}$")
        private val PHONE = Regex("^\\+?[0-9 ()-]{7,20}$")
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

class SavePassengerUseCase @Inject constructor(private val passengers: PassengerRepository) {
    /** Tidies the details (trimmed names, upper-case passport) and saves them. */
    suspend operator fun invoke(passenger: Passenger): Outcome<Passenger, AppError> {
        fun tidy(s: String) = s.trim().replace(Regex("\\s+"), " ")
        return passengers.save(
            passenger.copy(
                firstName = tidy(passenger.firstName),
                lastName = tidy(passenger.lastName),
                passportNumber = passenger.passportNumber?.uppercase()?.filter(Char::isLetterOrDigit)?.ifBlank { null },
                phone = passenger.phone?.trim()?.ifBlank { null },
                email = passenger.email?.trim()?.lowercase()?.ifBlank { null },
            ),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Seats
// ---------------------------------------------------------------------------------------------

/**
 * Seats to start with for [count] travellers: side by side in one block (between aisles) as near the
 * front as possible, avoiding paid extra-legroom rows; otherwise the frontmost free seats.
 */
class SuggestSeatsUseCase @Inject constructor() {
    operator fun invoke(map: SeatMap, count: Int): List<Seat> {
        if (count <= 0) return emptyList()
        val free = map.seats.filter { !it.taken }
        fun together(allowPaid: Boolean): List<Seat>? {
            for (row in map.rows) {
                for (block in map.blocks) {
                    val seats = block.mapNotNull { map.seat("$row$it") }
                    val ok = seats.map { !it.taken && (allowPaid || it.extraFeeUsd == 0) }
                    for (start in 0..(seats.size - count)) {
                        if (ok.subList(start, start + count).all { it }) return seats.subList(start, start + count)
                    }
                }
            }
            return null
        }
        return together(allowPaid = false)
            ?: together(allowPaid = true)
            ?: (free.filter { it.extraFeeUsd == 0 } + free.filter { it.extraFeeUsd > 0 }).take(count)
    }
}

// ---------------------------------------------------------------------------------------------
// Pricing. Each charge is its own rule (open/closed): a new fee is a new rule in the list.
// Mirrors the server's create_booking, leg by leg, so the total on screen is the total charged.
// ---------------------------------------------------------------------------------------------

fun interface PriceRule {
    fun apply(quote: BookingQuote, price: PriceBreakdown): PriceBreakdown
}

private fun BookingQuote.legFare(i: Int) = legs[i].flight.priceUsd * passengers
private fun BookingQuote.legDiscount(i: Int) = promo?.let { (legFare(i) * it.percentOff / 100.0).roundToInt() } ?: 0

object FareRule : PriceRule {
    override fun apply(quote: BookingQuote, price: PriceBreakdown) = price.copy(fareUsd = quote.legs.indices.sumOf { quote.legFare(it) })
}

object PromoRule : PriceRule {
    override fun apply(quote: BookingQuote, price: PriceBreakdown) = price.copy(discountUsd = quote.legs.indices.sumOf { quote.legDiscount(it) })
}

/** 12% on each leg's fare after the promo, rounded per leg like the server. */
object TaxRule : PriceRule {
    override fun apply(quote: BookingQuote, price: PriceBreakdown) =
        price.copy(taxesUsd = quote.legs.indices.sumOf { ((quote.legFare(it) - quote.legDiscount(it)) * 0.12).roundToInt() })
}

/** Extra-legroom seats cost their fee, for every traveller on every leg. */
object SeatFeeRule : PriceRule {
    override fun apply(quote: BookingQuote, price: PriceBreakdown) =
        price.copy(seatFeesUsd = quote.legs.sumOf { leg -> leg.seats.sumOf { it.extraFeeUsd } })
}

/** $35 per checked bag, on every leg. */
object BagsRule : PriceRule {
    const val PER_BAG_USD = 35
    override fun apply(quote: BookingQuote, price: PriceBreakdown) = price.copy(bagsUsd = PER_BAG_USD * quote.bags * quote.legs.size)
}

class CalculateBookingTotalUseCase @Inject constructor() {
    private val rules: List<PriceRule> = listOf(FareRule, PromoRule, TaxRule, SeatFeeRule, BagsRule)

    operator fun invoke(quote: BookingQuote): PriceBreakdown = rules.fold(PriceBreakdown()) { price, rule -> rule.apply(quote, price) }

    companion object {
        const val MAX_BAGS = 3
    }
}

// ---------------------------------------------------------------------------------------------
// Payment
// ---------------------------------------------------------------------------------------------

class CheckPromoUseCase @Inject constructor(private val promos: PromoRepository) {
    suspend operator fun invoke(code: String): Outcome<PromoCode?, AppError> =
        if (code.isBlank()) Outcome.Success(null) else promos.check(code.trim().uppercase())
}

/**
 * The traveller's saved ways to pay. A demo wallet: the board's Visa and Mastercard, plus Google
 * Pay on the signed-in email. Nothing here is a real card.
 */
/**
 * Books and pays every leg. [attemptId] is stable across retries, so a dropped connection never
 * books or charges twice (each leg's server call is idempotent on a key derived from it).
 */
class PayForTripUseCase @Inject constructor(private val bookings: BookingRepository) {
    suspend operator fun invoke(order: TripOrder, attemptId: String): Outcome<List<ConfirmedBooking>, BookingError> {
        val confirmed = mutableListOf<ConfirmedBooking>()
        order.legs.forEachIndexed { i, leg ->
            val key = UUID.nameUUIDFromBytes("$attemptId:$i".toByteArray()).toString()
            val bookingId = when (
                val created = bookings.create(leg.instanceId, order.cabin, leg.seats, order.passengerIds, order.bags, order.promoCode, key)
            ) {
                is Outcome.Success -> created.value
                is Outcome.Failure -> return created
            }
            when (val paid = bookings.pay(bookingId, order.method, order.cardId)) {
                is Outcome.Success -> confirmed += paid.value
                is Outcome.Failure -> return paid
            }
        }
        return Outcome.Success(confirmed)
    }
}

class GetBoardingPassesUseCase @Inject constructor(private val bookings: BookingRepository) {
    suspend operator fun invoke(bookingIds: List<String>): Outcome<List<BoardingPass>, AppError> {
        val passes = mutableListOf<BoardingPass>()
        for (id in bookingIds) {
            when (val result = bookings.boardingPasses(id)) {
                is Outcome.Success -> passes += result.value
                is Outcome.Failure -> return result
            }
        }
        return Outcome.Success(passes)
    }
}

/** Seat types that come with the fare (no seat fee). */
val SeatType.isCabinUpgrade: Boolean get() = this == SeatType.Premium || this == SeatType.Business || this == SeatType.First
