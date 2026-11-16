package com.skypass.core.data.remote

import com.skypass.core.model.Hotel
import com.skypass.core.model.HotelReservation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
internal data class HotelDto(
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
) {
    fun toModel() = Hotel(
        id = id,
        city = city,
        name = name,
        area = area,
        distanceKm = distanceKm,
        stars = stars,
        rating = rating,
        reviewCount = reviewCount,
        pricePerNightUsd = priceUsd.roundToInt(),
        freeCancellation = freeCancel,
        amenities = amenities,
        imageUrl = imageUrl,
    )
}

/** Row of `reserve_hotel(...)`. */
@Serializable
internal data class HotelReservationDto(
    val reference: String,
    @SerialName("total_usd") val totalUsd: Double,
    val nights: Int,
) {
    fun toModel() = HotelReservation(reference = reference, totalUsd = totalUsd.roundToInt(), nights = nights)
}
