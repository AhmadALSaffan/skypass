package com.skypass.core.data.remote

import com.skypass.core.model.FlightStatus
import com.skypass.core.model.ProfileStats
import com.skypass.core.model.SavedFlight
import com.skypass.core.model.Tier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/** Row of `profile_stats()`. */
@Serializable
internal data class ProfileStatsDto(
    val trips: Int,
    val countries: Int,
    val miles: Int,
    val tier: String,
    @SerialName("next_tier") val nextTier: String? = null,
    @SerialName("miles_to_next") val milesToNext: Int = 0,
) {
    fun toModel() = ProfileStats(
        trips = trips,
        countries = countries,
        miles = miles,
        tier = Tier.fromApi(tier) ?: Tier.Blue,
        nextTier = Tier.fromApi(nextTier),
        milesToNext = milesToNext,
    )
}

/** Row of `saved_flights()`. */
@Serializable
internal data class SavedFlightDto(
    @SerialName("instance_id") val instanceId: String,
    @SerialName("saved_at") val savedAt: String,
    @SerialName("airline_code") val airlineCode: String,
    @SerialName("airline_name") val airlineName: String,
    @SerialName("airline_color") val airlineColor: String? = null,
    @SerialName("airline_logo") val airlineLogo: String? = null,
    @SerialName("flight_number") val flightNumber: String,
    val origin: String,
    @SerialName("origin_city") val originCity: String,
    val destination: String,
    @SerialName("destination_city") val destinationCity: String,
    @SerialName("departure_local") val departureLocal: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val stops: Int = 0,
    @SerialName("fare_usd") val fareUsd: Double,
    val status: String,
) {
    fun toModel() = SavedFlight(
        instanceId = instanceId,
        savedAt = OffsetDateTime.parse(savedAt).toInstant(),
        airlineCode = airlineCode,
        airlineName = airlineName,
        airlineColor = airlineColor?.let(::parseHex) ?: 0xFF1C2550,
        airlineLogoUrl = airlineLogo,
        flightNumber = flightNumber,
        originCode = origin.trim(),
        originCity = originCity,
        destinationCode = destination.trim(),
        destinationCity = destinationCity,
        departure = LocalDateTime.parse(departureLocal),
        durationMinutes = durationMinutes,
        stops = stops,
        fareUsd = fareUsd.roundToInt(),
        status = FlightStatus.fromApi(status),
    )
}

/** Row of `saved_hotels()`: a hotel plus when it was saved. */
@Serializable
internal data class SavedHotelDto(
    val id: String,
    val city: String,
    val name: String,
    val area: String,
    @SerialName("distance_km") val distanceKm: Double,
    val stars: Int,
    val rating: Double,
    @SerialName("review_count") val reviewCount: Int,
    @SerialName("price_usd") val priceUsd: Double,
    @SerialName("free_cancel") val freeCancel: Boolean,
    val amenities: List<String> = emptyList(),
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("saved_at") val savedAt: String,
) {
    fun hotel() = HotelDto(id, city, name, area, distanceKm, stars, rating, reviewCount, priceUsd, freeCancel, amenities, imageUrl).toModel()
}

/** Row of `saved_destinations(p_origin)`. */
@Serializable
internal data class SavedDestinationDto(
    val id: String,
    val city: String,
    val country: String,
    @SerialName("airport_code") val airportCode: String,
    val category: String,
    @SerialName("from_price_usd") val fromPriceUsd: Double,
    val tone: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("from_home") val fromHome: Boolean = false,
    @SerialName("saved_at") val savedAt: String,
) {
    fun destination() = ExploreDestinationDto(id, city, country, airportCode, category, fromPriceUsd, tone, imageUrl, fromHome).toModel()
}

/** What Personal information may change. */
@Serializable
internal data class ProfileUpdateDto(
    @SerialName("full_name") val fullName: String,
    @SerialName("home_airport") val homeAirport: String,
)
