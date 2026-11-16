package com.skypass.core.domain.repository

import com.skypass.core.model.AppError
import com.skypass.core.model.BoardingPass
import com.skypass.core.model.BookingError
import com.skypass.core.model.Cabin
import com.skypass.core.model.ConfirmedBooking
import com.skypass.core.model.FlightOffer
import com.skypass.core.model.Outcome
import com.skypass.core.model.Passenger
import com.skypass.core.model.PaymentMethodType
import com.skypass.core.model.PromoCode
import com.skypass.core.model.SeatMap

/** A single departure, priced for the cabin (the booking screen opens from an instance id). */
interface FlightOfferRepository {
    suspend fun offer(instanceId: String, cabin: Cabin, passengers: Int): Outcome<FlightOffer, AppError>
}

interface SeatMapRepository {
    /** The whole cabin for [cabin] on this departure. */
    suspend fun seatMap(instanceId: String, cabin: Cabin): Outcome<SeatMap, AppError>
}

/** Travellers saved on the account. */
interface PassengerRepository {
    suspend fun passengers(): Outcome<List<Passenger>, AppError>

    /** Creates (no id) or updates (with id); returns the stored passenger. */
    suspend fun save(passenger: Passenger): Outcome<Passenger, AppError>

    /** Removes a traveller. Past bookings keep their own copy of the names. */
    suspend fun delete(passengerId: String): Outcome<Unit, AppError>
}

interface PromoRepository {
    /** The code if it's valid today, null if not. */
    suspend fun check(code: String): Outcome<PromoCode?, AppError>
}

/** Creates bookings (server-priced, seats locked) and pays for them. Both are idempotent. */
interface BookingRepository {
    suspend fun create(
        instanceId: String,
        cabin: Cabin,
        seats: List<String>,
        passengerIds: List<String>,
        bags: Int,
        promoCode: String?,
        idempotencyKey: String,
    ): Outcome<String, BookingError>

    /** Pays with [method]; a card payment names the saved card ([cardId]). */
    suspend fun pay(bookingId: String, method: PaymentMethodType, cardId: String?): Outcome<ConfirmedBooking, BookingError>

    /** One pass per traveller on the booking. */
    suspend fun boardingPasses(bookingId: String): Outcome<List<BoardingPass>, AppError>
}
