package com.skypass.core.testing

import com.skypass.core.domain.repository.EditProfileRepository
import com.skypass.core.domain.repository.ProfileStatsRepository
import com.skypass.core.domain.repository.SavedDestinationsRepository
import com.skypass.core.domain.repository.SavedListRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.FlightStatus
import com.skypass.core.model.Outcome
import com.skypass.core.model.ProfileStats
import com.skypass.core.model.SavedDestination
import com.skypass.core.model.SavedFlight
import com.skypass.core.model.SavedHotel
import com.skypass.core.model.Tier
import com.skypass.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDateTime

object TestSaved {
    private val SAVED = Instant.parse("2026-11-10T09:00:00Z")

    /** The board's Profile tiles: 14 trips, 9 countries, 48.2k miles, Gold. */
    val STATS = ProfileStats(trips = 14, countries = 9, miles = 48_200, tier = Tier.Gold, nextTier = Tier.Platinum, milesToNext = 1_800)

    val FLIGHT = SavedFlight(
        instanceId = "i-9", savedAt = SAVED, airlineCode = "NZ", airlineName = "Air New Zealand", airlineColor = 0xFF1C2550,
        airlineLogoUrl = null, flightNumber = "NZ5", originCode = "LAX", originCity = "Los Angeles", destinationCode = "AKL",
        destinationCity = "Auckland", departure = LocalDateTime.of(2026, 11, 22, 21, 30), durationMinutes = 780, stops = 0,
        fareUsd = 899, status = FlightStatus.OnTime,
    )
    val DEPARTED = FLIGHT.copy(instanceId = "i-1", departure = LocalDateTime.of(2026, 11, 2, 9, 0), status = FlightStatus.Departed)
    val HOTEL = SavedHotel(TestHotels.WAITEMATA, SAVED)
    val DESTINATION = SavedDestination(TestExplore.QUEENSTOWN, SAVED)
}

class FakeProfileStatsRepository(var result: Outcome<ProfileStats, AppError> = Outcome.Success(TestSaved.STATS)) : ProfileStatsRepository {
    override suspend fun stats(): Outcome<ProfileStats, AppError> = result
}

class FakeEditProfileRepository(var fail: Boolean = false) : EditProfileRepository {
    val updates = mutableListOf<Pair<String, String>>()
    override suspend fun update(fullName: String, homeAirport: String): Outcome<UserProfile, AppError> {
        if (fail) return Outcome.Failure(AppError.Network)
        updates += fullName to homeAirport
        return Outcome.Success(UserProfile("u-1", fullName, "tina.anderson@gmail.com", "Blue", homeAirport, 0))
    }
}

class FakeSavedListRepository(
    var flights: List<SavedFlight> = listOf(TestSaved.FLIGHT, TestSaved.DEPARTED),
    var hotels: List<SavedHotel> = listOf(TestSaved.HOTEL),
    var destinations: List<SavedDestination> = listOf(TestSaved.DESTINATION),
    var failure: AppError? = null,
) : SavedListRepository {
    val origins = mutableListOf<String>()
    override suspend fun flights(): Outcome<List<SavedFlight>, AppError> = failure?.let { Outcome.Failure(it) } ?: Outcome.Success(flights)
    override suspend fun hotels(): Outcome<List<SavedHotel>, AppError> = failure?.let { Outcome.Failure(it) } ?: Outcome.Success(hotels)
    override suspend fun destinations(originCode: String): Outcome<List<SavedDestination>, AppError> {
        origins += originCode
        return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(destinations)
    }
}

class FakeSavedDestinationsRepository : SavedDestinationsRepository {
    val ids = MutableStateFlow<Set<String>>(emptySet())
    override fun savedDestinationIds(): Flow<Set<String>> = ids
    override suspend fun setSaved(destinationId: String, saved: Boolean): Outcome<Unit, AppError> {
        ids.update { if (saved) it + destinationId else it - destinationId }
        return Outcome.Success(Unit)
    }
}
