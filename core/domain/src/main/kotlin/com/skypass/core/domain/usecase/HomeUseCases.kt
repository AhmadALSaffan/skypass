package com.skypass.core.domain.usecase

import com.skypass.core.domain.repository.AirportRepository
import com.skypass.core.domain.repository.DestinationRepository
import com.skypass.core.domain.repository.LocationRepository
import com.skypass.core.domain.repository.ProfileRepository
import com.skypass.core.domain.repository.TripRepository
import com.skypass.core.model.Airport
import com.skypass.core.model.AppError
import com.skypass.core.model.Cabin
import com.skypass.core.model.Destination
import com.skypass.core.model.FlightSearchQuery
import com.skypass.core.model.GeoPoint
import com.skypass.core.model.Outcome
import com.skypass.core.model.TripType
import com.skypass.core.model.UpcomingTrip
import com.skypass.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject

enum class DayPart { Morning, Afternoon, Evening }

/** "Good morning / afternoon / evening" from the phone's clock. */
class GreetingUseCase @Inject constructor(private val clock: Clock) {
    operator fun invoke(): DayPart {
        val hour = LocalTime.now(clock).hour
        return when {
            hour in 5..11 -> DayPart.Morning
            hour in 12..17 -> DayPart.Afternoon
            else -> DayPart.Evening
        }
    }
}

class GetProfileUseCase @Inject constructor(private val profiles: ProfileRepository) {
    suspend operator fun invoke(): Outcome<UserProfile, AppError> = profiles.profile()
}

class ObserveUpcomingTripUseCase @Inject constructor(private val trips: TripRepository) {
    operator fun invoke(): Flow<UpcomingTrip?> = trips.upcomingTrip()
}

class GetAirportsUseCase @Inject constructor(private val airports: AirportRepository) {
    suspend operator fun invoke(): Outcome<List<Airport>, AppError> = airports.airports()
}

/**
 * Airport picker search: exact code first, then codes and cities that start with the text, then
 * anything containing it (city, country or airport name).
 */
class SearchAirportsUseCase @Inject constructor() {
    operator fun invoke(all: List<Airport>, text: String): List<Airport> {
        val q = text.trim()
        if (q.isEmpty()) return all.sortedBy { it.city }
        fun rank(a: Airport): Int? = when {
            a.code.equals(q, ignoreCase = true) -> 0
            a.code.startsWith(q, ignoreCase = true) -> 1
            a.city.startsWith(q, ignoreCase = true) -> 2
            a.city.contains(q, ignoreCase = true) ||
                a.country.contains(q, ignoreCase = true) ||
                a.name.contains(q, ignoreCase = true) -> 3
            else -> null
        }
        return all.mapNotNull { a -> rank(a)?.let { it to a } }
            .sortedWith(compareBy({ it.first }, { it.second.city }))
            .map { it.second }
    }
}

/** Home's curated list for the home airport, falling back to featured destinations. */
class GetPopularDestinationsUseCase @Inject constructor(private val destinations: DestinationRepository) {
    suspend operator fun invoke(homeAirport: String): Outcome<List<Destination>, AppError> =
        when (val popular = destinations.popularFrom(homeAirport)) {
            is Outcome.Success -> if (popular.value.isNotEmpty()) popular else destinations.featured()
            is Outcome.Failure -> popular
        }
}

/** The search card's starting point: one adult, Economy, departing in 5 days, back a week later. */
class CreateDefaultSearchUseCase @Inject constructor(private val clock: Clock) {
    operator fun invoke(from: Airport, to: Airport): FlightSearchQuery {
        val depart = LocalDate.now(clock).plusDays(DEFAULT_LEAD_DAYS)
        return FlightSearchQuery(
            tripType = TripType.OneWay,
            from = from,
            to = to,
            departDate = depart,
            returnDate = null,
            adults = 1,
            cabin = Cabin.Economy,
        )
    }

    companion object {
        const val DEFAULT_LEAD_DAYS = 5L
        const val DEFAULT_STAY_DAYS = 7L
        /** Departures are on sale this many days ahead. */
        const val BOOKING_WINDOW_DAYS = 89L
    }
}

/** The airport closest to the traveller (great-circle distance), or null without a location. */
class FindNearestAirportUseCase @Inject constructor(private val location: LocationRepository) {
    suspend operator fun invoke(airports: List<Airport>): Airport? {
        val here = location.currentLocation() ?: return null
        return nearest(here, airports)
    }

    companion object {
        fun nearest(here: GeoPoint, airports: List<Airport>): Airport? = airports
            .filter { it.latitude != null && it.longitude != null }
            .minByOrNull { distanceKm(here, GeoPoint(it.latitude!!, it.longitude!!)) }

        /** Haversine distance in kilometres. */
        fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
            val r = 6371.0
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(h))
        }
    }
}
