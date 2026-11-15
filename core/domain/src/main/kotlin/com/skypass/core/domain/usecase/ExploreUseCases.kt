package com.skypass.core.domain.usecase

import com.skypass.core.domain.repository.ExploreRepository
import com.skypass.core.domain.repository.ProfileRepository
import com.skypass.core.domain.repository.SearchIntentRepository
import com.skypass.core.model.Airport
import com.skypass.core.model.AppError
import com.skypass.core.model.Destination
import com.skypass.core.model.ExploreCatalog
import com.skypass.core.model.ExploreCategory
import com.skypass.core.model.Outcome
import com.skypass.core.model.SearchIntent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The airport a traveller flies from: the closest one when location is allowed, else their
 * profile's home airport, else JFK (the board's default).
 */
class ResolveOriginAirportUseCase @Inject constructor(
    private val findNearest: FindNearestAirportUseCase,
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke(airports: List<Airport>): Airport? {
        findNearest(airports)?.let { return it }
        val home = (profiles.profile() as? Outcome.Success)?.value?.homeAirport
        return airports.firstOrNull { it.code == home }
            ?: airports.firstOrNull { it.code == DEFAULT_ORIGIN }
            ?: airports.firstOrNull()
    }

    companion object {
        const val DEFAULT_ORIGIN = "JFK"
    }
}

/** Explore's grid and its deal, loaded together. The grid is required; a missing deal isn't fatal. */
class GetExploreCatalogUseCase @Inject constructor(private val explore: ExploreRepository) {
    suspend operator fun invoke(originCode: String): Outcome<ExploreCatalog, AppError> = coroutineScope {
        val destinations = async { explore.destinations(originCode) }
        val deals = async { explore.deals() }
        when (val d = destinations.await()) {
            is Outcome.Failure -> d
            is Outcome.Success -> Outcome.Success(
                ExploreCatalog(
                    originCode = originCode,
                    deal = (deals.await() as? Outcome.Success)?.value?.firstOrNull(),
                    destinations = d.value,
                ),
            )
        }
    }
}

/** Search text and category chip together. Text matches city, country or airport code. */
class FilterDestinationsUseCase @Inject constructor() {
    operator fun invoke(all: List<Destination>, category: ExploreCategory, text: String): List<Destination> {
        val q = text.trim()
        return all.filter { d ->
            category.matches(d) && (
                q.isEmpty() ||
                    d.city.contains(q, ignoreCase = true) ||
                    d.country.contains(q, ignoreCase = true) ||
                    d.airportCode.equals(q, ignoreCase = true)
                )
        }
    }
}

/** Ask Home to search for a trip (it switches to Home and fills the card). */
class RequestFlightSearchUseCase @Inject constructor(private val intents: SearchIntentRepository) {
    operator fun invoke(intent: SearchIntent) = intents.request(intent)
}

class ObserveSearchIntentUseCase @Inject constructor(private val intents: SearchIntentRepository) {
    operator fun invoke(): StateFlow<SearchIntent?> = intents.pending
}

class ConsumeSearchIntentUseCase @Inject constructor(private val intents: SearchIntentRepository) {
    operator fun invoke() = intents.consume()
}
