package com.skypass.core.data.repository

import android.util.Log
import com.skypass.core.data.remote.CarDto
import com.skypass.core.data.remote.CarReservationDto
import com.skypass.core.domain.repository.CarRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Car
import com.skypass.core.model.CarReservation
import com.skypass.core.model.DriverDetails
import com.skypass.core.model.Outcome
import com.skypass.core.model.Rental
import com.skypass.core.model.ReservationError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.ZoneOffset
import javax.inject.Inject

internal class SupabaseCarRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : CarRepository {

    override suspend fun cars(airportCode: String): Outcome<List<Car>, AppError> = supabase.safeCall {
        supabase.from("cars").select {
            filter { eq("location_code", airportCode) }
            order("sort_order", Order.ASCENDING)
        }.decodeList<CarDto>().map { it.toModel() }
    }

    override suspend fun reserve(
        car: Car,
        rental: Rental,
        details: DriverDetails,
        attemptId: String,
    ): Outcome<CarReservation, ReservationError> = try {
        supabase.auth.awaitInitialization()
        val row = supabase.postgrest.rpc(
            "reserve_car",
            buildJsonObject {
                put("p_car", car.id)
                put("p_pickup_location", rental.pickup.label)
                put("p_dropoff_location", rental.dropoff.label)
                // The airport's wall-clock time, sent as UTC: the server only compares the two.
                put("p_pickup_at", rental.pickupAt.atOffset(ZoneOffset.UTC).toString())
                put("p_dropoff_at", rental.dropoffAt.atOffset(ZoneOffset.UTC).toString())
                put("p_idempotency_key", attemptId)
                put("p_driver_name", details.driver.name.trim())
                put("p_driver_email", details.driver.email.trim())
                put("p_driver_phone", details.driver.phone.trim().ifEmpty { null })
                put("p_driver_age", details.age.apiValue)
                put("p_flight_number", details.flightNumber.trim().ifEmpty { null })
                putJsonArray("p_extras") { details.extras.forEach { add(it.apiValue) } }
            },
        ).decodeList<CarReservationDto>().single()
        Outcome.Success(row.toModel())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestException) {
        Outcome.Failure(ReservationError.Network)
    } catch (e: RestException) {
        Log.w("Skypass", "Rental refused: ${e.error}", e)
        val text = "${e.error} ${e.description}"
        Outcome.Failure(
            when {
                "invalid_dates" in text -> ReservationError.InvalidDates
                "invalid_driver" in text || "invalid_extra" in text -> ReservationError.InvalidDetails
                else -> ReservationError.Unknown
            },
        )
    } catch (e: Exception) {
        Log.w("Skypass", "Rental failed", e)
        Outcome.Failure(ReservationError.Unknown)
    }
}
