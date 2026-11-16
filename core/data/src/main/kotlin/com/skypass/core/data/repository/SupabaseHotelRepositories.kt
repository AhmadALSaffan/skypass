package com.skypass.core.data.repository

import android.util.Log
import com.skypass.core.data.remote.HotelDto
import com.skypass.core.data.remote.HotelReservationDto
import com.skypass.core.domain.repository.HotelRepository
import com.skypass.core.domain.repository.SavedHotelsRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Hotel
import com.skypass.core.model.HotelReservation
import com.skypass.core.model.Outcome
import com.skypass.core.model.ReservationError
import com.skypass.core.model.RoomType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

internal class SupabaseHotelRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : HotelRepository {

    override suspend fun hotels(city: String): Outcome<List<Hotel>, AppError> = safeCall {
        supabase.from("hotels").select {
            filter { eq("city", city) }
            order("price_usd", Order.ASCENDING)
        }.decodeList<HotelDto>().map { it.toModel() }
    }

    override suspend fun reserve(
        hotelId: String,
        room: RoomType,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guests: Int,
        attemptId: String,
    ): Outcome<HotelReservation, ReservationError> = try {
        val row = supabase.postgrest.rpc(
            "reserve_hotel",
            buildJsonObject {
                put("p_hotel", hotelId)
                put("p_room", room.apiValue)
                put("p_check_in", checkIn.toString())
                put("p_check_out", checkOut.toString())
                put("p_guests", guests)
                put("p_idempotency_key", attemptId)
            },
        ).decodeList<HotelReservationDto>().single()
        Outcome.Success(row.toModel())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestException) {
        Outcome.Failure(ReservationError.Network)
    } catch (e: RestException) {
        Log.w("Skypass", "Reservation refused: ${e.error}", e)
        val text = "${e.error} ${e.description}"
        Outcome.Failure(
            when {
                "invalid_dates" in text -> ReservationError.InvalidDates
                "too_many_guests" in text -> ReservationError.TooManyGuests
                else -> ReservationError.Unknown
            },
        )
    } catch (e: Exception) {
        Log.w("Skypass", "Reservation failed", e)
        Outcome.Failure(ReservationError.Unknown)
    }
}

/** Hearted hotels. One store per process so the list and the Saved tab agree. */
@Singleton
internal class SupabaseSavedHotelsRepository @Inject constructor(
    supabase: SupabaseClient,
) : SavedHotelsRepository {
    private val store = SavedItemsStore(supabase, "hotel")

    override fun savedHotelIds(): Flow<Set<String>> = store.ids()

    override suspend fun setSaved(hotelId: String, saved: Boolean): Outcome<Unit, AppError> = store.setSaved(hotelId, saved)
}
