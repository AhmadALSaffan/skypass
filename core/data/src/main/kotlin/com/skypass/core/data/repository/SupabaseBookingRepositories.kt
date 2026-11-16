package com.skypass.core.data.repository

import android.util.Log
import com.skypass.core.data.remote.FlightOfferDto
import com.skypass.core.data.remote.parseHex
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
import com.skypass.core.model.PassengerType
import com.skypass.core.model.PassportType
import com.skypass.core.model.PaymentMethodType
import com.skypass.core.model.PromoCode
import com.skypass.core.model.Seat
import com.skypass.core.model.SeatMap
import com.skypass.core.model.SeatType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

internal class SupabaseFlightOfferRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : FlightOfferRepository {
    override suspend fun offer(instanceId: String, cabin: Cabin, passengers: Int): Outcome<FlightOffer, AppError> {
        val result = supabase.safeCall {
            supabase.postgrest.rpc(
                "flight_offer",
                buildJsonObject {
                    put("p_instance_id", instanceId)
                    put("p_cabin", cabin.apiValue)
                    put("p_passengers", passengers)
                },
            ).decodeList<FlightOfferDto>().firstOrNull()?.toModel()
        }
        return when (result) {
            is Outcome.Success -> result.value?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.NotFound)
            is Outcome.Failure -> result
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Seat map
// ---------------------------------------------------------------------------------------------

@Serializable
private data class SeatDto(
    val seat: String,
    @SerialName("row_num") val row: Int,
    val letter: String,
    @SerialName("seat_type") val type: String,
    @SerialName("extra_fee_usd") val extraFeeUsd: Double,
    val taken: Boolean,
    val layout: String,
    @SerialName("exit_row") val exitRow: Boolean,
    val cabin: String,
) {
    fun toModel() = Seat(
        code = seat,
        row = row,
        letter = letter,
        type = when (type) {
            "legroom" -> SeatType.Legroom
            "premium" -> SeatType.Premium
            "business" -> SeatType.Business
            "first" -> SeatType.First
            else -> SeatType.Standard
        },
        extraFeeUsd = extraFeeUsd.toInt(),
        taken = taken,
        exitRow = exitRow,
    )
}

internal class SupabaseSeatMapRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : SeatMapRepository {
    override suspend fun seatMap(instanceId: String, cabin: Cabin): Outcome<SeatMap, AppError> {
        val result = supabase.safeCall {
            supabase.postgrest.rpc(
                "get_seat_map",
                buildJsonObject {
                    put("p_instance_id", instanceId)
                    put("p_cabin", cabin.apiValue)
                },
            ).decodeList<SeatDto>()
        }
        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                val first = result.value.firstOrNull() ?: return Outcome.Failure(AppError.NotFound)
                Outcome.Success(SeatMap(first.layout, Cabin.fromApi(first.cabin), result.value.map { it.toModel() }))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Passengers
// ---------------------------------------------------------------------------------------------

@Serializable
private data class PassengerDto(
    val id: String? = null,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("passenger_type") val type: String = "adult",
    @SerialName("passport_type") val passportType: String? = null,
    @SerialName("passport_number") val passportNumber: String? = null,
    @SerialName("passport_issue_date") val passportIssueDate: String? = null,
    @SerialName("passport_expiry_date") val passportExpiryDate: String? = null,
    val gender: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false,
) {
    fun toModel() = Passenger(
        id = id,
        firstName = firstName,
        lastName = lastName.orEmpty(),
        type = when (type) {
            "child" -> PassengerType.Child
            "infant" -> PassengerType.Infant
            else -> PassengerType.Adult
        },
        passportType = PassportType.fromApi(passportType),
        passportNumber = passportNumber,
        passportIssueDate = passportIssueDate?.let(LocalDate::parse),
        passportExpiryDate = passportExpiryDate?.let(LocalDate::parse),
        gender = Gender.fromApi(gender),
        phone = phone,
        email = email,
        isPrimary = isPrimary,
    )
}

private fun Passenger.toDto() = PassengerDto(
    id = id,
    firstName = firstName,
    lastName = lastName.ifBlank { null },
    type = type.name.lowercase(),
    passportType = passportType?.apiValue,
    passportNumber = passportNumber,
    passportIssueDate = passportIssueDate?.toString(),
    passportExpiryDate = passportExpiryDate?.toString(),
    gender = gender?.apiValue,
    phone = phone,
    email = email,
    isPrimary = isPrimary,
)

private const val PASSENGER_COLUMNS =
    "id,first_name,last_name,passenger_type,passport_type,passport_number,passport_issue_date,passport_expiry_date,gender,phone,email,is_primary"

internal class SupabasePassengerRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : PassengerRepository {

    override suspend fun passengers(): Outcome<List<Passenger>, AppError> = supabase.safeCall {
        supabase.from("passengers").select(io.github.jan.supabase.postgrest.query.Columns.raw(PASSENGER_COLUMNS)) {
            order("is_primary", Order.DESCENDING)
            order("created_at", Order.ASCENDING)
        }.decodeList<PassengerDto>().map { it.toModel() }
    }

    override suspend fun save(passenger: Passenger): Outcome<Passenger, AppError> = supabase.safeCall {
        val dto = passenger.toDto()
        val columns = io.github.jan.supabase.postgrest.query.Columns.raw(PASSENGER_COLUMNS)
        if (passenger.id == null) {
            supabase.from("passengers").insert(dto.copy(id = null)) { select(columns) }.decodeSingle<PassengerDto>()
        } else {
            supabase.from("passengers").update(dto.copy(id = null)) {
                filter { eq("id", passenger.id!!) }
                select(columns)
            }.decodeSingle<PassengerDto>()
        }.toModel()
    }

    override suspend fun delete(passengerId: String): Outcome<Unit, AppError> = supabase.safeCall {
        supabase.from("passengers").delete { filter { eq("id", passengerId) } }
        Unit
    }
}

// ---------------------------------------------------------------------------------------------
// Promo codes
// ---------------------------------------------------------------------------------------------

@Serializable
private data class PromoDto(val code: String, @SerialName("percent_off") val percentOff: Int, val description: String)

internal class SupabasePromoRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : PromoRepository {
    override suspend fun check(code: String): Outcome<PromoCode?, AppError> = supabase.safeCall {
        supabase.postgrest.rpc("check_promo", buildJsonObject { put("p_code", code) })
            .decodeList<PromoDto>()
            .firstOrNull()
            ?.let { PromoCode(it.code, it.percentOff, it.description) }
    }
}

// ---------------------------------------------------------------------------------------------
// Bookings, payment, boarding passes
// ---------------------------------------------------------------------------------------------

@Serializable
private data class BookingRowDto(
    val id: String,
    val pnr: String? = null,
    @SerialName("total_usd") val totalUsd: Double,
)

@Serializable
private data class BoardingPassDto(
    @SerialName("booking_id") val bookingId: String,
    val pnr: String? = null,
    val seat: String,
    val cabin: String,
    @SerialName("boarding_zone") val zone: String,
    @SerialName("airline_code") val airlineCode: String,
    @SerialName("airline_name") val airlineName: String,
    @SerialName("airline_color") val airlineColor: String,
    @SerialName("airline_logo") val airlineLogo: String? = null,
    @SerialName("flight_number") val flightNumber: String,
    val origin: String,
    @SerialName("origin_city") val originCity: String,
    val destination: String,
    @SerialName("destination_city") val destinationCity: String,
    @SerialName("departure_local") val departureLocal: String,
    @SerialName("arrival_local") val arrivalLocal: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val gate: String,
    @SerialName("boarding_time") val boardingTime: String,
    @SerialName("passenger_names") val passengerNames: List<String> = emptyList(),
    @SerialName("passenger_seats") val passengerSeats: List<String> = emptyList(),
) {
    /** One pass per traveller; a booking without named travellers still gets one pass. */
    fun toModels(): List<BoardingPass> {
        val seats = passengerSeats.ifEmpty { listOf(seat) }
        return seats.mapIndexed { i, s ->
            BoardingPass(
                bookingId = bookingId,
                pnr = pnr.orEmpty(),
                passengerName = passengerNames.getOrNull(i) ?: "Traveller",
                seat = s,
                cabin = Cabin.fromApi(cabin),
                zone = zone,
                airlineCode = airlineCode,
                airlineName = airlineName,
                airlineColor = parseHex(airlineColor),
                airlineLogoUrl = airlineLogo,
                flightNumber = flightNumber,
                originCode = origin.trim(),
                originCity = originCity,
                destinationCode = destination.trim(),
                destinationCity = destinationCity,
                departure = LocalDateTime.parse(departureLocal),
                arrival = LocalDateTime.parse(arrivalLocal),
                durationMinutes = durationMinutes,
                gate = gate,
                boardingTime = LocalTime.parse(boardingTime),
            )
        }
    }
}

internal class SupabaseBookingRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : BookingRepository {

    override suspend fun create(
        instanceId: String,
        cabin: Cabin,
        seats: List<String>,
        passengerIds: List<String>,
        bags: Int,
        promoCode: String?,
        idempotencyKey: String,
    ): Outcome<String, BookingError> = bookingCall {
        supabase.postgrest.rpc(
            "create_booking",
            buildJsonObject {
                put("p_instance_id", instanceId)
                put("p_cabin", cabin.apiValue)
                putJsonArray("p_seats") { seats.forEach { add(it) } }
                put("p_bags", bags)
                put("p_idempotency_key", idempotencyKey)
                putJsonArray("p_passenger_ids") { passengerIds.forEach { add(it) } }
                put("p_promo", promoCode)
            },
        ).decodeAs<BookingRowDto>().id
    }

    override suspend fun pay(bookingId: String, method: PaymentMethodType): Outcome<ConfirmedBooking, BookingError> = bookingCall {
        val row = supabase.postgrest.rpc(
            "pay_booking",
            buildJsonObject {
                put("p_booking_id", bookingId)
                put("p_method", method.apiValue)
            },
        ).decodeAs<BookingRowDto>()
        ConfirmedBooking(row.id, row.pnr.orEmpty(), row.totalUsd.toInt())
    }

    override suspend fun boardingPasses(bookingId: String): Outcome<List<BoardingPass>, AppError> = supabase.safeCall {
        supabase.postgrest.rpc("boarding_pass", buildJsonObject { put("p_booking_id", bookingId) })
            .decodeList<BoardingPassDto>()
            .flatMap { it.toModels() }
    }

    /** Maps the server's raised errors (seat_taken, invalid_promo …) to what the UI explains. */
    private suspend fun <T> bookingCall(block: suspend () -> T): Outcome<T, BookingError> = try {
        supabase.auth.awaitInitialization()
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestException) {
        Outcome.Failure(BookingError.Network)
    } catch (e: RestException) {
        Log.w("Skypass", "Booking refused: ${e.error}", e)
        val text = "${e.error} ${e.description}"
        Outcome.Failure(
            when {
                "seat_taken" in text || "duplicate_seat" in text -> BookingError.SeatTaken
                "invalid_promo" in text -> BookingError.InvalidPromo
                else -> BookingError.Unknown
            },
        )
    } catch (e: Exception) {
        Log.w("Skypass", "Booking failed", e)
        Outcome.Failure(BookingError.Unknown)
    }
}
