package com.skypass.core.domain.usecase

import com.skypass.core.domain.repository.EditProfileRepository
import com.skypass.core.domain.repository.PassengerRepository
import com.skypass.core.domain.repository.ProfileRepository
import com.skypass.core.domain.repository.ProfileStatsRepository
import com.skypass.core.domain.repository.SavedDestinationsRepository
import com.skypass.core.domain.repository.SavedFlightsRepository
import com.skypass.core.domain.repository.SavedHotelsRepository
import com.skypass.core.domain.repository.SavedListRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Outcome
import com.skypass.core.model.Passenger
import com.skypass.core.model.ProfileStats
import com.skypass.core.model.SavedDestination
import com.skypass.core.model.SavedFlight
import com.skypass.core.model.SavedHotel
import com.skypass.core.model.UserProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** What the Profile screen shows besides the session: the account row, the tiles and the badges. */
data class ProfileOverview(
    val profile: UserProfile?,
    val stats: ProfileStats,
    val passengers: List<Passenger>,
)

/** Loads the three together. Only the profile is required; tiles fall back to zero. */
class GetProfileOverviewUseCase @Inject constructor(
    private val profiles: ProfileRepository,
    private val stats: ProfileStatsRepository,
    private val passengers: PassengerRepository,
) {
    suspend operator fun invoke(): Outcome<ProfileOverview, AppError> = coroutineScope {
        val profile = async { profiles.profile() }
        val tiles = async { stats.stats() }
        val saved = async { passengers.passengers() }
        when (val p = profile.await()) {
            is Outcome.Failure -> p
            is Outcome.Success -> Outcome.Success(
                ProfileOverview(
                    profile = p.value,
                    stats = (tiles.await() as? Outcome.Success)?.value ?: ProfileStats.Empty,
                    passengers = (saved.await() as? Outcome.Success)?.value.orEmpty(),
                ),
            )
        }
    }
}

/** The "Saved flights & stays" badge: every saved flight, stay and destination, live. */
class ObserveSavedCountUseCase @Inject constructor(
    private val flights: SavedFlightsRepository,
    private val hotels: SavedHotelsRepository,
    private val destinations: SavedDestinationsRepository,
) {
    operator fun invoke(): Flow<Int> = combine(
        flights.savedFlightIds(),
        hotels.savedHotelIds(),
        destinations.savedDestinationIds(),
    ) { f, h, d -> f.size + h.size + d.size }
}

enum class ProfileProblem { NameTooShort, NoHomeAirport }

class UpdateProfileUseCase @Inject constructor(private val profiles: EditProfileRepository) {
    fun validate(fullName: String, homeAirport: String): ProfileProblem? = when {
        fullName.trim().length < 2 -> ProfileProblem.NameTooShort
        !Regex("^[A-Z]{3}$").matches(homeAirport) -> ProfileProblem.NoHomeAirport
        else -> null
    }

    /** Saves a tidied name (single spaces) and the airport code. */
    suspend operator fun invoke(fullName: String, homeAirport: String): Outcome<UserProfile, AppError> {
        validate(fullName, homeAirport)?.let { return Outcome.Failure(AppError.Unknown(it.name)) }
        return profiles.update(fullName.trim().replace(Regex("\\s+"), " "), homeAirport)
    }
}

class DeletePassengerUseCase @Inject constructor(private val passengers: PassengerRepository) {
    suspend operator fun invoke(passengerId: String): Outcome<Unit, AppError> = passengers.delete(passengerId)
}

data class SavedItems(
    val flights: List<SavedFlight>,
    val hotels: List<SavedHotel>,
    val destinations: List<SavedDestination>,
) {
    val count: Int get() = flights.size + hotels.size + destinations.size
}

/** The Saved tab, loaded together; destinations are priced from the traveller's home airport. */
class GetSavedItemsUseCase @Inject constructor(
    private val saved: SavedListRepository,
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke(): Outcome<SavedItems, AppError> = coroutineScope {
        val flights = async { saved.flights() }
        val hotels = async { saved.hotels() }
        val origin = (profiles.profile() as? Outcome.Success)?.value?.homeAirport ?: DEFAULT_ORIGIN
        val destinations = saved.destinations(origin)
        val results = listOf(flights.await(), hotels.await(), destinations)
        results.firstNotNullOfOrNull { (it as? Outcome.Failure)?.error }?.let { return@coroutineScope Outcome.Failure(it) }
        Outcome.Success(
            SavedItems(
                flights = (flights.await() as Outcome.Success).value,
                hotels = (hotels.await() as Outcome.Success).value,
                destinations = (destinations as Outcome.Success).value,
            ),
        )
    }

    private companion object {
        const val DEFAULT_ORIGIN = "JFK"
    }
}

class ObserveSavedDestinationsUseCase @Inject constructor(private val saved: SavedDestinationsRepository) {
    operator fun invoke(): Flow<Set<String>> = saved.savedDestinationIds()
}

class SetDestinationSavedUseCase @Inject constructor(private val saved: SavedDestinationsRepository) {
    suspend operator fun invoke(destinationId: String, saved: Boolean): Outcome<Unit, AppError> = this.saved.setSaved(destinationId, saved)
}
