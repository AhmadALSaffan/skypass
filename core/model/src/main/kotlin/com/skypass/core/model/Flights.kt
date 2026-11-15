package com.skypass.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One bookable departure as shown on a results boarding-pass card. Prices are per person, USD. */
data class FlightOffer(
    val instanceId: String,
    val flightId: String,
    val airlineCode: String,
    val airlineName: String,
    /** Brand ARGB for the monogram, e.g. 0xFF005DAA. */
    val airlineColor: Long,
    /** Square airline logo; the monogram shows while it loads or if it can't. */
    val airlineLogoUrl: String? = null,
    val flightNumber: String,
    val aircraft: String,
    val originCode: String,
    val originCity: String,
    val destinationCode: String,
    val destinationCity: String,
    /** Local time at the origin airport. */
    val departure: LocalDateTime,
    /** Local time at the destination airport. */
    val arrival: LocalDateTime,
    val durationMinutes: Int,
    val stops: Int,
    val stopAirport: String?,
    val gate: String,
    val boardingTime: LocalTime,
    val status: FlightStatus,
    val rating: Double,
    val perks: List<String>,
    val priceUsd: Int,
    val totalUsd: Int,
    /** Cheapest nonstop on the day. */
    val isBestValue: Boolean,
) {
    val isNonstop: Boolean get() = stops == 0

    /** Arrives on a later calendar day than it leaves (shown as "+1"). */
    val arrivalDayOffset: Int get() = (arrival.toLocalDate().toEpochDay() - departure.toLocalDate().toEpochDay()).toInt()
}

/** Cheapest fare on a day, for the date strip. Null when nothing flies that day. */
data class DayPrice(val date: LocalDate, val minPriceUsd: Int?)

enum class SortOrder { Cheapest, Fastest, Earliest }

/** Part of the day a flight leaves in. */
enum class DepartureWindow(val fromHour: Int, val untilHour: Int) {
    Morning(0, 12),
    Afternoon(12, 18),
    Evening(18, 24);

    fun contains(time: LocalTime): Boolean = time.hour in fromHour until untilHour
}

data class FlightFilters(
    val nonstopOnly: Boolean = false,
    /** Empty = all airlines. */
    val airlines: Set<String> = emptySet(),
    /** Empty = any time. */
    val windows: Set<DepartureWindow> = emptySet(),
) {
    val activeCount: Int get() = (if (nonstopOnly) 1 else 0) + (if (airlines.isEmpty()) 0 else 1) + (if (windows.isEmpty()) 0 else 1)
}

/** Which leg of a trip is being chosen. */
enum class TripLeg { Outbound, Return }
