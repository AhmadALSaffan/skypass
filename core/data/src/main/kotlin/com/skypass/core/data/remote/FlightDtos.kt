package com.skypass.core.data.remote

import com.skypass.core.model.DayPrice
import com.skypass.core.model.FlightOffer
import com.skypass.core.model.FlightStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Row of `search_flights(...)`. */
@Serializable
internal data class FlightOfferDto(
    @SerialName("instance_id") val instanceId: String,
    @SerialName("flight_id") val flightId: String,
    @SerialName("airline_code") val airlineCode: String,
    @SerialName("airline_name") val airlineName: String,
    @SerialName("airline_color") val airlineColor: String,
    @SerialName("airline_logo") val airlineLogo: String? = null,
    @SerialName("flight_number") val flightNumber: String,
    val aircraft: String,
    val origin: String,
    @SerialName("origin_city") val originCity: String,
    val destination: String,
    @SerialName("destination_city") val destinationCity: String,
    @SerialName("departure_local") val departureLocal: String,
    @SerialName("arrival_local") val arrivalLocal: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val stops: Int,
    @SerialName("stop_airport") val stopAirport: String? = null,
    val gate: String,
    @SerialName("boarding_time") val boardingTime: String,
    val status: String,
    val rating: Double,
    val perks: List<String> = emptyList(),
    @SerialName("price_usd") val priceUsd: Double,
    @SerialName("total_usd") val totalUsd: Double,
    @SerialName("is_best_value") val isBestValue: Boolean,
) {
    fun toModel() = FlightOffer(
        instanceId = instanceId,
        flightId = flightId,
        airlineCode = airlineCode,
        airlineName = airlineName,
        airlineColor = parseHex(airlineColor),
        airlineLogoUrl = airlineLogo,
        flightNumber = flightNumber,
        aircraft = aircraft,
        originCode = origin.trim(),
        originCity = originCity,
        destinationCode = destination.trim(),
        destinationCity = destinationCity,
        departure = LocalDateTime.parse(departureLocal),
        arrival = LocalDateTime.parse(arrivalLocal),
        durationMinutes = durationMinutes,
        stops = stops,
        stopAirport = stopAirport?.trim(),
        gate = gate,
        boardingTime = LocalTime.parse(boardingTime),
        status = FlightStatus.fromApi(status),
        rating = rating,
        perks = perks,
        priceUsd = priceUsd.toInt(),
        totalUsd = totalUsd.toInt(),
        isBestValue = isBestValue,
    )
}

/** Row of `date_price_strip(...)`. */
@Serializable
internal data class DayPriceDto(
    val day: String,
    @SerialName("min_price_usd") val minPriceUsd: Double? = null,
) {
    fun toModel() = DayPrice(LocalDate.parse(day), minPriceUsd?.toInt())
}

@Serializable
internal data class SavedItemDto(
    val kind: String,
    @SerialName("item_id") val itemId: String,
)
