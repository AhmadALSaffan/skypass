package com.skypass.core.data.remote

import com.skypass.core.model.Deal
import com.skypass.core.model.Destination
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `explore_destinations(p_origin)`. */
@Serializable
internal data class ExploreDestinationDto(
    val id: String,
    val city: String,
    val country: String,
    @SerialName("airport_code") val airportCode: String,
    val category: String,
    @SerialName("from_price_usd") val fromPriceUsd: Double,
    val tone: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("from_home") val fromHome: Boolean = false,
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
        pricedFromOrigin = fromHome,
    )
}

@Serializable
internal data class DealDto(
    val id: String,
    val title: String,
    @SerialName("airline_code") val airlineCode: String,
    val origin: String,
    val destination: String,
    val nonstop: Boolean,
    @SerialName("from_price_usd") val fromPriceUsd: Double,
    val tone: String,
    @SerialName("image_url") val imageUrl: String? = null,
) {
    fun toModel() = Deal(
        id = id,
        title = title,
        airlineCode = airlineCode.trim(),
        originCode = origin.trim(),
        destinationCode = destination.trim(),
        nonstop = nonstop,
        fromPriceUsd = fromPriceUsd.toInt(),
        tone = parseHex(tone),
        imageUrl = imageUrl,
    )
}
