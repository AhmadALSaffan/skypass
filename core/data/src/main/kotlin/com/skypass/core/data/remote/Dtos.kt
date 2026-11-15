package com.skypass.core.data.remote

import com.skypass.core.model.Airport
import com.skypass.core.model.Cabin
import com.skypass.core.model.Destination
import com.skypass.core.model.FlightStatus
import com.skypass.core.model.UpcomingTrip
import com.skypass.core.model.UserProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.LocalTime

// Wire shapes of Supabase tables / RPCs (snake_case) and their mapping to domain models.

@Serializable
internal data class AirportDto(
    val code: String,
    val city: String,
    val country: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
) {
    fun toModel() = Airport(code = code.trim(), city = city, country = country, name = name, latitude = lat, longitude = lon)
}

@Serializable
internal data class DestinationDto(
    val id: String,
    val city: String,
    val country: String,
    @SerialName("airport_code") val airportCode: String,
    val category: String,
    @SerialName("from_price_usd") val fromPriceUsd: Double,
    val tone: String,
    @SerialName("image_url") val imageUrl: String? = null,
) {
    fun toModel() = Destination(
        id = id,
        city = city,
        country = country,
        airportCode = airportCode.trim(),
        category = category,
        fromPriceUsd = fromPriceUsd.toInt(),
        tone = parseHex(tone),
        imageUrl = imageUrl,
    )
}

@Serializable
internal data class ProfileDto(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    val tier: String,
    @SerialName("home_airport") val homeAirport: String? = null,
    val miles: Int,
) {
    fun toModel() = UserProfile(
        id = id,
        fullName = fullName,
        email = email,
        tier = tier,
        homeAirport = homeAirport?.trim() ?: "JFK",
        miles = miles,
    )
}

/** Row of `next_trip()`. Also the offline cache format. */
@Serializable
internal data class TripDto(
    @SerialName("booking_id") val bookingId: String,
    val pnr: String? = null,
    val seat: String,
    val cabin: String,
    @SerialName("instance_id") val instanceId: String,
    @SerialName("airline_code") val airlineCode: String,
    @SerialName("airline_name") val airlineName: String,
    // Optional so trips cached before logos existed still decode.
    @SerialName("airline_color") val airlineColor: String? = null,
    @SerialName("airline_logo") val airlineLogo: String? = null,
    @SerialName("flight_number") val flightNumber: String,
    val origin: String,
    @SerialName("origin_city") val originCity: String,
    val destination: String,
    @SerialName("destination_city") val destinationCity: String,
    @SerialName("departure_local") val departureLocal: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val gate: String,
    @SerialName("boarding_time") val boardingTime: String,
    val status: String,
    @SerialName("delay_minutes") val delayMinutes: Int,
) {
    fun toModel() = UpcomingTrip(
        bookingId = bookingId,
        pnr = pnr,
        seat = seat,
        cabin = Cabin.fromApi(cabin),
        instanceId = instanceId,
        airlineCode = airlineCode,
        airlineName = airlineName,
        flightNumber = flightNumber,
        originCode = origin.trim(),
        originCity = originCity,
        destinationCode = destination.trim(),
        destinationCity = destinationCity,
        departure = LocalDateTime.parse(departureLocal),
        durationMinutes = durationMinutes,
        gate = gate,
        boardingTime = LocalTime.parse(boardingTime),
        status = FlightStatus.fromApi(status),
        delayMinutes = delayMinutes,
        airlineColor = airlineColor?.let(::parseHex) ?: 0xFF1C2550,
        airlineLogoUrl = airlineLogo,
    )
}

/** "#CDEBE6" → 0xFFCDEBE6. */
internal fun parseHex(hex: String): Long = 0xFF000000L or hex.removePrefix("#").toLong(16)
