package com.skypass.core.domain.usecase

import com.skypass.core.model.BookingError
import com.skypass.core.model.BookingQuote
import com.skypass.core.model.Cabin
import com.skypass.core.model.LegOrder
import com.skypass.core.model.LegSelection
import com.skypass.core.model.Outcome
import com.skypass.core.model.PaymentMethodType
import com.skypass.core.model.PromoCode
import com.skypass.core.model.TripOrder
import com.skypass.core.testing.FakeBookingRepository
import com.skypass.core.testing.TestFlights
import com.skypass.core.testing.TestPassengers
import com.skypass.core.testing.TestSeats
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BookingUseCasesTest {

    private val total = CalculateBookingTotalUseCase()
    private fun seat(code: String) = TestSeats.map.seat(code)!!

    @Test
    fun `the board's booking costs 269 dollars`() {
        // NZ 5 at $209, a standard seat, 1 adult, 1 bag: 209 + 25 tax + 0 + 35.
        val price = total(BookingQuote(listOf(LegSelection(TestFlights.NZ5, listOf(seat("33C")))), passengers = 1, bags = 1))
        assertEquals(269, price.totalUsd)
    }

    @Test
    fun `promo comes off the fare before tax, like the server`() {
        // 209 − 21 (10%) + 23 tax on 188 + 35 bag = 246, the server's figure for KIAORA10.
        val price = total(
            BookingQuote(listOf(LegSelection(TestFlights.NZ5, listOf(seat("33C")))), 1, 1, PromoCode("KIAORA10", 10, "")),
        )
        assertEquals(21, price.discountUsd)
        assertEquals(23, price.taxesUsd)
        assertEquals(246, price.totalUsd)
    }

    @Test
    fun `every traveller's legroom seat is charged, on every leg`() {
        val price = total(
            BookingQuote(
                legs = listOf(
                    LegSelection(TestFlights.NZ5, listOf(seat("30B"), seat("30C"))),
                    LegSelection(TestFlights.AA1, listOf(seat("33A"), seat("33B"))),
                ),
                passengers = 2,
                bags = 2,
            ),
        )
        assertEquals((209 + 312) * 2, price.fareUsd)
        assertEquals(78, price.seatFeesUsd)
        assertEquals(35 * 2 * 2, price.bagsUsd)
    }

    @Test
    fun `seats are suggested side by side, skipping paid rows`() {
        val suggest = SuggestSeatsUseCase()
        assertEquals(listOf("31D", "31E", "31F"), suggest(TestSeats.map, 3).map { it.code })
        assertEquals(listOf("31A"), suggest(TestSeats.map, 1).map { it.code })
    }

    @Test
    fun `passport and contact details are all checked`() {
        val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
        val validate = ValidatePassengerUseCase(clock)
        val trip = LocalDate.of(2026, 11, 27)
        assertTrue(validate(TestPassengers.TINA, trip).isEmpty())

        val blank = validate(TestPassengers.TINA.copy(lastName = "", gender = null, email = null), trip)
        assertEquals(FieldProblem.Required, blank[PassengerField.LastName])
        assertEquals(FieldProblem.Required, blank[PassengerField.Gender])
        assertEquals(FieldProblem.Required, blank[PassengerField.Email])

        val wrong = validate(TestPassengers.TINA.copy(passportNumber = "12", phone = "abc", email = "tina@"), trip)
        assertEquals(FieldProblem.Invalid, wrong[PassengerField.PassportNumber])
        assertEquals(FieldProblem.Invalid, wrong[PassengerField.Phone])
        assertEquals(FieldProblem.Invalid, wrong[PassengerField.Email])

        fun expiry(date: String) = validate(TestPassengers.TINA.copy(passportExpiryDate = LocalDate.parse(date)), trip)[PassengerField.ExpiryDate]
        assertEquals(FieldProblem.ExpiresBeforeTrip, expiry("2026-11-20"))
        assertEquals(FieldProblem.ExpiresWithinSixMonths, expiry("2027-03-01"))
        assertEquals(FieldProblem.BeforeIssue, expiry("2021-01-01"))
        assertEquals(
            FieldProblem.NotYetIssued,
            validate(TestPassengers.TINA.copy(passportIssueDate = LocalDate.of(2027, 1, 1)), trip)[PassengerField.IssueDate],
        )
    }

    @Test
    fun `paying books and pays every leg, and a retry reuses the same bookings`() = runTest {
        val repo = FakeBookingRepository()
        val pay = PayForTripUseCase(repo)
        val order = TripOrder(
            legs = listOf(LegOrder("nz5", listOf("33C")), LegOrder("aa", listOf("33C"))),
            cabin = Cabin.Economy,
            passengerIds = listOf("p-1"),
            bags = 1,
            promoCode = null,
            method = PaymentMethodType.Visa,
        )
        val first = pay(order, "attempt-1") as Outcome.Success
        assertEquals(2, first.value.size)
        pay(order, "attempt-1")
        assertEquals(2, repo.created.size) // same keys, no double booking
        assertEquals(listOf("b-1", "b-2"), repo.paid)

        repo.createError = BookingError.SeatTaken
        assertEquals(Outcome.Failure(BookingError.SeatTaken), pay(order, "attempt-2"))
    }
}
