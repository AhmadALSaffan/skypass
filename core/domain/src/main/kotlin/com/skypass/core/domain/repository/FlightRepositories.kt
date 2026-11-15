package com.skypass.core.domain.repository

import com.skypass.core.model.AppError
import com.skypass.core.model.Cabin
import com.skypass.core.model.DayPrice
import com.skypass.core.model.FlightOffer
import com.skypass.core.model.Outcome
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface FlightSearchRepository {
    /** Departures on [date], priced server-side for [cabin] and [passengers]. */
    suspend fun search(
        from: String,
        to: String,
        date: LocalDate,
        cabin: Cabin,
        passengers: Int,
    ): Outcome<List<FlightOffer>, AppError>

    /** Cheapest fare two days either side of [center]. */
    suspend fun priceStrip(from: String, to: String, center: LocalDate, cabin: Cabin): Outcome<List<DayPrice>, AppError>
}

/** Bookmarked departures (results bookmark, Saved tab). */
interface SavedFlightsRepository {
    /** Instance ids the user has saved; updates as they toggle. */
    fun savedFlightIds(): Flow<Set<String>>

    suspend fun setSaved(instanceId: String, saved: Boolean): Outcome<Unit, AppError>
}
