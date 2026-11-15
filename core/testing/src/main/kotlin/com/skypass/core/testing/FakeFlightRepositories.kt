package com.skypass.core.testing

import com.skypass.core.domain.repository.FlightSearchRepository
import com.skypass.core.domain.repository.SavedFlightsRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Cabin
import com.skypass.core.model.DayPrice
import com.skypass.core.model.FlightOffer
import com.skypass.core.model.FlightStatus
import com.skypass.core.model.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The board's five JFK → LAX results on Fri 20 Nov 2026. */
object TestFlights {
    private fun offer(
        id: String, code: String, name: String, number: String, dep: String, arr: String,
        minutes: Int, stops: Int, stop: String?, price: Int, best: Boolean = false,
    ) = FlightOffer(
        instanceId = id, flightId = "f-$id", airlineCode = code, airlineName = name, airlineColor = 0xFF000000,
        flightNumber = number, aircraft = "A321", originCode = "JFK", originCity = "New York",
        destinationCode = "LAX", destinationCity = "Los Angeles",
        departure = LocalDateTime.parse("2026-11-20T$dep"), arrival = LocalDateTime.parse("2026-11-20T$arr"),
        durationMinutes = minutes, stops = stops, stopAirport = stop, gate = "B22",
        boardingTime = LocalTime.parse(dep).minusMinutes(40), status = FlightStatus.OnTime, rating = 4.5,
        perks = listOf("Carry-on", "Wi-Fi"), priceUsd = price, totalUsd = price, isBestValue = best,
    )

    val NZ5 = offer("nz5", "NZ", "Air New Zealand", "NZ 5", "07:15", "10:40", 385, 0, null, 209, best = true)
    val UA2270 = offer("ua", "UA", "United Airlines", "UA 2270", "06:00", "11:35", 515, 1, "DEN", 214)
    val AS1235 = offer("as", "AS", "Alaska Airlines", "AS 1235", "15:45", "21:05", 500, 1, "SEA", 236)
    val B623 = offer("b6", "B6", "JetBlue", "B6 23", "09:00", "12:12", 372, 0, null, 248)
    val AA1 = offer("aa", "AA", "American Airlines", "AA 1", "12:30", "15:52", 382, 0, null, 312)
    val all = listOf(NZ5, UA2270, AS1235, B623, AA1)
}

class FakeFlightSearchRepository(
    var flightsByDate: Map<LocalDate, List<FlightOffer>> = mapOf(LocalDate.of(2026, 11, 20) to TestFlights.all),
    var failure: AppError? = null,
) : FlightSearchRepository {
    val searches = mutableListOf<LocalDate>()

    override suspend fun search(from: String, to: String, date: LocalDate, cabin: Cabin, passengers: Int): Outcome<List<FlightOffer>, AppError> {
        searches += date
        failure?.let { return Outcome.Failure(it) }
        return Outcome.Success(flightsByDate[date].orEmpty())
    }

    override suspend fun priceStrip(from: String, to: String, center: LocalDate, cabin: Cabin): Outcome<List<DayPrice>, AppError> =
        Outcome.Success((-2..2).map { DayPrice(center.plusDays(it.toLong()), 200 + it) })
}

class FakeSavedFlightsRepository(var fail: Boolean = false) : SavedFlightsRepository {
    private val ids = MutableStateFlow<Set<String>>(emptySet())
    override fun savedFlightIds(): Flow<Set<String>> = ids
    override suspend fun setSaved(instanceId: String, saved: Boolean): Outcome<Unit, AppError> {
        if (fail) return Outcome.Failure(AppError.Network)
        ids.value = if (saved) ids.value + instanceId else ids.value - instanceId
        return Outcome.Success(Unit)
    }
}
