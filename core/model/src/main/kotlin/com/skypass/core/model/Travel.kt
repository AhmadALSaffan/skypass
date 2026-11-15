package com.skypass.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Airport(
    val code: String,
    val city: String,
    val country: String,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** A position on Earth, in degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Cabin with the board's labels and the fare multiplier the server applies. */
enum class Cabin(val label: String, val short: String, val apiValue: String) {
    Economy("Economy", "Econ", "economy"),
    Premium("Premium", "Prem", "premium"),
    Business("Business", "Biz", "business"),
    First("First", "First", "first");

    /** The board cycles cabins on tap: Economy → Premium → Business → First → Economy. */
    fun next(): Cabin = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromApi(value: String): Cabin = entries.firstOrNull { it.apiValue == value } ?: Economy
    }
}

enum class TripType { OneWay, RoundTrip, MultiCity }

/** Everything the search card collects. */
data class FlightSearchQuery(
    val tripType: TripType,
    val from: Airport,
    val to: Airport,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val adults: Int,
    val cabin: Cabin,
) {
    val isValid: Boolean
        get() = from.code != to.code &&
            adults in MIN_ADULTS..MAX_ADULTS &&
            (returnDate == null || !returnDate.isBefore(departDate)) &&
            // A round trip needs the return date the traveller picked; we never guess it.
            (tripType != TripType.RoundTrip || returnDate != null)

    companion object {
        const val MIN_ADULTS = 1
        const val MAX_ADULTS = 9
    }
}

data class Destination(
    val id: String,
    val city: String,
    val country: String,
    val airportCode: String,
    val category: String,
    val fromPriceUsd: Int,
    /** Pastel ARGB from the board, e.g. 0xFFCDEBE6. Shown while the photo loads. */
    val tone: Long,
    val imageUrl: String? = null,
    /** True when the price is a real fare from the traveller's airport, not the catalogue "from". */
    val pricedFromOrigin: Boolean = false,
)

enum class FlightStatus { OnTime, Delayed, Boarding, Departed, Cancelled;

    companion object {
        fun fromApi(value: String): FlightStatus = when (value) {
            "delayed" -> Delayed
            "boarding" -> Boarding
            "departed" -> Departed
            "cancelled" -> Cancelled
            else -> OnTime
        }
    }
}

/** The next booked flight, as shown on Home and (later) the boarding pass. */
data class UpcomingTrip(
    val bookingId: String,
    val pnr: String?,
    val seat: String,
    val cabin: Cabin,
    val instanceId: String,
    val airlineCode: String,
    val airlineName: String,
    val flightNumber: String,
    val originCode: String,
    val originCity: String,
    val destinationCode: String,
    val destinationCity: String,
    val departure: LocalDateTime,
    val durationMinutes: Int,
    val gate: String,
    val boardingTime: LocalTime,
    val status: FlightStatus,
    val delayMinutes: Int,
    val airlineColor: Long = 0xFF1C2550,
    val airlineLogoUrl: String? = null,
)

data class UserProfile(
    val id: String,
    val fullName: String?,
    val email: String?,
    val tier: String,
    val homeAirport: String,
    val miles: Int,
)

/** Explore's filter chips, in the board's order. [All] shows everything. */
enum class ExploreCategory(val label: String) {
    All("All"), Beaches("Beaches"), Cities("Cities"), Mountains("Mountains"), Islands("Islands");

    fun matches(destination: Destination): Boolean = this == All || destination.category.equals(label, ignoreCase = true)
}

/** A featured fare on Explore, e.g. "Fly to Aotearoa this summer · Nonstop JFK → AKL · from $899". */
data class Deal(
    val id: String,
    val title: String,
    val airlineCode: String,
    val originCode: String,
    val destinationCode: String,
    val nonstop: Boolean,
    val fromPriceUsd: Int,
    val tone: Long,
    val imageUrl: String? = null,
)

/** What Explore found for a traveller flying from [originCode]. */
data class ExploreCatalog(
    val originCode: String,
    val deal: Deal?,
    val destinations: List<Destination>,
)

/**
 * A search another tab asks Home to set up: TO is always given, FROM only when it matters (a deal
 * departs from a fixed airport). Codes, not airports, so any tab can make one.
 */
data class SearchIntent(val toCode: String, val fromCode: String? = null)
