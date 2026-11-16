package com.skypass.core.data.remote

import com.skypass.core.model.ArrivalWindow
import com.skypass.core.model.CarBooking
import com.skypass.core.model.CarExtra
import com.skypass.core.model.ContactDetails
import com.skypass.core.model.DriverAge
import com.skypass.core.model.DriverDetails
import com.skypass.core.model.ReservationStatus
import com.skypass.core.model.RoomType
import com.skypass.core.model.StayBooking
import com.skypass.core.model.StayDetails
import com.skypass.core.model.StayRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** A row of `hotel_reservations` with its hotel embedded (`select=*,hotel:hotels(*)`). */
@Serializable
internal data class StayBookingDto(
    val reference: String,
    val room: String,
    @SerialName("check_in") val checkIn: String,
    @SerialName("check_out") val checkOut: String,
    val guests: Int,
    @SerialName("total_usd") val totalUsd: Double,
    @SerialName("nightly_usd") val nightlyUsd: Double? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("guest_name") val guestName: String? = null,
    @SerialName("guest_email") val guestEmail: String? = null,
    @SerialName("guest_phone") val guestPhone: String? = null,
    @SerialName("arrival_window") val arrivalWindow: String? = null,
    val requests: List<String> = emptyList(),
    val note: String? = null,
    val hotel: HotelDto,
) {
    fun toModel(): StayBooking {
        val checkIn = LocalDate.parse(checkIn)
        val checkOut = LocalDate.parse(checkOut)
        val nights = ChronoUnit.DAYS.between(checkIn, checkOut).toInt().coerceAtLeast(1)
        return StayBooking(
            reference = reference,
            hotel = hotel.toModel(),
            room = RoomType.entries.firstOrNull { it.apiValue == room } ?: RoomType.Standard,
            checkIn = checkIn,
            checkOut = checkOut,
            guests = guests,
            nightlyUsd = nightlyUsd?.roundToInt() ?: (totalUsd / nights).roundToInt(),
            totalUsd = totalUsd.roundToInt(),
            // Reservations made before the details step have no guest.
            details = guestName?.let {
                StayDetails(
                    guest = ContactDetails(it, guestEmail.orEmpty(), guestPhone.orEmpty()),
                    arrival = ArrivalWindow.fromApi(arrivalWindow) ?: ArrivalWindow.Evening,
                    requests = requests.mapNotNull(StayRequest::fromApi).toSet(),
                    note = note.orEmpty(),
                )
            },
            status = if (status == "cancelled") ReservationStatus.Cancelled else ReservationStatus.Confirmed,
            bookedAt = OffsetDateTime.parse(createdAt).toInstant(),
        )
    }
}

/** A row of `rental_reservations` with its car embedded (`select=*,car:cars(*)`). */
@Serializable
internal data class CarBookingDto(
    val reference: String? = null,
    @SerialName("pickup_location") val pickupLocation: String,
    @SerialName("dropoff_location") val dropoffLocation: String,
    @SerialName("pickup_at") val pickupAt: String,
    @SerialName("dropoff_at") val dropoffAt: String,
    val days: Int? = null,
    @SerialName("total_usd") val totalUsd: Double,
    @SerialName("daily_usd") val dailyUsd: Double? = null,
    @SerialName("extras_usd") val extrasUsd: Double = 0.0,
    @SerialName("young_driver_fee_usd") val youngDriverFeeUsd: Double = 0.0,
    @SerialName("one_way_fee_usd") val oneWayFeeUsd: Double = 0.0,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("driver_name") val driverName: String? = null,
    @SerialName("driver_email") val driverEmail: String? = null,
    @SerialName("driver_phone") val driverPhone: String? = null,
    @SerialName("driver_age") val driverAge: String? = null,
    @SerialName("flight_number") val flightNumber: String? = null,
    val extras: List<String> = emptyList(),
    val car: CarDto,
) {
    fun toModel(): CarBooking {
        // Stored as the airport's wall clock in UTC (see SupabaseCarRepository.reserve).
        val pickup = OffsetDateTime.parse(pickupAt).toLocalDateTime()
        val dropoff = OffsetDateTime.parse(dropoffAt).toLocalDateTime()
        val days = (days ?: 1).coerceAtLeast(1)
        return CarBooking(
            reference = reference.orEmpty(),
            car = car.toModel(),
            pickupLocation = pickupLocation,
            dropoffLocation = dropoffLocation,
            pickupAt = pickup,
            dropoffAt = dropoff,
            days = days,
            dailyUsd = dailyUsd?.roundToInt()
                ?: ((totalUsd - oneWayFeeUsd - extrasUsd - youngDriverFeeUsd) / days).roundToInt(),
            extrasUsd = extrasUsd.roundToInt(),
            youngDriverFeeUsd = youngDriverFeeUsd.roundToInt(),
            oneWayFeeUsd = oneWayFeeUsd.roundToInt(),
            totalUsd = totalUsd.roundToInt(),
            details = driverName?.let {
                DriverDetails(
                    driver = ContactDetails(it, driverEmail.orEmpty(), driverPhone.orEmpty()),
                    age = DriverAge.fromApi(driverAge) ?: DriverAge.Adult,
                    flightNumber = flightNumber.orEmpty(),
                    extras = extras.mapNotNull(CarExtra::fromApi).toSet(),
                )
            },
            status = if (status == "cancelled") ReservationStatus.Cancelled else ReservationStatus.Confirmed,
            bookedAt = OffsetDateTime.parse(createdAt).toInstant(),
        )
    }
}
