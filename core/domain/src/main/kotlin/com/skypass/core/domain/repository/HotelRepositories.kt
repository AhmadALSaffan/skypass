package com.skypass.core.domain.repository

import com.skypass.core.model.AppError
import com.skypass.core.model.Hotel
import com.skypass.core.model.HotelReservation
import com.skypass.core.model.Outcome
import com.skypass.core.model.ReservationError
import com.skypass.core.model.RoomType
import com.skypass.core.model.StayDetails
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface HotelRepository {
    /** Stays in [city], cheapest first. */
    suspend fun hotels(city: String): Outcome<List<Hotel>, AppError>

    /**
     * Books [room] at [hotelId] for the guest in [details]. The server prices it and keeps the
     * details for My trips; calling again with the same [attemptId] returns the first reservation
     * instead of booking twice.
     */
    suspend fun reserve(
        hotelId: String,
        room: RoomType,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guests: Int,
        details: StayDetails,
        attemptId: String,
    ): Outcome<HotelReservation, ReservationError>
}

/** Hearted hotels (Hotels list, Saved tab). */
interface SavedHotelsRepository {
    fun savedHotelIds(): Flow<Set<String>>

    suspend fun setSaved(hotelId: String, saved: Boolean): Outcome<Unit, AppError>
}
