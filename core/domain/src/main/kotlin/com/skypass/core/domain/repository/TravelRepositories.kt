package com.skypass.core.domain.repository

import com.skypass.core.model.Airport
import com.skypass.core.model.AppError
import com.skypass.core.model.Destination
import com.skypass.core.model.GeoPoint
import com.skypass.core.model.Outcome
import com.skypass.core.model.UpcomingTrip
import com.skypass.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

// Small, single-purpose contracts (interface segregation): each screen depends only on what it reads.

interface AirportRepository {
    /** Every airport Skypass flies to. Cached after the first load. */
    suspend fun airports(): Outcome<List<Airport>, AppError>
}

interface DestinationRepository {
    /** "Popular from <airport>" on Home. */
    suspend fun popularFrom(airportCode: String): Outcome<List<Destination>, AppError>

    /** The Explore grid; also Home's fallback when an airport has no curated list. */
    suspend fun featured(): Outcome<List<Destination>, AppError>
}

interface TripRepository {
    /**
     * The next booked flight. Emits the cached trip first (works offline), then the fresh one, then
     * live gate/status changes while collected. Emits null when there is no trip.
     */
    fun upcomingTrip(): Flow<UpcomingTrip?>
}

interface ProfileRepository {
    suspend fun profile(): Outcome<UserProfile, AppError>
}

interface LocationRepository {
    /** Whether the user has allowed Skypass to use their location. */
    fun hasPermission(): Boolean

    /** A recent approximate position, or null if unavailable / not allowed. */
    suspend fun currentLocation(): GeoPoint?
}
