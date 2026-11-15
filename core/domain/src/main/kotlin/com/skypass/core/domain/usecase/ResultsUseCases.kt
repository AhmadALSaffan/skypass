package com.skypass.core.domain.usecase

import com.skypass.core.domain.repository.FlightSearchRepository
import com.skypass.core.domain.repository.SavedFlightsRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Cabin
import com.skypass.core.model.DayPrice
import com.skypass.core.model.FlightFilters
import com.skypass.core.model.FlightOffer
import com.skypass.core.model.Outcome
import com.skypass.core.model.SortOrder
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class SearchFlightsUseCase @Inject constructor(private val flights: FlightSearchRepository) {
    suspend operator fun invoke(from: String, to: String, date: LocalDate, cabin: Cabin, passengers: Int) =
        flights.search(from, to, date, cabin, passengers)
}

class GetPriceStripUseCase @Inject constructor(private val flights: FlightSearchRepository) {
    suspend operator fun invoke(from: String, to: String, center: LocalDate, cabin: Cabin): Outcome<List<DayPrice>, AppError> =
        flights.priceStrip(from, to, center, cabin)
}

// ---------------------------------------------------------------------------------------------
// Sorting: open for new orders, closed for modification. Each order is its own strategy; adding
// "Best rated" means adding a class and a map entry, not editing the existing ones.
// ---------------------------------------------------------------------------------------------

fun interface FlightSortStrategy {
    fun sort(flights: List<FlightOffer>): List<FlightOffer>
}

object CheapestFirst : FlightSortStrategy {
    override fun sort(flights: List<FlightOffer>) = flights.sortedWith(compareBy({ it.priceUsd }, { it.departure }))
}

object FastestFirst : FlightSortStrategy {
    override fun sort(flights: List<FlightOffer>) = flights.sortedWith(compareBy({ it.durationMinutes }, { it.priceUsd }))
}

object EarliestFirst : FlightSortStrategy {
    override fun sort(flights: List<FlightOffer>) = flights.sortedWith(compareBy({ it.departure }, { it.priceUsd }))
}

class SortFlightsUseCase @Inject constructor() {
    private val strategies: Map<SortOrder, FlightSortStrategy> = mapOf(
        SortOrder.Cheapest to CheapestFirst,
        SortOrder.Fastest to FastestFirst,
        SortOrder.Earliest to EarliestFirst,
    )

    operator fun invoke(flights: List<FlightOffer>, order: SortOrder): List<FlightOffer> =
        strategies.getValue(order).sort(flights)
}

class FilterFlightsUseCase @Inject constructor() {
    operator fun invoke(flights: List<FlightOffer>, filters: FlightFilters): List<FlightOffer> = flights.filter { f ->
        (!filters.nonstopOnly || f.isNonstop) &&
            (filters.airlines.isEmpty() || f.airlineCode in filters.airlines) &&
            (filters.windows.isEmpty() || filters.windows.any { it.contains(f.departure.toLocalTime()) })
    }
}

class ObserveSavedFlightsUseCase @Inject constructor(private val saved: SavedFlightsRepository) {
    operator fun invoke(): Flow<Set<String>> = saved.savedFlightIds()
}

class ToggleSavedFlightUseCase @Inject constructor(private val saved: SavedFlightsRepository) {
    suspend operator fun invoke(instanceId: String, saved: Boolean): Outcome<Unit, AppError> =
        this.saved.setSaved(instanceId, saved)
}
