package com.skypass.core.testing

import com.skypass.core.domain.repository.CancelError
import com.skypass.core.domain.repository.MyTripsRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.ArrivalWindow
import com.skypass.core.model.CarBooking
import com.skypass.core.model.CarExtra
import com.skypass.core.model.Cabin
import com.skypass.core.model.ContactDetails
import com.skypass.core.model.DriverDetails
import com.skypass.core.model.FlightBooking
import com.skypass.core.model.FlightStatus
import com.skypass.core.model.Outcome
import com.skypass.core.model.ReservationStatus
import com.skypass.core.model.RoomType
import com.skypass.core.model.StayBooking
import com.skypass.core.model.StayDetails
import com.skypass.core.model.StayRequest
import com.skypass.core.model.UpcomingTrip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Bookings around 2026-11-15: one trip coming up of each kind, plus a past stay. */
object TestTrips {
    val TINA = ContactDetails("Tina Anderson", "tina.anderson@example.com", "+64 21 555 0199")
    private val BOOKED = Instant.parse("2026-11-01T09:00:00Z")

    val FLIGHT = FlightBooking(
        trip = UpcomingTrip(
            bookingId = "b-1", pnr = "SKP7Q2", seat = "14A", cabin = Cabin.Economy, instanceId = "i-1",
            airlineCode = "NZ", airlineName = "Air New Zealand", flightNumber = "NZ5",
            originCode = "LAX", originCity = "Los Angeles", destinationCode = "AKL", destinationCity = "Auckland",
            departure = LocalDateTime.of(2026, 11, 19, 21, 30), durationMinutes = 780, gate = "B22",
            boardingTime = LocalTime.of(20, 45), status = FlightStatus.OnTime, delayMinutes = 0,
        ),
        totalUsd = 1284,
        passengers = 1,
    )

    val STAY = StayBooking(
        reference = "SKHK7M",
        hotel = TestHotels.KOWHAI,
        room = RoomType.Deluxe,
        checkIn = LocalDate.of(2026, 11, 21),
        checkOut = LocalDate.of(2026, 11, 28),
        guests = 2,
        nightlyUsd = 255,
        totalUsd = 1785,
        details = StayDetails(TINA, ArrivalWindow.Evening, setOf(StayRequest.HighFloor, StayRequest.QuietRoom), "Celebrating an anniversary"),
        status = ReservationStatus.Confirmed,
        bookedAt = BOOKED,
    )

    val PAST_STAY = STAY.copy(
        reference = "SKHP2A",
        hotel = TestHotels.HARBOURLINE,
        room = RoomType.Standard,
        checkIn = LocalDate.of(2026, 10, 2),
        checkOut = LocalDate.of(2026, 10, 5),
        nightlyUsd = 268,
        totalUsd = 804,
    )

    val CAR = CarBooking(
        reference = "SKC4XR",
        car = TestCars.CERATO,
        pickupLocation = "Auckland Airport (AKL)",
        dropoffLocation = "Auckland Airport (AKL)",
        pickupAt = LocalDateTime.of(2026, 11, 20, 14, 0),
        dropoffAt = LocalDateTime.of(2026, 11, 27, 10, 0),
        days = 7,
        dailyUsd = 41,
        extrasUsd = 126,
        youngDriverFeeUsd = 0,
        oneWayFeeUsd = 0,
        totalUsd = 413,
        details = DriverDetails(TINA, flightNumber = "NZ5", extras = setOf(CarExtra.FullCover)),
        status = ReservationStatus.Confirmed,
        bookedAt = BOOKED,
    )
}

class FakeMyTripsRepository(
    var flights: List<FlightBooking> = listOf(TestTrips.FLIGHT),
    var stays: List<StayBooking> = listOf(TestTrips.STAY, TestTrips.PAST_STAY),
    var cars: List<CarBooking> = listOf(TestTrips.CAR),
    var failure: AppError? = null,
) : MyTripsRepository {
    var cancelError: CancelError? = null
    val cancelled = mutableListOf<String>()

    override suspend fun flights(): Outcome<List<FlightBooking>, AppError> = failure?.let { Outcome.Failure(it) } ?: Outcome.Success(flights)

    override suspend fun stays(): Outcome<List<StayBooking>, AppError> = failure?.let { Outcome.Failure(it) } ?: Outcome.Success(stays)

    override suspend fun cars(): Outcome<List<CarBooking>, AppError> = failure?.let { Outcome.Failure(it) } ?: Outcome.Success(cars)

    override suspend fun cancel(reference: String): Outcome<Unit, CancelError> {
        cancelError?.let { return Outcome.Failure(it) }
        cancelled += reference
        stays = stays.map { if (it.reference == reference) it.copy(status = ReservationStatus.Cancelled) else it }
        cars = cars.map { if (it.reference == reference) it.copy(status = ReservationStatus.Cancelled) else it }
        return Outcome.Success(Unit)
    }
}
