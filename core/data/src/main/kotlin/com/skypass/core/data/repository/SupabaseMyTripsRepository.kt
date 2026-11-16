package com.skypass.core.data.repository

import android.util.Log
import com.skypass.core.data.remote.CarBookingDto
import com.skypass.core.data.remote.StayBookingDto
import com.skypass.core.data.remote.TripDto
import com.skypass.core.domain.repository.CancelError
import com.skypass.core.domain.repository.MyTripsRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.CarBooking
import com.skypass.core.model.FlightBooking
import com.skypass.core.model.Outcome
import com.skypass.core.model.StayBooking
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import kotlin.math.roundToInt

/** Reads the traveller's bookings back from Supabase. RLS only ever returns their own rows. */
internal class SupabaseMyTripsRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : MyTripsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun flights(): Outcome<List<FlightBooking>, AppError> = supabase.safeCall {
        supabase.postgrest.rpc("my_flights").decodeList<JsonObject>().map { row ->
            FlightBooking(
                trip = json.decodeFromJsonElement(TripDto.serializer(), row).toModel(),
                totalUsd = row["total_usd"]?.jsonPrimitive?.doubleOrNull?.roundToInt() ?: 0,
                passengers = row["passengers"]?.jsonPrimitive?.intOrNull ?: 1,
            )
        }
    }

    override suspend fun stays(): Outcome<List<StayBooking>, AppError> = supabase.safeCall {
        supabase.from("hotel_reservations")
            .select(Columns.raw("*, hotel:hotels(*)")) {
                // Stays starting the same day: the latest booking first.
                order("check_in", Order.ASCENDING)
                order("created_at", Order.DESCENDING)
            }
            .decodeList<StayBookingDto>()
            .map { it.toModel() }
    }

    override suspend fun cars(): Outcome<List<CarBooking>, AppError> = supabase.safeCall {
        supabase.from("rental_reservations")
            .select(Columns.raw("*, car:cars(*)")) {
                order("pickup_at", Order.ASCENDING)
                order("created_at", Order.DESCENDING)
            }
            .decodeList<CarBookingDto>()
            .map { it.toModel() }
    }

    override suspend fun cancel(reference: String): Outcome<Unit, CancelError> = try {
        supabase.auth.awaitInitialization()
        supabase.postgrest.rpc("cancel_reservation", buildJsonObject { put("p_reference", reference) })
        Outcome.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestException) {
        Outcome.Failure(CancelError.Network)
    } catch (e: RestException) {
        Log.w("Skypass", "Cancel refused: ${e.error}", e)
        val notCancellable = "not_cancellable" in "${e.error} ${e.description}"
        Outcome.Failure(if (notCancellable) CancelError.NotCancellable else CancelError.Unknown)
    } catch (e: Exception) {
        Log.w("Skypass", "Cancel failed", e)
        Outcome.Failure(CancelError.Unknown)
    }
}
