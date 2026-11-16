package com.skypass.core.domain.usecase

import com.skypass.core.model.Cabin
import com.skypass.core.model.FlightStatus
import com.skypass.core.model.HotelFilter
import com.skypass.core.model.Outcome
import com.skypass.core.model.ReservationError
import com.skypass.core.model.RoomType
import com.skypass.core.model.Stay
import com.skypass.core.model.UpcomingTrip
import com.skypass.core.testing.FakeHotelRepository
import com.skypass.core.testing.FakeTripRepository
import com.skypass.core.testing.TestHotels
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class HotelUseCasesTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))

    private fun trip(departure: LocalDateTime, minutes: Int) = UpcomingTrip(
        bookingId = "b-1", pnr = "SKP7Q2", seat = "30E", cabin = Cabin.Economy, instanceId = "nz2",
        airlineCode = "NZ", airlineName = "Air New Zealand", flightNumber = "NZ 2",
        originCode = "JFK", originCity = "New York", destinationCode = "AKL", destinationCity = "Auckland",
        departure = departure, durationMinutes = minutes, gate = "B22", boardingTime = LocalTime.of(18, 20),
        status = FlightStatus.OnTime, delayMinutes = 0,
    )

    @Test
    fun `without a trip, the board's stay is Auckland, 20 to 27 Nov`() = runTest {
        val trips = FakeTripRepository().apply { emit(null) }
        val stay = ResolveStayUseCase(trips, clock)()
        assertEquals(Stay("Auckland", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 27), 1), stay)
        assertEquals(7, stay.nights)
    }

    @Test
    fun `with a trip, the stay starts the day you land`() = runTest {
        val trips = FakeTripRepository().apply { emit(trip(LocalDateTime.of(2026, 11, 20, 19, 0), 17 * 60 + 50)) }
        val stay = ResolveStayUseCase(trips, clock)()
        assertEquals("Auckland", stay.city)
        assertEquals(LocalDate.of(2026, 11, 21), stay.checkIn) // 19:00 + 17h50 lands next day
        assertEquals(LocalDate.of(2026, 11, 28), stay.checkOut)

        // Asking for another city ignores the trip's dates.
        assertEquals("Sydney", ResolveStayUseCase(trips, clock)("Sydney").city)
        assertEquals(LocalDate.of(2026, 11, 20), ResolveStayUseCase(trips, clock)("Sydney").checkIn)
    }

    @Test
    fun `the board's chips`() {
        val filter = FilterHotelsUseCase()
        assertEquals(listOf("Kōwhai Airport Inn", "Pōhutukawa Lodge"), filter(TestHotels.auckland, HotelFilter.Under250).map { it.name })
        assertEquals(listOf("Pōhutukawa Lodge", "Waitematā House"), filter(TestHotels.auckland, HotelFilter.FiveStar).map { it.name })
        assertEquals(3, filter(TestHotels.auckland, HotelFilter.FreeCancel).size)
        assertEquals(TestHotels.auckland, filter(TestHotels.auckland, HotelFilter.All))
    }

    @Test
    fun `room prices match the server's sum, and only rooms that fit are offered`() {
        val price = PriceStayUseCase()
        // Board: Kōwhai $189 a night, $1,323 for 7 nights.
        assertEquals(RoomPrice(RoomType.Standard, 189, 1323), price(TestHotels.KOWHAI, RoomType.Standard, 7))
        assertEquals(RoomPrice(RoomType.Deluxe, 255, 1785), price(TestHotels.KOWHAI, RoomType.Deluxe, 7)) // 189 × 1.35 = 255.15
        assertEquals(listOf(RoomType.Deluxe, RoomType.Suite), price.rooms(TestHotels.KOWHAI, 7, guests = 3).map { it.room })
    }

    @Test
    fun `stays are checked before searching`() {
        val validate = ValidateStayUseCase(clock)
        val ok = Stay("Auckland", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 27), 2)
        assertNull(validate(ok))
        assertEquals(StayProblem.CheckInInPast, validate(ok.copy(checkIn = LocalDate.of(2026, 11, 1))))
        assertEquals(StayProblem.NoNights, validate(ok.copy(checkOut = ok.checkIn)))
        assertEquals(StayProblem.TooLong, validate(ok.copy(checkOut = ok.checkIn.plusDays(31))))
    }

    @Test
    fun `a retry reuses the reservation, and a full room is refused before the server`() = runTest {
        val repo = FakeHotelRepository()
        val reserve = ReserveHotelUseCase(repo)
        val stay = Stay("Auckland", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 27), 2)
        val first = reserve(TestHotels.KOWHAI, RoomType.Standard, stay, "attempt-1") as Outcome.Success
        assertEquals(1323, first.value.totalUsd)
        assertEquals(first, reserve(TestHotels.KOWHAI, RoomType.Standard, stay, "attempt-1"))
        assertEquals(1, repo.reservations.size)

        assertEquals(
            Outcome.Failure(ReservationError.TooManyGuests),
            reserve(TestHotels.KOWHAI, RoomType.Standard, stay.copy(guests = 3), "attempt-2"),
        )
    }
}
