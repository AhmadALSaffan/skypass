package com.skypass.core.testing

import com.skypass.core.domain.repository.HotelRepository
import com.skypass.core.domain.repository.SavedHotelsRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Hotel
import com.skypass.core.model.HotelReservation
import com.skypass.core.model.Outcome
import com.skypass.core.model.ReservationError
import com.skypass.core.model.RoomType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** The board's four Auckland stays. */
object TestHotels {
    val KOWHAI = Hotel("h1", "Auckland", "Kōwhai Airport Inn", "Auckland Airport", 0.4, 4, 8.6, 2140, 189, true, listOf("Free Wi-Fi", "Shuttle", "Breakfast"))
    val POHUTUKAWA = Hotel("h4", "Auckland", "Pōhutukawa Lodge", "Devonport", 24.0, 5, 9.1, 612, 236, true, listOf("Pool", "Beach access", "Breakfast"))
    val HARBOURLINE = Hotel("h3", "Auckland", "Harbourline Hotel", "Viaduct Harbour", 20.0, 4, 8.9, 986, 268, false, listOf("Rooftop bar", "Gym"))
    val WAITEMATA = Hotel("h2", "Auckland", "Waitematā House", "Wynyard Quarter", 21.0, 5, 9.3, 1408, 412, true, listOf("Pool", "Spa", "Harbour view"))
    val auckland = listOf(KOWHAI, POHUTUKAWA, HARBOURLINE, WAITEMATA)
}

class FakeHotelRepository(
    var hotels: Map<String, List<Hotel>> = mapOf("Auckland" to TestHotels.auckland),
    var failure: AppError? = null,
) : HotelRepository {
    var reserveError: ReservationError? = null
    /** attemptId → reservation, like the server's idempotency key. */
    val reservations = linkedMapOf<String, HotelReservation>()
    val requestedCities = mutableListOf<String>()

    override suspend fun hotels(city: String): Outcome<List<Hotel>, AppError> {
        requestedCities += city
        return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(hotels[city].orEmpty())
    }

    override suspend fun reserve(
        hotelId: String,
        room: RoomType,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guests: Int,
        attemptId: String,
    ): Outcome<HotelReservation, ReservationError> {
        reserveError?.let { return Outcome.Failure(it) }
        reservations[attemptId]?.let { return Outcome.Success(it) }
        val hotel = hotels.values.flatten().first { it.id == hotelId }
        val nights = ChronoUnit.DAYS.between(checkIn, checkOut).toInt()
        val total = (hotel.pricePerNightUsd * room.factor).roundToInt() * nights
        return HotelReservation("SKH${reservations.size + 1}AB", total, nights)
            .also { reservations[attemptId] = it }
            .let { Outcome.Success(it) }
    }
}

class FakeSavedHotelsRepository : SavedHotelsRepository {
    val ids = MutableStateFlow<Set<String>>(emptySet())
    override fun savedHotelIds(): Flow<Set<String>> = ids
    override suspend fun setSaved(hotelId: String, saved: Boolean): Outcome<Unit, AppError> {
        ids.update { if (saved) it + hotelId else it - hotelId }
        return Outcome.Success(Unit)
    }
}
