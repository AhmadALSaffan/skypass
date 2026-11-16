package com.skypass.core.domain.usecase

import com.skypass.core.domain.repository.HotelRepository
import com.skypass.core.domain.repository.SavedHotelsRepository
import com.skypass.core.domain.repository.TripRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Hotel
import com.skypass.core.model.HotelFilter
import com.skypass.core.model.HotelReservation
import com.skypass.core.model.Outcome
import com.skypass.core.model.ReservationError
import com.skypass.core.model.RoomType
import com.skypass.core.model.Stay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Where and when to look for a stay. A city the traveller chose wins. Otherwise it's the arrival
 * city of their next trip, from the day they land, for a week. With no trip it's the board's
 * default: Auckland, in 5 days, for 7 nights.
 */
class ResolveStayUseCase @Inject constructor(
    private val trips: TripRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(city: String? = null): Stay {
        val trip = withTimeoutOrNull(TRIP_WAIT_MS) { trips.upcomingTrip().first() }
        val today = LocalDate.now(clock)
        if (trip != null && (city == null || city == trip.destinationCity)) {
            val arrives = trip.departure.plusMinutes(trip.durationMinutes.toLong()).toLocalDate()
            val checkIn = if (arrives.isBefore(today)) today else arrives
            return Stay(trip.destinationCity, checkIn, checkIn.plusDays(DEFAULT_NIGHTS), guests = 1)
        }
        val checkIn = today.plusDays(CreateDefaultSearchUseCase.DEFAULT_LEAD_DAYS)
        return Stay(city ?: DEFAULT_CITY, checkIn, checkIn.plusDays(DEFAULT_NIGHTS), guests = 1)
    }

    companion object {
        const val DEFAULT_CITY = "Auckland"
        const val DEFAULT_NIGHTS = 7L
        private const val TRIP_WAIT_MS = 1_500L
    }
}

class GetHotelsUseCase @Inject constructor(private val hotels: HotelRepository) {
    suspend operator fun invoke(city: String): Outcome<List<Hotel>, AppError> = hotels.hotels(city)
}

/** The chip's rule, keeping the server's order (cheapest first). */
class FilterHotelsUseCase @Inject constructor() {
    operator fun invoke(all: List<Hotel>, filter: HotelFilter): List<Hotel> = all.filter(filter.matches)
}

data class RoomPrice(val room: RoomType, val perNightUsd: Int, val totalUsd: Int)

/** The same sum the server does: nightly rate × room factor, rounded, × nights. */
class PriceStayUseCase @Inject constructor() {
    operator fun invoke(hotel: Hotel, room: RoomType, nights: Int): RoomPrice {
        val perNight = (hotel.pricePerNightUsd * room.factor).roundToInt()
        return RoomPrice(room, perNight, perNight * nights)
    }

    /** Every room that fits [guests], cheapest first. */
    fun rooms(hotel: Hotel, nights: Int, guests: Int): List<RoomPrice> =
        RoomType.entries.filter { it.capacity >= guests }.map { invoke(hotel, it, nights) }
}

enum class StayProblem { CheckInInPast, NoNights, TooLong, TooManyGuests }

class ValidateStayUseCase @Inject constructor(private val clock: Clock) {
    operator fun invoke(stay: Stay): StayProblem? = when {
        stay.checkIn.isBefore(LocalDate.now(clock)) -> StayProblem.CheckInInPast
        stay.nights < 1 -> StayProblem.NoNights
        stay.nights > Stay.MAX_NIGHTS -> StayProblem.TooLong
        stay.guests !in Stay.MIN_GUESTS..Stay.MAX_GUESTS -> StayProblem.TooManyGuests
        else -> null
    }
}

class ReserveHotelUseCase @Inject constructor(private val hotels: HotelRepository) {
    suspend operator fun invoke(hotel: Hotel, room: RoomType, stay: Stay, attemptId: String): Outcome<HotelReservation, ReservationError> {
        if (stay.guests > room.capacity) return Outcome.Failure(ReservationError.TooManyGuests)
        return hotels.reserve(hotel.id, room, stay.checkIn, stay.checkOut, stay.guests, attemptId)
    }
}

class ObserveSavedHotelsUseCase @Inject constructor(private val saved: SavedHotelsRepository) {
    operator fun invoke(): Flow<Set<String>> = saved.savedHotelIds()
}

class SetHotelSavedUseCase @Inject constructor(private val saved: SavedHotelsRepository) {
    suspend operator fun invoke(hotelId: String, saved: Boolean): Outcome<Unit, AppError> = this.saved.setSaved(hotelId, saved)
}
