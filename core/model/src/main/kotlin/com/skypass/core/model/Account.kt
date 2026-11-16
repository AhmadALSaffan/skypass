package com.skypass.core.model

import java.time.Instant
import java.time.LocalDateTime

/** Skypass loyalty tiers, earned by miles flown: Silver at 10k, Gold at 25k, Platinum at 50k. */
enum class Tier(val label: String, val fromMiles: Int) {
    Blue("Skypass Blue", 0),
    Silver("Skypass Silver", 10_000),
    Gold("Skypass Gold", 25_000),
    Platinum("Skypass Platinum", 50_000);

    companion object {
        fun fromApi(value: String?): Tier? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/** The Profile tiles, counted on the server from the traveller's own bookings. */
data class ProfileStats(
    val trips: Int,
    val countries: Int,
    val miles: Int,
    val tier: Tier,
    val nextTier: Tier?,
    val milesToNext: Int,
) {
    /** How far along the way to [nextTier], 0..1 (1 at the top tier). */
    val progress: Float
        get() {
            val next = nextTier ?: return 1f
            val span = (next.fromMiles - tier.fromMiles).coerceAtLeast(1)
            return ((miles - tier.fromMiles).toFloat() / span).coerceIn(0f, 1f)
        }

    companion object {
        val Empty = ProfileStats(0, 0, 0, Tier.Blue, Tier.Silver, Tier.Silver.fromMiles)
    }
}

/** A departure bookmarked from the results. */
data class SavedFlight(
    val instanceId: String,
    val savedAt: Instant,
    val airlineCode: String,
    val airlineName: String,
    val airlineColor: Long,
    val airlineLogoUrl: String?,
    val flightNumber: String,
    val originCode: String,
    val originCity: String,
    val destinationCode: String,
    val destinationCity: String,
    /** Local time at the origin. */
    val departure: LocalDateTime,
    val durationMinutes: Int,
    val stops: Int,
    /** Today's economy fare for one adult. */
    val fareUsd: Int,
    val status: FlightStatus,
) {
    fun isBookable(now: LocalDateTime): Boolean = departure.isAfter(now) && status != FlightStatus.Cancelled
}

data class SavedHotel(val hotel: Hotel, val savedAt: Instant)

data class SavedDestination(val destination: Destination, val savedAt: Instant)
