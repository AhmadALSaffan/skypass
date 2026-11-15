package com.skypass.core.testing

import com.skypass.core.domain.repository.BookingRepository
import com.skypass.core.domain.repository.FlightOfferRepository
import com.skypass.core.domain.repository.PassengerRepository
import com.skypass.core.domain.repository.PromoRepository
import com.skypass.core.domain.repository.SeatMapRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.BoardingPass
import com.skypass.core.model.BookingError
import com.skypass.core.model.Cabin
import com.skypass.core.model.ConfirmedBooking
import com.skypass.core.model.FlightOffer
import com.skypass.core.model.Gender
import com.skypass.core.model.Outcome
import com.skypass.core.model.Passenger
import com.skypass.core.model.PassportType
import com.skypass.core.model.PaymentMethodType
import com.skypass.core.model.PromoCode
import com.skypass.core.model.Seat
import com.skypass.core.model.SeatMap
import com.skypass.core.model.SeatType
import java.time.LocalDate

/** A 787 economy cabin: rows 30–35, ABC-DEF-HJK, row 30 extra legroom (+$39), a few seats sold. */
object TestSeats {
    private val taken = setOf("30A", "30K", "31B", "31C", "32D", "32E", "32F", "33A", "34H", "34J")
    val map: SeatMap = SeatMap(
        layout = "ABC-DEF-HJK",
        cabin = Cabin.Economy,
        seats = (30..35).flatMap { row ->
            "ABCDEFHJK".map { l ->
                val code = "$row$l"
                Seat(code, row, l.toString(), if (row == 30) SeatType.Legroom else SeatType.Standard, if (row == 30) 39 else 0, code in taken, exitRow = row == 30)
            }
        },
    )
}

object TestPassengers {
    val TINA = Passenger(
        id = "p-1",
        firstName = "Tina",
        lastName = "Anderson",
        passportType = PassportType.Ordinary,
        passportNumber = "LA1234471",
        passportIssueDate = LocalDate.of(2022, 3, 1),
        passportExpiryDate = LocalDate.of(2032, 3, 1),
        gender = Gender.Female,
        phone = "+64 21 555 0199",
        email = "tina.anderson@gmail.com",
        isPrimary = true,
    )
}

class FakeFlightOfferRepository(
    var offers: Map<String, FlightOffer> = TestFlights.all.associateBy { it.instanceId },
    var failure: AppError? = null,
) : FlightOfferRepository {
    override suspend fun offer(instanceId: String, cabin: Cabin, passengers: Int): Outcome<FlightOffer, AppError> {
        failure?.let { return Outcome.Failure(it) }
        return offers[instanceId]?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.NotFound)
    }
}

class FakeSeatMapRepository(var map: SeatMap = TestSeats.map) : SeatMapRepository {
    override suspend fun seatMap(instanceId: String, cabin: Cabin): Outcome<SeatMap, AppError> = Outcome.Success(map)
}

class FakePassengerRepository(initial: List<Passenger> = emptyList(), var failSaves: Boolean = false) : PassengerRepository {
    val stored = initial.toMutableList()
    private var nextId = 100

    override suspend fun passengers(): Outcome<List<Passenger>, AppError> = Outcome.Success(stored.toList())

    override suspend fun save(passenger: Passenger): Outcome<Passenger, AppError> {
        if (failSaves) return Outcome.Failure(AppError.Network)
        val saved = if (passenger.id == null) passenger.copy(id = "p-${nextId++}") else passenger
        stored.removeAll { it.id == saved.id }
        stored += saved
        return Outcome.Success(saved)
    }
}

class FakePromoRepository(private val codes: Map<String, PromoCode> = mapOf("KIAORA10" to PromoCode("KIAORA10", 10, "10% off your fare"))) : PromoRepository {
    override suspend fun check(code: String): Outcome<PromoCode?, AppError> = Outcome.Success(codes[code])
}

class FakeBookingRepository : BookingRepository {
    /** Create calls by idempotency key, so retries can be checked. */
    val created = linkedMapOf<String, String>()
    val paid = mutableListOf<String>()
    var createError: BookingError? = null
    var payError: BookingError? = null

    override suspend fun create(
        instanceId: String,
        cabin: Cabin,
        seats: List<String>,
        passengerIds: List<String>,
        bags: Int,
        promoCode: String?,
        idempotencyKey: String,
    ): Outcome<String, BookingError> {
        createError?.let { return Outcome.Failure(it) }
        return Outcome.Success(created.getOrPut(idempotencyKey) { "b-${created.size + 1}" })
    }

    override suspend fun pay(bookingId: String, method: PaymentMethodType): Outcome<ConfirmedBooking, BookingError> {
        payError?.let { return Outcome.Failure(it) }
        if (bookingId !in paid) paid += bookingId
        return Outcome.Success(ConfirmedBooking(bookingId, "SKP7Q2", 269))
    }

    override suspend fun boardingPasses(bookingId: String): Outcome<List<BoardingPass>, AppError> = Outcome.Success(emptyList())
}
